package com.orange.igr.easydebug;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** JDK client on purpose: Spring hooks must not capture the ingest call a second time. */
public final class JdkEventPoster implements EventPoster {

    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;

    public JdkEventPoster(String ingestUrl, Duration timeout) {
        this.timeout = timeout == null ? Duration.ofMillis(Defaults.TIMEOUT_MILLIS) : timeout;
        this.endpoint = URI.create(ingestUrl);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public int post(String json) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "[]" : json, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("EasyDebug ingest interrupted", e);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
