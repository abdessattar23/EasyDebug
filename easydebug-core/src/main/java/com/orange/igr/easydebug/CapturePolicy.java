package com.orange.igr.easydebug;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capture rules read from configuration. There is no partner list and no status floor.
 * The collector URL is the only built-in exclusion, so ingest does not record itself.
 */
public final class CapturePolicy {

    private final String service;
    private final String collectorUrl;
    private final int maxBodyBytes;
    private final int queueCapacity;
    private final Duration timeout;
    private final int spoolMemoryBytes;
    private final List<String> excludePaths;
    private final List<String> excludeHosts;
    private final List<String> redactHeaders;
    private final List<String> redactQuery;
    private final List<String> correlationKeys;

    public CapturePolicy(String service,
                         String collectorUrl,
                         Integer maxBodyBytes,
                         Integer queueCapacity,
                         Duration timeout,
                         Integer spoolMemoryBytes,
                         String excludePaths,
                         String excludeHosts,
                         String redactHeaders,
                         String redactQuery,
                         String correlationKeys) {
        this.service = service == null || service.isBlank() ? "unknown" : service.trim();
        this.collectorUrl = collectorUrl == null ? "" : stripSlash(collectorUrl.trim());
        this.maxBodyBytes = maxBodyBytes == null ? Defaults.MAX_BODY_BYTES : Math.max(0, maxBodyBytes);
        this.queueCapacity = queueCapacity == null ? Defaults.QUEUE_CAPACITY : Math.max(1, queueCapacity);
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofMillis(Defaults.TIMEOUT_MILLIS) : timeout;
        this.spoolMemoryBytes = spoolMemoryBytes == null
                ? Defaults.SPOOL_MEMORY_BYTES : Math.max(0, spoolMemoryBytes);
        this.excludePaths = split(excludePaths, "");
        this.excludeHosts = split(excludeHosts, "");
        this.redactHeaders = split(redactHeaders, Defaults.REDACT_HEADERS);
        this.redactQuery = split(redactQuery, Defaults.REDACT_QUERY);
        this.correlationKeys = split(correlationKeys, "");
    }

    public String service() {
        return service;
    }

    public String collectorUrl() {
        return collectorUrl;
    }

    public int maxBodyBytes() {
        return maxBodyBytes;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public Duration timeout() {
        return timeout;
    }

    public int spoolMemoryBytes() {
        return spoolMemoryBytes;
    }

    public List<String> excludePaths() {
        return excludePaths;
    }

    public List<String> excludeHosts() {
        return excludeHosts;
    }

    public List<String> redactHeaders() {
        return redactHeaders;
    }

    public List<String> redactQuery() {
        return redactQuery;
    }

    public List<String> correlationKeys() {
        return correlationKeys;
    }

    public boolean excluded(String url, String path, String host) {
        if (isCollector(url)) {
            return true;
        }
        if (path != null) {
            for (String prefix : excludePaths) {
                if (pathMatches(path, prefix)) {
                    return true;
                }
            }
        }
        if (host != null) {
            for (String excluded : excludeHosts) {
                if (host.equalsIgnoreCase(excluded)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String redactUrl(String url) {
        return Redactor.redactUrl(url, redactQuery);
    }

    public Map<String, String> redactHeaders(Map<String, String> headers) {
        return Redactor.redactHeaders(headers, redactHeaders);
    }

    public String correlation(Map<String, String> mdc, Map<String, String> headers) {
        for (String key : correlationKeys) {
            String fromMdc = value(mdc, key);
            if (fromMdc != null) {
                return fromMdc;
            }
            String fromHeaders = value(headers, key);
            if (fromHeaders != null) {
                return fromHeaders;
            }
        }
        return null;
    }

    public Map<String, Object> describe(List<String> installed, List<String> skipped) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("service", service);
        described.put("collectorUrl", collectorUrl);
        described.put("maxBodyBytes", maxBodyBytes);
        described.put("queueCapacity", queueCapacity);
        described.put("timeout", timeout.toString());
        described.put("spoolMemoryBytes", spoolMemoryBytes);
        described.put("excludePaths", excludePaths);
        described.put("excludeHosts", excludeHosts);
        described.put("builtInExclude", List.of(
                "URLs that start with collectorUrl are not captured, so ingest does not record itself"));
        described.put("redactHeaders", redactHeaders);
        described.put("redactQuery", redactQuery);
        described.put("correlationKeys", correlationKeys);
        described.put("hooksInstalled", installed == null ? List.of() : List.copyOf(installed));
        described.put("hooksSkipped", skipped == null ? List.of() : List.copyOf(skipped));
        described.put("coverage", List.of(
                "servlet inbound: every request that enters the servlet chain",
                "webflux inbound: every request when the application type is reactive",
                "webclient: WebClient beans, and clients built from Spring's WebClient.Builder",
                "restclient: RestClient beans, and clients built from Spring Boot's RestClient.Builder",
                "resttemplate: RestTemplate beans, and clients built from RestTemplateBuilder",
                "feign: feign.Client beans, when feign is on the classpath",
                "rabbit: RabbitTemplate publishes and @RabbitListener deliveries when spring-amqp is present",
                "A client constructed in a method and never registered as a Spring bean is not hooked, unless it was built from one of those Boot builders",
                "A hand-built Netty or JDK HttpClient, and a process that is not this JVM, are not hooked"));
        return described;
    }

    public String ingestUrl() {
        return collectorUrl + "/api/events";
    }

    private boolean isCollector(String url) {
        if (collectorUrl.isBlank() || url == null || url.isBlank()) {
            return false;
        }
        return url.equals(collectorUrl) || url.startsWith(collectorUrl + "/") || url.startsWith(collectorUrl + "?");
    }

    private static boolean pathMatches(String path, String prefix) {
        if (prefix == null || prefix.isBlank() || path == null) {
            return false;
        }
        if (path.equals(prefix)) {
            return true;
        }
        String withSlash = prefix.endsWith("/") ? prefix : prefix + "/";
        return path.startsWith(withSlash);
    }

    private static String value(Map<String, String> values, String key) {
        if (values == null || key == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * @param raw null uses {@code fallback}; blank means an empty list
     */
    static List<String> split(String raw, String fallback) {
        String source = raw == null ? fallback : raw;
        if (source == null || source.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : source.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return List.copyOf(out);
    }

    private static String stripSlash(String url) {
        String stripped = url;
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }
}
