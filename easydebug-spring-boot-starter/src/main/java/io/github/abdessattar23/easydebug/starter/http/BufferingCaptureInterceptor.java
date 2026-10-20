package io.github.abdessattar23.easydebug.starter.http;

import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.Defaults;
import io.github.abdessattar23.easydebug.Payload;
import io.github.abdessattar23.easydebug.Spool;
import io.github.abdessattar23.easydebug.starter.EasyDebugRecorder;
import io.github.abdessattar23.easydebug.starter.ExchangeAssembler;
import io.github.abdessattar23.easydebug.starter.MdcSnapshot;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared by RestClient, RestTemplate, and their Boot builders. */
public final class BufferingCaptureInterceptor implements ClientHttpRequestInterceptor {

    private final CapturePolicy policy;
    private final ExchangeAssembler assembler;
    private final EasyDebugRecorder recorder;
    private final String hook;

    public BufferingCaptureInterceptor(CapturePolicy policy, ExchangeAssembler assembler,
                                       EasyDebugRecorder recorder, String hook) {
        this.policy = policy;
        this.assembler = assembler;
        this.recorder = recorder;
        this.hook = hook;
    }

    public String hook() {
        return hook;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String url = request.getURI().toString();
        if (policy.excluded(url, request.getURI().getPath(), request.getURI().getHost())) {
            return execution.execute(request, body);
        }
        Instant started = Instant.now();
        long nanos = System.nanoTime();
        Map<String, String> mdc = MdcSnapshot.copy();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            Spool responseSpool = Spool.capture(
                    response.getBody() == null ? InputStream.nullInputStream() : response.getBody(),
                    policy.maxBodyBytes(), policy.spoolMemoryBytes());
            record(request, body, response, responseSpool.toPayload(
                    header(response.getHeaders(), "Content-Type"),
                    header(response.getHeaders(), "Content-Disposition")), url, started, nanos, mdc, null);
            return new ReplayResponse(response, responseSpool);
        } catch (Exception e) {
            record(request, body, null, null, url, started, nanos, mdc, e);
            if (e instanceof IOException io) {
                throw io;
            }
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IOException(e);
        }
    }

    private void record(HttpRequest request, byte[] body, ClientHttpResponse response, Payload responsePayload,
                        String url, Instant started, long nanos, Map<String, String> mdc, Throwable error) {
        try {
            Payload requestPayload = io.github.abdessattar23.easydebug.BodyCapture.capture(
                    body,
                    header(request.getHeaders(), "Content-Type"),
                    header(request.getHeaders(), "Content-Disposition"),
                    policy.maxBodyBytes());
            Integer status = null;
            Map<String, String> responseHeaders = Map.of();
            if (response != null) {
                status = response.getStatusCode().value();
                responseHeaders = join(response.getHeaders());
            }
            recorder.record(assembler.http(
                    Defaults.DIRECTION_OUT,
                    hook,
                    started,
                    nanos,
                    request.getMethod().name(),
                    url,
                    status,
                    error,
                    join(request.getHeaders()),
                    requestPayload,
                    responseHeaders,
                    responsePayload,
                    mdc,
                    Map.of()));
        } catch (Exception ignored) {
            // keep the business call
        }
    }

    private static Map<String, String> join(HttpHeaders headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((key, values) -> out.put(key, String.join(", ", values)));
        return out;
    }

    private static String header(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static final class ReplayResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final Spool spool;

        private ReplayResponse(ClientHttpResponse delegate, Spool spool) {
            this.delegate = delegate;
            this.spool = spool;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            spool.close();
            delegate.close();
        }

        @Override
        public InputStream getBody() throws IOException {
            return spool.open();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
