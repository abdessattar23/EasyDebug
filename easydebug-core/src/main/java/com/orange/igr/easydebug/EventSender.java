package com.orange.igr.easydebug;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Async delivery. {@link #submit} never blocks and never throws.
 * A full queue or a rejected batch is counted and reported as {@code capture.gap}
 * on the next successful send.
 */
public final class EventSender implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EventSender.class);
    private static final int BATCH = 100;
    private static final long MAX_BACKOFF_MS = 30_000L;

    private final CapturePolicy policy;
    private final EventPoster poster;
    private final ObjectMapper mapper;
    private final BlockingQueue<CaptureEvent> queue;
    private final AtomicInteger missed = new AtomicInteger();
    private final AtomicReference<Instant> gapFrom = new AtomicReference<>();
    private final AtomicReference<Instant> gapUntil = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<Instant> lastWarn = new AtomicReference<>();
    private final Thread thread;

    public EventSender(CapturePolicy policy, EventPoster poster) {
        this(policy, poster, JsonMappers.mapper());
    }

    public EventSender(CapturePolicy policy, EventPoster poster, ObjectMapper mapper) {
        this.policy = policy;
        this.poster = poster;
        this.mapper = mapper;
        this.queue = new LinkedBlockingQueue<>(policy.queueCapacity());
        this.thread = new Thread(this::loop, "easydebug-sender");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public void submit(CaptureEvent event) {
        if (event == null || !running.get()) {
            return;
        }
        try {
            if (!queue.offer(event)) {
                noteMiss(1, event.occurredAt(), event.occurredAt());
            }
        } catch (RuntimeException e) {
            log.debug("EasyDebug submit failed: {}", e.toString());
        }
    }

    @Override
    public void close() {
        running.set(false);
        thread.interrupt();
        try {
            thread.join(1_500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            poster.close();
        } catch (Exception e) {
            log.debug("EasyDebug poster close failed: {}", e.toString());
        }
    }

    private void loop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                CaptureEvent first = queue.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                List<CaptureEvent> batch = new ArrayList<>(BATCH);
                batch.add(first);
                queue.drainTo(batch, BATCH - 1);
                prependGap(batch);
                sendWithRetry(batch);
            } catch (InterruptedException e) {
                if (!running.get()) {
                    flushOnce();
                    return;
                }
            } catch (Exception e) {
                log.error("EasyDebug sender loop error: {}", e.toString());
            }
        }
    }

    private void flushOnce() {
        List<CaptureEvent> batch = new ArrayList<>();
        queue.drainTo(batch);
        if (batch.isEmpty() && missed.get() == 0) {
            return;
        }
        prependGap(batch);
        if (batch.isEmpty()) {
            return;
        }
        try {
            int status = poster.post(json(batch));
            if (status >= 200 && status < 300) {
                return;
            }
            log.error("EasyDebug shutdown left {} events undelivered (HTTP {})", count(batch), status);
        } catch (Exception e) {
            log.error("EasyDebug shutdown left {} events undelivered: {}", count(batch), e.toString());
        }
    }

    private void sendWithRetry(List<CaptureEvent> batch) {
        long delay = 200L;
        while (true) {
            try {
                int status = poster.post(json(batch));
                if (status >= 200 && status < 300) {
                    return;
                }
                if (status >= 400 && status < 500) {
                    warn("EasyDebug ingest rejected HTTP " + status + " for " + count(batch) + " events");
                    noteMiss(count(batch), started(batch), ended(batch));
                    return;
                }
                warn("EasyDebug ingest returned HTTP " + status);
            } catch (Exception e) {
                if (!running.get()) {
                    log.error("EasyDebug shutdown left {} events undelivered: {}", count(batch), e.toString());
                    return;
                }
                warn("EasyDebug ingest failed: " + e);
            }
            if (!running.get()) {
                log.error("EasyDebug shutdown left {} events undelivered", count(batch));
                return;
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                if (!running.get()) {
                    log.error("EasyDebug shutdown left {} events undelivered", count(batch));
                    return;
                }
            }
            delay = Math.min(delay * 2, MAX_BACKOFF_MS);
        }
    }

    private void prependGap(List<CaptureEvent> batch) {
        int count = missed.getAndSet(0);
        Instant from = gapFrom.getAndSet(null);
        Instant until = gapUntil.getAndSet(null);
        if (count > 0) {
            batch.add(0, CaptureEvent.gap(policy.service(), count, from, until));
        }
    }

    private void noteMiss(int count, Instant from, Instant until) {
        if (count <= 0) {
            return;
        }
        if (from != null) {
            gapFrom.compareAndSet(null, from);
        }
        if (until != null) {
            gapUntil.set(until);
        }
        missed.addAndGet(count);
    }

    private String json(List<CaptureEvent> batch) throws JsonProcessingException {
        return mapper.writeValueAsString(batch);
    }

    private static int count(List<CaptureEvent> batch) {
        int count = 0;
        for (CaptureEvent event : batch) {
            if (Defaults.KIND_GAP.equals(event.kind())) {
                Object missedCount = event.attributes().get("missedCount");
                count += missedCount instanceof Number number ? number.intValue() : 1;
            } else {
                count++;
            }
        }
        return count;
    }

    private static Instant started(List<CaptureEvent> batch) {
        return batch.isEmpty() ? null : batch.get(0).occurredAt();
    }

    private static Instant ended(List<CaptureEvent> batch) {
        return batch.isEmpty() ? null : batch.get(batch.size() - 1).occurredAt();
    }

    private void warn(String message) {
        Instant now = Instant.now();
        Instant previous = lastWarn.get();
        if (previous == null || now.toEpochMilli() - previous.toEpochMilli() >= 30_000L) {
            if (lastWarn.compareAndSet(previous, now)) {
                log.warn(message);
                return;
            }
        }
        log.debug(message);
    }
}
