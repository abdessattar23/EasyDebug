package com.orange.igr.easydebug.starter;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MdcSnapshot {

    private MdcSnapshot() {
    }

    public static Map<String, String> copy() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        context.forEach((key, value) -> {
            if (key != null && value != null) {
                out.put(key, value);
            }
        });
        return Map.copyOf(out);
    }
}
