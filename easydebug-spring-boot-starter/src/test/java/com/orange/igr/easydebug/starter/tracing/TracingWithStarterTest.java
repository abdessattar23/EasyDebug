package com.orange.igr.easydebug.starter.tracing;

import com.fasterxml.jackson.databind.JsonNode;
import com.orange.igr.easydebug.JsonMappers;
import com.orange.igr.easydebug.starter.RecordingPoster;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TracingApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@Import(TracingWithStarterTest.PosterConfig.class)
@TestPropertySource(properties = {
        "easydebug.url=http://127.0.0.1:9",
        "easydebug.service=tracing-enabled",
        "management.tracing.sampling.probability=1.0",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class TracingWithStarterTest {

    @Autowired
    private RecordingPoster poster;

    @LocalServerPort
    private int port;

    @Test
    void starterDoesNotChangeBuilderPropagation() throws Exception {
        ResponseEntity<String> boot = TraceRelay.relay(port, "boot");
        ResponseEntity<String> manual = TraceRelay.relay(port, "manual");
        assertThat(boot.getStatusCode().value()).isEqualTo(200);
        assertThat(manual.getStatusCode().value()).isEqualTo(200);
        assertThat(boot.getBody()).contains(TraceRelay.TRACE);
        assertThat(TraceRelay.downstream(boot.getBody())).contains(TraceRelay.TRACE);
        assertThat(TraceRelay.downstream(manual.getBody())).doesNotContain(TraceRelay.TRACE);

        List<JsonNode> events = awaitBoth();
        assertThat(events).anyMatch(event ->
                inboundWho(event) && event.path("request").path("headers").path("traceparent").asText().contains(TraceRelay.TRACE));
        assertThat(events).anyMatch(event ->
                inboundWho(event) && !event.path("request").path("headers").path("traceparent").asText().contains(TraceRelay.TRACE));
        assertThat(events).noneMatch(event ->
                "webclient".equals(event.path("hook").asText())
                        && event.path("url").asText().contains("/who")
                        && !event.path("request").path("headers").path("traceparent").asText().contains(TraceRelay.TRACE)
                        && event.path("request").path("headers").path("traceparent").asText().matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class PosterConfig {
        @Bean
        RecordingPoster eventPoster() {
            return new RecordingPoster();
        }
    }

    private List<JsonNode> awaitBoth() throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        List<JsonNode> events = List.of();
        while (System.nanoTime() < end) {
            events = events();
            boolean traced = events.stream().anyMatch(event ->
                    inboundWho(event) && event.path("request").path("headers").path("traceparent").asText().contains(TraceRelay.TRACE));
            boolean plain = events.stream().anyMatch(event ->
                    inboundWho(event) && !event.path("request").path("headers").path("traceparent").asText().contains(TraceRelay.TRACE));
            if (traced && plain) {
                return events;
            }
            Thread.sleep(30);
        }
        throw new AssertionError(events.toString());
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

    private static boolean inboundWho(JsonNode event) {
        return "IN".equals(event.path("direction").asText())
                && "servlet".equals(event.path("hook").asText())
                && event.path("url").asText().contains("/who");
    }
}
