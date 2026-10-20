package io.github.abdessattar23.easydebug;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One side of a call. {@code size} and {@code sha256} describe the full bytes.
 * {@code body} is the prefix kept up to the configured cap.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Payload(
        Map<String, String> headers,
        String contentType,
        String filename,
        long size,
        String sha256,
        boolean truncated,
        String body,
        String bodyEncoding
) {
    public Payload {
        headers = copy(headers);
    }

    public static Payload empty() {
        return new Payload(Map.of(), null, null, 0, BodyCapture.sha256(new byte[0]), false, null, null);
    }

    public Payload withHeaders(Map<String, String> headers) {
        return new Payload(headers, contentType, filename, size, sha256, truncated, body, bodyEncoding);
    }

    private static Map<String, String> copy(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (key != null) {
                out.put(key, value == null ? "" : value);
            }
        });
        return Collections.unmodifiableMap(out);
    }
}
