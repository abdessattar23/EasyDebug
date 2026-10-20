package io.github.abdessattar23.easydebug.starter.http;

import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.Defaults;
import io.github.abdessattar23.easydebug.Payload;
import io.github.abdessattar23.easydebug.Spool;
import io.github.abdessattar23.easydebug.starter.EasyDebugRecorder;
import io.github.abdessattar23.easydebug.starter.ExchangeAssembler;
import io.github.abdessattar23.easydebug.starter.MdcSnapshot;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Mono;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records calls made with a {@code WebClient} built from Spring's {@code WebClient.Builder}. */
public final class WebClientCaptureFilter implements ExchangeFilterFunction {

    private static final DataBufferFactory BUFFERS = new DefaultDataBufferFactory();

    private final CapturePolicy policy;
    private final ExchangeAssembler assembler;
    private final EasyDebugRecorder recorder;

    public WebClientCaptureFilter(CapturePolicy policy, ExchangeAssembler assembler, EasyDebugRecorder recorder) {
        this.policy = policy;
        this.assembler = assembler;
        this.recorder = recorder;
    }

    @Override
    public reactor.core.publisher.Mono<ClientResponse> filter(ClientRequest request, org.springframework.web.reactive.function.client.ExchangeFunction next) {
        String url = request.url().toString();
        if (policy.excluded(url, request.url().getPath(), request.url().getHost())) {
            return next.exchange(request);
        }
        Spool requestSpool = new Spool(policy.maxBodyBytes(), policy.spoolMemoryBytes());
        ClientRequest captured = ClientRequest.from(request)
                .body((outputMessage, context) -> request.body().insert(new TeeRequest(outputMessage, requestSpool), context))
                .build();
        Instant started = Instant.now();
        long nanos = System.nanoTime();
        Map<String, String> mdc = MdcSnapshot.copy();
        AtomicBoolean recorded = new AtomicBoolean();
        return next.exchange(captured).flatMap(response ->
                readResponse(response).map(responseSpool -> {
                    record(recorded, request, requestSpool, response, responseSpool, url, started, nanos, mdc, null);
                    return ClientResponse.from(response).body(replay(responseSpool)).build();
                })
        ).doOnError(error -> record(recorded, request, requestSpool, null, null, url, started, nanos, mdc, error));
    }

    private void record(AtomicBoolean recorded, ClientRequest request, Spool requestSpool, ClientResponse response,
                        Spool responseSpool, String url, Instant started, long nanos, Map<String, String> mdc,
                        Throwable error) {
        if (!recorded.compareAndSet(false, true)) {
            if (responseSpool != null) {
                responseSpool.close();
            }
            return;
        }
        try {
            Payload requestPayload = requestSpool.toPayload(header(request.headers(), "Content-Type"),
                    header(request.headers(), "Content-Disposition"));
            Payload responsePayload = responseSpool == null ? null : responseSpool.toPayload(
                    response == null ? null : header(response.headers().asHttpHeaders(), "Content-Type"),
                    response == null ? null : header(response.headers().asHttpHeaders(), "Content-Disposition"));
            recorder.record(assembler.http(
                    Defaults.DIRECTION_OUT,
                    "webclient",
                    started,
                    nanos,
                    request.method().name(),
                    url,
                    response == null ? null : response.statusCode().value(),
                    error,
                    join(request.headers()),
                    requestPayload,
                    response == null ? Map.of() : join(response.headers().asHttpHeaders()),
                    responsePayload,
                    mdc,
                    Map.of()));
        } catch (Exception ignored) {
            // keep the business call
        } finally {
            requestSpool.close();
        }
    }

    private Mono<Spool> readResponse(ClientResponse response) {
        Spool spool = new Spool(policy.maxBodyBytes(), policy.spoolMemoryBytes());
        return response.bodyToFlux(DataBuffer.class)
                .concatMap(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        spool.write(bytes);
                        return Mono.<DataBuffer>empty();
                    } catch (IOException e) {
                        spool.close();
                        return Mono.error(e);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .then(Mono.just(spool));
    }

    private static Flux<DataBuffer> replay(Spool spool) {
        return Flux.generate(
                spool::open,
                (InputStream input, reactor.core.publisher.SynchronousSink<DataBuffer> sink) -> {
                    byte[] buffer = new byte[8192];
                    try {
                        int read = input.read(buffer);
                        if (read < 0) {
                            sink.complete();
                        } else {
                            byte[] copy = new byte[read];
                            System.arraycopy(buffer, 0, copy, 0, read);
                            sink.next(BUFFERS.wrap(copy));
                        }
                    } catch (IOException e) {
                        sink.error(e);
                    }
                    return input;
                },
                input -> {
                    try {
                        input.close();
                    } catch (IOException ignored) {
                        // closed after replay
                    }
                    spool.close();
                });
    }

    private static Map<String, String> join(org.springframework.http.HttpHeaders headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((key, values) -> out.put(key, String.join(", ", values)));
        return out;
    }

    private static String header(org.springframework.http.HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static final class TeeRequest extends ClientHttpRequestDecorator {
        private final Spool spool;

        private TeeRequest(ClientHttpRequest delegate, Spool spool) {
            super(delegate);
            this.spool = spool;
        }

        @Override
        public reactor.core.publisher.Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
            return super.writeWith(Flux.from(body).map(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);
                try {
                    spool.write(bytes);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                return bufferFactory().wrap(bytes);
            }));
        }

        @Override
        public reactor.core.publisher.Mono<Void> writeAndFlushWith(
                org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
            return writeWith(Flux.from(body).concatMap(Flux::from));
        }
    }
}
