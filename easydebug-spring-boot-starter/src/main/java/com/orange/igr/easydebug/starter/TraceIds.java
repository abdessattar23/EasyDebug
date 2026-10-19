package com.orange.igr.easydebug.starter;

import java.util.Map;

/**
 * Reads a W3C trace id the call already carries. EasyDebug does not create one
 * and does not rewrite the header.
 *
 * <p>A present {@code traceparent} header is the only source considered. An invalid
 * value yields nothing, including when MDC holds a trace id. MDC is used only when
 * the header is absent, and only for a 32-character hexadecimal {@code traceId}
 * that is not all zeros.
 */
final class TraceIds {

    private TraceIds() {
    }

    static String from(Map<String, String> headers, Map<String, String> mdc) {
        if (contains(headers, "traceparent")) {
            return w3c(value(headers, "traceparent"));
        }
        return mdcTraceId(mdc);
    }

    /**
     * @return the trace id from a version {@code 00} traceparent, or null when the
     * value is missing, malformed, or a version this reader does not accept
     */
    static String w3c(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String value = traceparent.trim();
        if (value.isEmpty() || value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0) {
            return null;
        }
        String[] parts = value.split("-", -1);
        if (parts.length != 4) {
            return null;
        }
        if (!parts[0].equalsIgnoreCase("00")) {
            return null;
        }
        if (!hex(parts[1], 32) || zeros(parts[1])) {
            return null;
        }
        if (!hex(parts[2], 16) || zeros(parts[2])) {
            return null;
        }
        if (!hex(parts[3], 2)) {
            return null;
        }
        return parts[1].toLowerCase();
    }

    static String mdcTraceId(Map<String, String> mdc) {
        String traceId = value(mdc, "traceId");
        if (traceId == null || !hex(traceId, 32) || zeros(traceId)) {
            return null;
        }
        return traceId.toLowerCase();
    }

    private static boolean contains(Map<String, String> values, String name) {
        if (values == null || name == null) {
            return false;
        }
        for (String key : values.keySet()) {
            if (key != null && key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String value(Map<String, String> values, String name) {
        if (values == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean hex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    private static boolean zeros(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
