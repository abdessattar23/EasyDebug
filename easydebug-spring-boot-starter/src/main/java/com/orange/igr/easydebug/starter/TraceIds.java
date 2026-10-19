package com.orange.igr.easydebug.starter;

import java.util.Map;

/** Reads a trace id the call already carries. EasyDebug does not create one. */
final class TraceIds {

    private TraceIds() {
    }

    static String from(Map<String, String> headers, Map<String, String> mdc) {
        String fromHeader = w3c(header(headers, "traceparent"));
        if (fromHeader != null) {
            return fromHeader;
        }
        return hex(value(mdc, "traceId"));
    }

    static String w3c(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length < 4) {
            return null;
        }
        return hex(parts[1]);
    }

    private static String hex(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        int length = trimmed.length();
        if (length != 16 && length != 32) {
            return null;
        }
        for (int i = 0; i < length; i++) {
            char c = trimmed.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) {
                return null;
            }
        }
        return trimmed.toLowerCase();
    }

    private static String header(Map<String, String> headers, String name) {
        return value(headers, name);
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
}
