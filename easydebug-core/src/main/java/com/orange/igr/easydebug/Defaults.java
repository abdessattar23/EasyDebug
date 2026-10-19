package com.orange.igr.easydebug;

/**
 * Names and default values. An empty configured list means "none", not "fall back to the default".
 * A null property means the default applies.
 */
public final class Defaults {

    public static final int MAX_BODY_BYTES = 1024 * 1024;
    public static final int QUEUE_CAPACITY = 10_000;
    public static final int SPOOL_MEMORY_BYTES = 8 * 1024 * 1024;
    public static final long TIMEOUT_MILLIS = 2_000L;
    public static final String REDACT_HEADERS = "Authorization,Cookie,Set-Cookie,Proxy-Authorization";
    public static final String REDACT_QUERY = "sig,signature,X-Amz-Signature,X-Amz-Security-Token";
    public static final String REDACTED = "[redacted]";

    public static final String KIND_HTTP = "http";
    public static final String KIND_RABBIT = "rabbit";
    public static final String KIND_POLICY = "capture.policy";
    public static final String KIND_GAP = "capture.gap";

    public static final String DIRECTION_IN = "IN";
    public static final String DIRECTION_OUT = "OUT";

    private Defaults() {
    }
}
