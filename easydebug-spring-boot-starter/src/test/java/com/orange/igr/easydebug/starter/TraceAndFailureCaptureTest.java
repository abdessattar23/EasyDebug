package com.orange.igr.easydebug.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.orange.igr.easydebug.JsonMappers;
import com.orange.igr.easydebug.starter.testapp.TestApp;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TraceAndFailureCaptureTest.PosterConfig.class, TraceAndFailureCaptureTest.Clients.class})
@TestPropertySource(properties = {
        "easydebug.url=http://127.0.0.1:9",
        "easydebug.service=trace-test",
        "management.tracing.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class TraceAndFailureCaptureTest {

    private static final String TRACEPARENT = "00-6ad628b71d876f1c9cf7ba771993f23e-ac10da97254a0584-01";

    @Autowired
    private RecordingPoster poster;

    @Autowired
    @Qualifier("traced")
    private WebClient traced;

    @Autowired
    @Qualifier("staticBuilder")
    private WebClient staticBuilder;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @LocalServerPort
    private int port;

    @Test
    void preservesATraceHeaderAndDoesNotInventOne() throws Exception {
        String tracedBody = traced.get()
                .uri("http://127.0.0.1:" + port + "/headers")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5));
        assertThat(tracedBody).contains("6ad628b71d876f1c9cf7ba771993f23e");

        MDC.put("traceId", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        String plainBody;
        try {
            plainBody = staticBuilder.get()
                    .uri("http://127.0.0.1:" + port + "/headers")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));
        } finally {
            MDC.remove("traceId");
        }
        assertThat(plainBody).contains("\"traceparent\":\"\"");

        List<JsonNode> events = await("/headers");
        assertThat(events).anyMatch(event ->
                inbound(event) && event.path("request").path("headers").path("traceparent").asText().equals(TRACEPARENT)
                        && event.path("attributes").path("traceId").asText().equals("6ad628b71d876f1c9cf7ba771993f23e"));
        assertThat(events).anyMatch(event ->
                "webclient".equals(event.path("hook").asText())
                        && "OUT".equals(event.path("direction").asText())
                        && event.path("url").asText().contains("/headers")
                        && event.path("mdc").path("traceId").asText().equals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                        && !event.path("request").path("headers").has("traceparent"));
    }

    @Test
    void recordsInboundFailureAndRefusedOutboundCall() throws Exception {
        ResponseEntity<String> boom = new TestRestTemplate().getForEntity("http://127.0.0.1:" + port + "/boom", String.class);
        assertThat(boom.getStatusCode().value()).isEqualTo(500);

        assertThatThrownBy(() -> restTemplateBuilder.build()
                .getForObject("http://127.0.0.1:1/absent", String.class))
                .isInstanceOf(Exception.class);

        List<JsonNode> events = await("/boom", "127.0.0.1:1");
        assertThat(events).anyMatch(event ->
                inbound(event) && event.path("url").asText().contains("/boom")
                        && event.path("status").asInt() == 500
                        && event.path("error").asText().contains("IllegalStateException: boom"));
        assertThat(events).anyMatch(event ->
                "resttemplate".equals(event.path("hook").asText())
                        && event.path("url").asText().contains("127.0.0.1:1")
                        && event.path("error").asText().contains("ConnectException"));
    }

    @Test
    void recordsClientAndServerErrorStatuses() throws Exception {
        TestRestTemplate http = new TestRestTemplate();
        String base = "http://127.0.0.1:" + port;
        assertThat(http.getForEntity(base + "/status/400", String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.getForEntity(base + "/status/503", String.class).getStatusCode().value()).isEqualTo(503);
        assertThatThrownBy(() -> webClientBuilder.build().get()
                .uri(base + "/status/400")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5)))
                .isInstanceOf(WebClientResponseException.class)
                .extracting(error -> ((WebClientResponseException) error).getStatusCode().value())
                .isEqualTo(400);

        List<JsonNode> events = awaitMatch(found ->
                found.stream().anyMatch(event -> inbound(event) && event.path("url").asText().contains("/status/400")
                        && event.path("status").asInt() == 400)
                        && found.stream().anyMatch(event -> inbound(event) && event.path("url").asText().contains("/status/503")
                        && event.path("status").asInt() == 503)
                        && found.stream().anyMatch(event -> "webclient".equals(event.path("hook").asText())
                        && event.path("url").asText().contains("/status/400")
                        && event.path("status").asInt() == 400));
        assertThat(events).anyMatch(event ->
                inbound(event) && event.path("url").asText().contains("/status/400")
                        && event.path("status").asInt() == 400
                        && event.path("response").path("body").asText().contains("status-400")
                        && event.path("error").isNull());
        assertThat(events).anyMatch(event ->
                inbound(event) && event.path("url").asText().contains("/status/503")
                        && event.path("status").asInt() == 503
                        && event.path("response").path("body").asText().contains("status-503")
                        && event.path("error").isNull());
        assertThat(events).anyMatch(event ->
                "webclient".equals(event.path("hook").asText())
                        && "OUT".equals(event.path("direction").asText())
                        && event.path("url").asText().contains("/status/400")
                        && event.path("status").asInt() == 400
                        && event.path("response").path("body").asText().contains("status-400"));
    }

    @Test
    void recordsWebClientRefusalAndTimeout() throws Exception {
        WebClient client = webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(Duration.ofMillis(200))))
                .build();
        assertThatThrownBy(() -> client.get()
                .uri("http://127.0.0.1:" + port + "/slow")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5)))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> webClientBuilder.build().get()
                .uri("http://127.0.0.1:1/absent")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5)))
                .isInstanceOf(Exception.class);

        List<JsonNode> events = awaitMatch(found ->
                found.stream().anyMatch(event -> "webclient".equals(event.path("hook").asText())
                        && event.path("url").asText().contains("/slow")
                        && !event.path("error").asText().isBlank())
                        && found.stream().anyMatch(event -> "webclient".equals(event.path("hook").asText())
                        && event.path("url").asText().contains("127.0.0.1:1/absent")
                        && (event.path("error").asText().contains("ConnectException")
                        || event.path("attributes").path("stack").asText().contains("ConnectException"))));
        assertThat(events).anyMatch(event ->
                "webclient".equals(event.path("hook").asText())
                        && event.path("url").asText().contains("/slow")
                        && !event.path("error").asText().isBlank());
        assertThat(events).anyMatch(event ->
                "webclient".equals(event.path("hook").asText())
                        && event.path("url").asText().contains("127.0.0.1:1/absent")
                        && (event.path("error").asText().contains("ConnectException")
                        || event.path("attributes").path("stack").asText().contains("ConnectException"))
                        && event.path("status").isNull());
    }

    private List<JsonNode> awaitMatch(Predicate<List<JsonNode>> ready) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        List<JsonNode> events = List.of();
        while (System.nanoTime() < end) {
            events = events();
            if (ready.test(events)) {
                return events;
            }
            Thread.sleep(30);
        }
        throw new AssertionError(events.toString());
    }

    private List<JsonNode> await(String... required) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < end) {
            List<JsonNode> events = events();
            String blob = events.toString();
            boolean ready = true;
            for (String token : required) {
                if (!blob.contains(token)) {
                    ready = false;
                    break;
                }
            }
            if (ready) {
                return events;
            }
            Thread.sleep(30);
        }
        throw new AssertionError(events().toString());
    }

    private List<JsonNode> events() throws Exception {
        List<JsonNode> events = new ArrayList<>();
        var mapper = JsonMappers.mapper();
        for (String body : poster.bodies()) {
            for (JsonNode node : mapper.readTree(body)) {
                events.add(node);
            }
        }
        return events;
    }

    private static boolean inbound(JsonNode event) {
        return "IN".equals(event.path("direction").asText()) && "servlet".equals(event.path("hook").asText());
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class PosterConfig {
        @Bean
        RecordingPoster eventPoster() {
            return new RecordingPoster();
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class Clients {
        @Bean
        @Qualifier("traced")
        WebClient traced(WebClient.Builder builder) {
            return builder.filter((request, next) -> next.exchange(
                    ClientRequest.from(request).header("traceparent", TRACEPARENT).build())).build();
        }

        @Bean
        @Qualifier("staticBuilder")
        WebClient staticBuilder() {
            return WebClient.builder().build();
        }
    }
}
