package com.orange.igr.easydebug.starter.tracing;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

final class TraceRelay {

    static final String TRACE = "6ad628b71d876f1c9cf7ba771993f23e";
    static final String TRACEPARENT = "00-" + TRACE + "-ac10da97254a0584-01";

    private TraceRelay() {
    }

    static ResponseEntity<String> relay(int port, String client) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("traceparent", TRACEPARENT);
        return new TestRestTemplate().exchange(
                "http://127.0.0.1:" + port + "/relay?client=" + client,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    static String downstream(String body) {
        int marker = body == null ? -1 : body.indexOf("\"downstream\"");
        return marker < 0 ? "" : body.substring(marker);
    }
}
