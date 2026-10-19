package com.orange.igr.easydebug.starter.feign;

import com.orange.igr.easydebug.BodyCapture;
import com.orange.igr.easydebug.CapturePolicy;
import com.orange.igr.easydebug.Defaults;
import com.orange.igr.easydebug.Payload;
import com.orange.igr.easydebug.starter.EasyDebugRecorder;
import com.orange.igr.easydebug.starter.ExchangeAssembler;
import com.orange.igr.easydebug.starter.MdcSnapshot;
import feign.Client;
import feign.Request;
import feign.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wraps a {@code feign.Client} bean. A chain of client beans shares the same {@link Request}
 * instance, so the outer wrapper records the call and the inner ones forward it.
 */
public final class FeignCaptureClient implements Client {

    private static final ThreadLocal<Set<Request>> ACTIVE =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private final Client delegate;
    private final CapturePolicy policy;
    private final ExchangeAssembler assembler;
    private final EasyDebugRecorder recorder;

    public FeignCaptureClient(Client delegate, CapturePolicy policy, ExchangeAssembler assembler,
                              EasyDebugRecorder recorder) {
        this.delegate = delegate;
        this.policy = policy;
        this.assembler = assembler;
        this.recorder = recorder;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        Set<Request> active = ACTIVE.get();
        boolean outer = active.add(request);
        try {
            if (!outer || excluded(request)) {
                return delegate.execute(request, options);
            }
            Instant started = Instant.now();
            long nanos = System.nanoTime();
            Map<String, String> mdc = MdcSnapshot.copy();
            try {
                Response response = delegate.execute(request, options);
                byte[] responseBody = read(response);
                record(request, response, responseBody, started, nanos, mdc, null);
                return response.toBuilder().body(responseBody).build();
            } catch (Exception e) {
                record(request, null, null, started, nanos, mdc, e);
                if (e instanceof IOException io) {
                    throw io;
                }
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IOException(e);
            }
        } finally {
            if (outer) {
                active.remove(request);
                if (active.isEmpty()) {
                    ACTIVE.remove();
                }
            }
        }
    }

    private boolean excluded(Request request) {
        URI uri = uri(request.url());
        String path = uri == null ? null : uri.getPath();
        String host = uri == null ? null : uri.getHost();
        return policy.excluded(request.url(), path, host);
    }

    private void record(Request request, Response response, byte[] responseBody, Instant started, long nanos,
                        Map<String, String> mdc, Throwable error) {
        try {
            Map<String, String> requestHeaders = join(request.headers());
            Payload requestPayload = BodyCapture.capture(
                    request.body(),
                    header(requestHeaders, "Content-Type"),
                    header(requestHeaders, "Content-Disposition"),
                    policy.maxBodyBytes());
            Integer status = response == null ? null : response.status();
            Map<String, String> responseHeaders = response == null ? Map.of() : join(response.headers());
            Payload responsePayload = response == null ? null : BodyCapture.capture(
                    responseBody,
                    header(responseHeaders, "Content-Type"),
                    header(responseHeaders, "Content-Disposition"),
                    policy.maxBodyBytes());
            recorder.record(assembler.http(
                    Defaults.DIRECTION_OUT,
                    "feign",
                    started,
                    nanos,
                    request.httpMethod().name(),
                    request.url(),
                    status,
                    error,
                    requestHeaders,
                    requestPayload,
                    responseHeaders,
                    responsePayload,
                    mdc,
                    Map.of()));
        } catch (Exception ignored) {
            // keep the business call
        }
    }

    private static byte[] read(Response response) throws IOException {
        if (response.body() == null) {
            return new byte[0];
        }
        try (InputStream in = response.body().asInputStream()) {
            return in.readAllBytes();
        }
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Map<String, String> join(Map<String, Collection<String>> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        if (headers == null) {
            return out;
        }
        headers.forEach((key, values) -> {
            if (key != null) {
                out.put(key, values == null ? "" : String.join(", ", values));
            }
        });
        return out;
    }

    private static URI uri(String url) {
        try {
            return url == null ? null : URI.create(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
