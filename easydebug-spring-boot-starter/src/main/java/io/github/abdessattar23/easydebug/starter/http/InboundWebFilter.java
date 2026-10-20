package io.github.abdessattar23.easydebug.starter.http;

import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.Defaults;
import io.github.abdessattar23.easydebug.Payload;
import io.github.abdessattar23.easydebug.Spool;
import io.github.abdessattar23.easydebug.starter.EasyDebugRecorder;
import io.github.abdessattar23.easydebug.starter.ExchangeAssembler;
import io.github.abdessattar23.easydebug.starter.MdcSnapshot;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Records every request when the application type is reactive. */
public final class InboundWebFilter implements WebFilter, Ordered {

    private final CapturePolicy policy;
    private final ExchangeAssembler assembler;
    private final EasyDebugRecorder recorder;

    public InboundWebFilter(CapturePolicy policy, ExchangeAssembler assembler, EasyDebugRecorder recorder) {
        this.policy = policy;
        this.assembler = assembler;
        this.recorder = recorder;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String url = url(request);
        URI uri = request.getURI();
        if (policy.excluded(url, uri.getPath(), host(request))) {
            return chain.filter(exchange);
        }
        Instant started = Instant.now();
        long nanos = System.nanoTime();
        Map<String, String> mdc = MdcSnapshot.copy();
        Spool requestSpool = new Spool(policy.maxBodyBytes(), policy.spoolMemoryBytes());
        Spool responseSpool = new Spool(policy.maxBodyBytes(), policy.spoolMemoryBytes());
        AtomicBoolean recorded = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean requestBodySeen = new AtomicBoolean();
        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.defer(() -> {
                    boolean record = requestBodySeen.compareAndSet(false, true);
                    Flux<DataBuffer> body = super.getBody();
                    if (!record) {
                        return body;
                    }
                    return body.doOnNext(buffer -> {
                        try {
                            requestSpool.write(copy(buffer));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                });
            }
        };
        ServerHttpResponse decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return super.writeWith(Flux.from(body).doOnNext(buffer -> {
                    try {
                        responseSpool.write(copy(buffer));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                    org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).concatMap(Flux::from));
            }
        };
        ServerWebExchange decorated = exchange.mutate().request(decoratedRequest).response(decoratedResponse).build();
        return chain.filter(decorated)
                .doOnError(failure::set)
                .doFinally(signal -> finish(recorded, request, decoratedResponse, requestSpool, responseSpool,
                        url, started, nanos, mdc, error(signal, failure.get())));
    }

    private void finish(AtomicBoolean recorded, ServerHttpRequest request, ServerHttpResponse response,
                        Spool requestSpool, Spool responseSpool, String url, Instant started, long nanos,
                        Map<String, String> mdc, Throwable error) {
        if (!recorded.compareAndSet(false, true)) {
            return;
        }
        try {
            Map<String, Object> attributes = new LinkedHashMap<>();
            InetSocketAddress remote = request.getRemoteAddress();
            if (remote != null) {
                attributes.put("remoteAddr", remote.toString());
            }
            Payload requestPayload = requestSpool.toPayload(
                    request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                    request.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
            Payload responsePayload = responseSpool.toPayload(
                    response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                    response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
            Integer status = response.getStatusCode() == null ? null : response.getStatusCode().value();
            recorder.record(assembler.http(
                    Defaults.DIRECTION_IN,
                    "webflux",
                    started,
                    nanos,
                    request.getMethod() == null ? null : request.getMethod().name(),
                    url,
                    status,
                    error,
                    join(request.getHeaders()),
                    requestPayload,
                    join(response.getHeaders()),
                    responsePayload,
                    mdc,
                    attributes));
        } catch (Exception ignored) {
            // a capture failure must not replace the application response
        } finally {
            requestSpool.close();
            responseSpool.close();
        }
    }

    private static Throwable error(SignalType signal, Throwable failure) {
        if (failure != null) {
            return failure;
        }
        if (signal == SignalType.CANCEL) {
            return new CancellationException("cancelled");
        }
        return null;
    }

    private static byte[] copy(DataBuffer buffer) {
        int count = buffer.readableByteCount();
        byte[] bytes = new byte[count];
        if (count == 0) {
            return bytes;
        }
        int position = buffer.readPosition();
        buffer.read(bytes);
        buffer.readPosition(position);
        return bytes;
    }

    private static String url(ServerHttpRequest request) {
        URI uri = request.getURI();
        String text = uri.toString();
        if (!text.startsWith("/")) {
            return text;
        }
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String host = request.getHeaders().getFirst(HttpHeaders.HOST);
        return scheme + "://" + (host == null || host.isBlank() ? "localhost" : host) + text;
    }

    private static String host(ServerHttpRequest request) {
        URI uri = request.getURI();
        if (uri.getHost() != null && !uri.getHost().isBlank()) {
            return uri.getHost();
        }
        String header = request.getHeaders().getFirst(HttpHeaders.HOST);
        if (header == null || header.isBlank()) {
            return null;
        }
        int colon = header.lastIndexOf(':');
        return colon > 0 ? header.substring(0, colon) : header;
    }

    private static Map<String, String> join(HttpHeaders headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((key, values) -> out.put(key, String.join(", ", values)));
        return out;
    }
}
