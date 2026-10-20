package io.github.abdessattar23.easydebug;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    private final CapturePolicy policy = new CapturePolicy(
            "demo", "http://127.0.0.1:8090", null, null, Duration.ofSeconds(2), null,
            "/actuator", "metrics.internal", null, null, "correlationId,X-Request-Id");

    @Test
    void redactsHeaderValuesAndQuerySecretsButKeepsTheKeys() {
        Map<String, String> headers = policy.redactHeaders(Map.of(
                "Authorization", "Bearer secret",
                "Accept", "application/json"));
        String url = policy.redactUrl(
                "https://s3.example/file?X-Amz-Signature=abc&ok=1&sig=nope");

        assertThat(headers).containsEntry("Authorization", "[redacted]");
        assertThat(headers).containsEntry("Accept", "application/json");
        assertThat(url).contains("X-Amz-Signature=[redacted]");
        assertThat(url).contains("sig=[redacted]");
        assertThat(url).contains("ok=1");
        assertThat(url).doesNotContain("abc");
    }

    @Test
    void emptyRedactionListsStoreRawValuesAndAttachmentUrlsAreKept() {
        CapturePolicy open = new CapturePolicy(
                "demo", "http://127.0.0.1:8090", null, null, null, null,
                null, null, "", "", "");

        assertThat(open.redactHeaders(Map.of("Authorization", "Bearer secret")))
                .containsEntry("Authorization", "Bearer secret");
        assertThat(open.redactUrl("https://owf.example/attachment?X-Amz-Signature=abc"))
                .contains("X-Amz-Signature=abc");
        assertThat(open.excluded("https://owf.example/attachment", "/attachment", "owf.example")).isFalse();
    }

    @Test
    void excludesTheCollectorAndConfiguredPathsOnly() {
        assertThat(policy.excluded("http://127.0.0.1:8090/api/events", "/api/events", "127.0.0.1")).isTrue();
        assertThat(policy.excluded("http://127.0.0.1:8080/actuator/health", "/actuator/health", "127.0.0.1")).isTrue();
        assertThat(policy.excluded("http://127.0.0.1:8080/actuator2", "/actuator2", "127.0.0.1")).isFalse();
        assertThat(policy.excluded("http://metrics.internal/x", "/x", "metrics.internal")).isTrue();
        assertThat(policy.correlation(Map.of("correlationId", "c-1"), Map.of())).isEqualTo("c-1");
        assertThat(policy.correlation(Map.of(), Map.of("X-Request-Id", "r-1"))).isEqualTo("r-1");
    }
}
