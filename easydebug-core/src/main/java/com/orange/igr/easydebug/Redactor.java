package com.orange.igr.easydebug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Header and query redaction. The key stays; the value becomes {@link Defaults#REDACTED}. */
public final class Redactor {

    private Redactor() {
    }

    public static Map<String, String> redactHeaders(Map<String, String> headers, List<String> names) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            out.put(key, matches(key, names) ? Defaults.REDACTED : (value == null ? "" : value));
        });
        return Map.copyOf(out);
    }

    public static String redactUrl(String url, List<String> names) {
        if (url == null || url.isBlank() || names == null || names.isEmpty()) {
            return url;
        }
        int query = url.indexOf('?');
        if (query < 0 || query == url.length() - 1) {
            return url;
        }
        String base = url.substring(0, query);
        String raw = url.substring(query + 1);
        int hash = raw.indexOf('#');
        String fragment = "";
        if (hash >= 0) {
            fragment = raw.substring(hash);
            raw = raw.substring(0, hash);
        }
        String[] pairs = raw.split("&", -1);
        List<String> rebuilt = new ArrayList<>(pairs.length);
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                rebuilt.add(pair);
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String decodedKey = decode(key);
            if (matches(decodedKey, names) || matches(key, names)) {
                rebuilt.add(key + "=" + Defaults.REDACTED);
            } else {
                rebuilt.add(pair);
            }
        }
        return base + "?" + String.join("&", rebuilt) + fragment;
    }

    public static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String rest = url;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            rest = rest.substring(scheme + 3);
        } else if (rest.startsWith("/")) {
            return null;
        }
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String authority = rest.substring(0, end);
        if (authority.isBlank()) {
            return null;
        }
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            return close > 0 ? authority.substring(1, close) : authority;
        }
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int colon = authority.indexOf(':');
        String host = colon >= 0 ? authority.substring(0, colon) : authority;
        return host.isBlank() ? null : host;
    }

    private static boolean matches(String key, List<String> names) {
        if (key == null || names == null || names.isEmpty()) {
            return false;
        }
        for (String name : names) {
            if (name != null && key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value.toLowerCase(Locale.ROOT);
        }
    }
}
