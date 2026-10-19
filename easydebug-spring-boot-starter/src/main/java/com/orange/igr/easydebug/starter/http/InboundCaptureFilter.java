package com.orange.igr.easydebug.starter.http;

import com.orange.igr.easydebug.CapturePolicy;
import com.orange.igr.easydebug.Defaults;
import com.orange.igr.easydebug.Payload;
import com.orange.igr.easydebug.Spool;
import com.orange.igr.easydebug.starter.EasyDebugRecorder;
import com.orange.igr.easydebug.starter.ExchangeAssembler;
import com.orange.igr.easydebug.starter.MdcSnapshot;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records every servlet request, including failures and multipart uploads. */
public final class InboundCaptureFilter extends OncePerRequestFilter {

    private final CapturePolicy policy;
    private final ExchangeAssembler assembler;
    private final EasyDebugRecorder recorder;

    public InboundCaptureFilter(CapturePolicy policy, ExchangeAssembler assembler, EasyDebugRecorder recorder) {
        this.policy = policy;
        this.assembler = assembler;
        this.recorder = recorder;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        // The wrapper stays on the async context. Recording happens from the async listener.
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (Boolean.TRUE.equals(request.getAttribute(getClass().getName()))) {
            chain.doFilter(request, response);
            return;
        }
        String url = url(request);
        if (policy.excluded(url, request.getRequestURI(), request.getServerName())) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(getClass().getName(), Boolean.TRUE);
        Instant started = Instant.now();
        long nanos = System.nanoTime();
        Map<String, String> mdc = MdcSnapshot.copy();
        boolean multipart = isMultipart(request);
        Spool requestSpool = null;
        MultipartCaptureRequest parsedMultipart = null;
        String captureError = null;
        HttpServletRequest inbound = request;
        if (multipart) {
            parsedMultipart = new MultipartCaptureRequest(request);
            inbound = parsedMultipart;
        } else {
            try {
                requestSpool = Spool.capture(request.getInputStream(), policy.maxBodyBytes(), policy.spoolMemoryBytes());
                inbound = new ReplayRequest(request, requestSpool);
            } catch (Exception e) {
                captureError = e.toString();
            }
        }
        final MultipartCaptureRequest multipartRequest = parsedMultipart;
        CapturingResponse outbound = new CapturingResponse(response, policy);
        AtomicBoolean recorded = new AtomicBoolean();
        Throwable error = null;
        try {
            chain.doFilter(inbound, outbound);
        } catch (Throwable thrown) {
            error = thrown;
            if (thrown instanceof ServletException servletException) {
                throw servletException;
            }
            if (thrown instanceof IOException ioException) {
                throw ioException;
            }
            if (thrown instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (thrown instanceof Error serious) {
                throw serious;
            }
            throw new ServletException(thrown);
        } finally {
            if (request.isAsyncStarted()) {
                Throwable asyncError = error;
                String bodyError = captureError;
                Spool spool = requestSpool;
                request.getAsyncContext().addListener(new AsyncListener() {
                    @Override
                    public void onComplete(AsyncEvent event) {
                        finish(recorded, request, outbound, spool, multipartRequest, url, started, nanos, mdc, asyncError, bodyError);
                    }

                    @Override
                    public void onTimeout(AsyncEvent event) {
                        finish(recorded, request, outbound, spool, multipartRequest, url, started, nanos, mdc,
                                new IllegalStateException("async timeout"), bodyError);
                    }

                    @Override
                    public void onError(AsyncEvent event) {
                        finish(recorded, request, outbound, spool, multipartRequest, url, started, nanos, mdc,
                                event.getThrowable() == null ? asyncError : event.getThrowable(), bodyError);
                    }

                    @Override
                    public void onStartAsync(AsyncEvent event) {
                    }
                });
            } else {
                finish(recorded, request, outbound, requestSpool, multipartRequest, url, started, nanos, mdc, error, captureError);
            }
        }
    }

    private void finish(AtomicBoolean recorded, HttpServletRequest request, CapturingResponse response, Spool spool,
                        MultipartCaptureRequest multipart, String url, Instant started, long nanos,
                        Map<String, String> mdc, Throwable error, String captureError) {
        if (!recorded.compareAndSet(false, true)) {
            return;
        }
        try {
            Payload requestPayload;
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("remoteAddr", request.getRemoteAddr());
            if (captureError != null) {
                attributes.put("requestCaptureError", captureError);
            }
            if (multipart != null) {
                requestPayload = multipart.payload(policy, request, attributes);
            } else if (spool != null) {
                requestPayload = spool.toPayload(request.getContentType(), request.getHeader("Content-Disposition"));
            } else {
                requestPayload = Payload.empty();
            }
            Payload responsePayload = response.payload();
            recorder.record(assembler.http(
                    Defaults.DIRECTION_IN,
                    "servlet",
                    started,
                    nanos,
                    request.getMethod(),
                    url,
                    status(response, error),
                    error,
                    headers(request),
                    requestPayload,
                    headers(response),
                    responsePayload,
                    mdc,
                    attributes));
        } catch (Exception ignored) {
            // a capture failure must not replace the application response
        } finally {
            if (spool != null) {
                spool.close();
            }
            response.closeSpool();
        }
    }

    /**
     * An exception that leaves the filter becomes HTTP 500 when the application has not already
     * set an error status. The container applies that status after this filter returns.
     */
    private static int status(CapturingResponse response, Throwable error) {
        int status = response.getStatus();
        if (error != null && !response.isCommitted() && status < 400) {
            return 500;
        }
        return status;
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    static String url(HttpServletRequest request) {
        StringBuffer buffer = request.getRequestURL();
        if (request.getQueryString() != null) {
            buffer.append('?').append(request.getQueryString());
        }
        return buffer.toString();
    }

    static Map<String, String> headers(HttpServletRequest request) {
        Map<String, List<String>> acc = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            Enumeration<String> values = request.getHeaders(name);
            List<String> list = new ArrayList<>();
            while (values != null && values.hasMoreElements()) {
                list.add(values.nextElement());
            }
            acc.put(name, list);
        }
        return join(acc);
    }

    static Map<String, String> headers(HttpServletResponse response) {
        Map<String, List<String>> acc = new LinkedHashMap<>();
        Collection<String> names = response.getHeaderNames();
        if (names != null) {
            for (String name : names) {
                Collection<String> values = response.getHeaders(name);
                acc.put(name, values == null ? List.of() : List.copyOf(values));
            }
        }
        return join(acc);
    }

    private static Map<String, String> join(Map<String, List<String>> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((key, values) -> out.put(key, String.join(", ", values)));
        return out;
    }
}
