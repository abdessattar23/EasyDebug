package com.orange.igr.easydebug;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One captured fact. A call, the startup policy, or a delivery gap.
 * Missing identity fields are filled so a partial event can still be stored.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CaptureEvent(
        String id,
        String kind,
        String direction,
        String service,
        String hook,
        Instant occurredAt,
        Long durationMs,
        String method,
        String url,
        String host,
        Integer status,
        String error,
        String correlation,
        Map<String, String> mdc,
        Payload request,
        Payload response,
        Map<String, Object> attributes
) {
    public CaptureEvent {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (kind == null || kind.isBlank()) {
            kind = "unknown";
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        mdc = copyStrings(mdc);
        attributes = copyAttributes(attributes);
    }

    public static CaptureEvent policy(String service, Map<String, Object> attributes) {
        return new CaptureEvent(
                null, Defaults.KIND_POLICY, null, service, "starter", Instant.now(),
                null, null, null, null, null, null, null, Map.of(), null, null, attributes);
    }

    public static CaptureEvent gap(String service, int missedCount, Instant from, Instant until) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("missedCount", missedCount);
        attributes.put("from", from == null ? null : from.toString());
        attributes.put("until", until == null ? null : until.toString());
        return new CaptureEvent(
                null, Defaults.KIND_GAP, null, service, "sender", Instant.now(),
                null, null, null, null, null,
                missedCount + " calls were not delivered",
                null, Map.of(), null, null, attributes);
    }

    static Map<String, String> copyStrings(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                out.put(key, value);
            }
        });
        return Collections.unmodifiableMap(out);
    }

    static Map<String, Object> copyAttributes(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null) {
                out.put(key, value);
            }
        });
        return Collections.unmodifiableMap(out);
    }
}
