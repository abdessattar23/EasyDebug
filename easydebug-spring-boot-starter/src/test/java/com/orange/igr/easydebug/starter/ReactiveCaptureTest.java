package com.orange.igr.easydebug.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.orange.igr.easydebug.JsonMappers;
import com.orange.igr.easydebug.starter.reactivetest.ReactiveApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ReactiveApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ReactiveCaptureTest.PosterConfig.class)
@TestPropertySource(properties = {
        "easydebug.url=http://127.0.0.1:9",
        "easydebug.service=reactive-test",
        "spring.main.web-application-type=reactive",
        "management.tracing.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class ReactiveCaptureTest {

    @Autowired
    private RecordingPoster poster;

    @Autowired
    private WebTestClient web;

    @Test
    void recordsReactiveInbound() throws Exception {
        web.post().uri("/echo")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello-webflux")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("hello-webflux"));

        List<JsonNode> events = await();
        assertThat(events).anyMatch(event ->
                "capture.policy".equals(event.path("kind").asText())
                        && event.path("attributes").path("hooksInstalled").toString().contains("webflux-inbound")
                        && !event.path("attributes").path("hooksSkipped").toString().contains("webflux-inbound"));
        assertThat(events).anyMatch(event ->
                "IN".equals(event.path("direction").asText())
                        && "webflux".equals(event.path("hook").asText())
                        && event.path("url").asText().contains("/echo")
                        && event.path("request").path("body").asText().contains("hello-webflux")
                        && event.path("response").path("body").asText().contains("hello-webflux"));
    }

    private List<JsonNode> await() throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < end) {
            List<JsonNode> events = events();
            boolean inbound = events.stream().anyMatch(event ->
                    "webflux".equals(event.path("hook").asText())
                            && event.path("request").path("body").asText().contains("hello-webflux"));
            boolean policy = events.stream().anyMatch(event -> "capture.policy".equals(event.path("kind").asText()));
            if (inbound && policy) {
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

    @org.springframework.boot.test.context.TestConfiguration
    static class PosterConfig {
        @Bean
        RecordingPoster eventPoster() {
            return new RecordingPoster();
        }
    }
}
