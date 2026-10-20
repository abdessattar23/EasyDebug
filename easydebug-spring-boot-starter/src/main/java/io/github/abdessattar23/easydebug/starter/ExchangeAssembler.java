package io.github.abdessattar23.easydebug.starter;

import io.github.abdessattar23.easydebug.CaptureEvent;
import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.Defaults;
import io.github.abdessattar23.easydebug.Payload;
import io.github.abdessattar23.easydebug.Redactor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds a call event. Redaction is applied here; the call is still recorded. */
public final class ExchangeAssembler {

    private final CapturePolicy policy;

    public ExchangeAssembler(CapturePolicy policy) {
        this.policy = policy;
    }

    public CaptureEvent http(String direction,
                             String hook,
                             Instant started,
                             long startedNanos,
                             String method,
                             String url,
                             Integer status,
                             Throwable error,
                             Map<String, String> requestHeaders,
                             Payload request,
                             Map<String, String> responseHeaders,
                             Payload response,
                             Map<String, String> mdc,
                             Map<String, Object> attributes) {
        Map<String, String> safeRequestHeaders = policy.redactHeaders(requestHeaders);
        Map<String, String> safeResponseHeaders = policy.redactHeaders(responseHeaders);
        Payload requestPayload = request == null ? Payload.empty() : request.withHeaders(safeRequestHeaders);
        Payload responsePayload = response == null ? null : response.withHeaders(safeResponseHeaders);
        String safeUrl = policy.redactUrl(url);
        Map<String, Object> extra = new LinkedHashMap<>();
        if (attributes != null) {
            extra.putAll(attributes);
        }
        if (error != null) {
            extra.put("stack", stack(error));
            extra.put("stackTruncated", stack(error).contains("[truncated"));
        }
        String traceId = TraceIds.from(safeRequestHeaders, mdc);
        if (traceId != null) {
            extra.putIfAbsent("traceId", traceId);
        }
        return new CaptureEvent(
                null,
                Defaults.KIND_HTTP,
                direction,
                policy.service(),
                hook,
                started,
                Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L),
                method,
                safeUrl,
                Redactor.host(url),
                status,
                summary(error),
                policy.correlation(mdc, requestHeaders),
                mdc,
                requestPayload,
                responsePayload,
                extra);
    }

    public CaptureEvent rabbit(String direction,
                               String hook,
                               String method,
                               String exchange,
                               String routingKey,
                               Integer status,
                               Throwable error,
                               Map<String, String> headers,
                               Payload body,
                               Map<String, String> mdc,
                               Instant started,
                               long startedNanos) {
        String url = "amqp://" + (exchange == null ? "" : exchange) + "/" + (routingKey == null ? "" : routingKey);
        Map<String, Object> rabbit = new LinkedHashMap<>();
        rabbit.put("exchange", exchange);
        rabbit.put("routingKey", routingKey);
        CaptureEvent event = http(direction, hook, started, startedNanos, method, url, status, error,
                headers, body, Map.of(), null, mdc, rabbit);
        return new CaptureEvent(
                event.id(), Defaults.KIND_RABBIT, event.direction(), event.service(), event.hook(),
                event.occurredAt(), event.durationMs(), event.method(), event.url(), event.host(),
                event.status(), event.error(), event.correlation(), event.mdc(),
                event.request(), event.response(), event.attributes());
    }

    private static String summary(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getName()
                : error.getClass().getName() + ": " + message;
    }

    private String stack(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        String text = writer.toString();
        int max = policy.maxBodyBytes();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "... [truncated, " + text.length() + " chars total]";
    }
}
