package io.github.abdessattar23.easydebug.starter.http;

import io.github.abdessattar23.easydebug.Spool;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Replays a body that the filter already read, and rebuilds parameters from the query string
 * and a form body so consuming the stream does not hide {@code @RequestParam}.
 */
final class ReplayRequest extends HttpServletRequestWrapper {

    private final Spool spool;
    private final Charset charset;
    private Map<String, String[]> parameters;

    ReplayRequest(HttpServletRequest request, Spool spool) {
        super(request);
        this.spool = spool;
        this.charset = charset(request.getCharacterEncoding());
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new SpoolServletInputStream(spool);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
        long size = spool.size();
        return size > Integer.MAX_VALUE ? -1 : (int) size;
    }

    @Override
    public long getContentLengthLong() {
        return spool.size();
    }

    @Override
    public String getParameter(String name) {
        String[] values = getParameterMap().get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if (parameters == null) {
            parameters = buildParameters();
        }
        return parameters;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(getParameterMap().keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return getParameterMap().get(name);
    }

    private Map<String, String[]> buildParameters() {
        Map<String, List<String>> acc = new LinkedHashMap<>();
        append(acc, getQueryString());
        String type = getContentType();
        if (type != null && type.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            try (ServletInputStream in = getInputStream()) {
                append(acc, new String(in.readAllBytes(), charset));
            } catch (IOException ignored) {
                // the call is still recorded; parameters fall back to the query string
            }
        }
        Map<String, String[]> out = new LinkedHashMap<>();
        acc.forEach((key, values) -> out.put(key, values.toArray(String[]::new)));
        return Collections.unmodifiableMap(out);
    }

    private void append(Map<String, List<String>> acc, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String pair : raw.split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            acc.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, charset);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static Charset charset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
