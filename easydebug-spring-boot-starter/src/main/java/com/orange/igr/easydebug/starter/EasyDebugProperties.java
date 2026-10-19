package com.orange.igr.easydebug.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bound from {@code easydebug.*} or the matching {@code EASYDEBUG_*} environment variables.
 * A blank list property means empty (no redaction, no extra excludes). Null means the built-in default.
 */
@ConfigurationProperties(prefix = "easydebug")
public class EasyDebugProperties {

    /** Collector base URL, for example {@code http://127.0.0.1:8090}. Blank disables capture. */
    private String url;

    /** Service name stored on every event. Falls back to {@code spring.application.name}. */
    private String service;

    /** Stored body prefix cap. The hash and the size still cover the full bytes. */
    private Integer maxBodyBytes;

    /** In-memory queue. When it is full, the next successful send includes a gap event. */
    private Integer queueCapacity;

    /** Connect and read timeout for the ingest POST. */
    private Duration timeout;

    /** Bytes kept in memory before a body spills to a temp file. */
    private Integer spoolMemoryBytes;

    /** Comma-separated path prefixes that are not captured. */
    private String excludePaths;

    /** Comma-separated hosts that are not captured. */
    private String excludeHosts;

    /** Comma-separated header names whose values are replaced with {@code [redacted]}. */
    private String redactHeaders;

    /** Comma-separated query parameter names whose values are replaced with {@code [redacted]}. */
    private String redactQuery;

    /**
     * Comma-separated MDC or header names copied into the convenience {@code correlation} field.
     * The full MDC map is always stored either way.
     */
    private String correlationKeys;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public Integer getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(Integer maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public Integer getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(Integer queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Integer getSpoolMemoryBytes() {
        return spoolMemoryBytes;
    }

    public void setSpoolMemoryBytes(Integer spoolMemoryBytes) {
        this.spoolMemoryBytes = spoolMemoryBytes;
    }

    public String getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(String excludePaths) {
        this.excludePaths = excludePaths;
    }

    public String getExcludeHosts() {
        return excludeHosts;
    }

    public void setExcludeHosts(String excludeHosts) {
        this.excludeHosts = excludeHosts;
    }

    public String getRedactHeaders() {
        return redactHeaders;
    }

    public void setRedactHeaders(String redactHeaders) {
        this.redactHeaders = redactHeaders;
    }

    public String getRedactQuery() {
        return redactQuery;
    }

    public void setRedactQuery(String redactQuery) {
        this.redactQuery = redactQuery;
    }

    public String getCorrelationKeys() {
        return correlationKeys;
    }

    public void setCorrelationKeys(String correlationKeys) {
        this.correlationKeys = correlationKeys;
    }
}
