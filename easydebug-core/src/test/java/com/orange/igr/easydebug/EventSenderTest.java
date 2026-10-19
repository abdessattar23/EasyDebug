package com.orange.igr.easydebug;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EventSenderTest {

    private final CapturePolicy policy = new CapturePolicy(
            "demo", "http://127.0.0.1:8090", 100, 1, Duration.ofSeconds(1), null,
            null, null, "", "", "");

    @Test
    void retriesATransportFailureThenDeliversTheSameEvents() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<String> bodies = java.util.Collections.synchronizedList(new ArrayList<>());
        EventPoster poster = json -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IOException("down");
            }
            bodies.add(json);
            return 202;
        };
        EventSender sender = new EventSender(policy, poster);
        try {
            sender.submit(event("one"));
            awaitBodies(bodies, 1);
        } finally {
            sender.close();
        }

        assertThat(attempts.get()).isGreaterThanOrEqualTo(2);
        assertThat(bodies).hasSize(1);
        assertThat(bodies.getFirst()).contains("\"id\":\"one\"");
    }

    @Test
    void fullQueueBecomesAGapOnTheNextSuccessfulSend() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> bodies = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger calls = new AtomicInteger();
        EventPoster poster = json -> {
            bodies.add(json);
            if (calls.getAndIncrement() == 0) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("release timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
            }
            return 202;
        };
        EventSender sender = new EventSender(policy, poster);
        try {
            sender.submit(event("first"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            sender.submit(event("queued"));
            sender.submit(event("dropped"));
            release.countDown();
            awaitGap(bodies);
        } finally {
            sender.close();
        }

        String all = String.join("\n", bodies);
        assertThat(all).contains("\"id\":\"first\"");
        assertThat(all).contains("\"id\":\"queued\"");
        assertThat(all).doesNotContain("\"id\":\"dropped\"");
        JsonNode gap = findGap(bodies);
        assertThat(gap.path("attributes").path("missedCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(gap.path("kind").asText()).isEqualTo("capture.gap");
    }

    private static CaptureEvent event(String id) {
        return new CaptureEvent(
                id, Defaults.KIND_HTTP, Defaults.DIRECTION_OUT, "demo", "test", null,
                1L, "GET", "https://example.test/x", "example.test", 200, null, null,
                java.util.Map.of(), Payload.empty(), Payload.empty(), java.util.Map.of());
    }

    private static void awaitBodies(List<String> bodies, int count) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (bodies.size() >= count) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("bodies=" + bodies);
    }

    private static void awaitGap(List<String> bodies) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (findGap(bodies) != null) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("bodies=" + bodies);
    }

    private static JsonNode findGap(List<String> bodies) throws Exception {
        var mapper = JsonMappers.mapper();
        List<String> snapshot;
        synchronized (bodies) {
            snapshot = List.copyOf(bodies);
        }
        for (String body : snapshot) {
            for (JsonNode node : mapper.readTree(body)) {
                if ("capture.gap".equals(node.path("kind").asText())) {
                    return node;
                }
            }
        }
        return null;
    }
}
