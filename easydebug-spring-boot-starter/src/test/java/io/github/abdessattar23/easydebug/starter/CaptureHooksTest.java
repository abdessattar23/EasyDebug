package io.github.abdessattar23.easydebug.starter;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.abdessattar23.easydebug.JsonMappers;
import io.github.abdessattar23.easydebug.starter.testapp.TestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({CaptureHooksTest.PosterConfig.class, CaptureHooksTest.ClientsConfig.class})
@TestPropertySource(properties = {
        "easydebug.url=http://127.0.0.1:9",
        "easydebug.service=starter-test",
        "management.tracing.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class CaptureHooksTest {

    @Autowired
    private RecordingPoster poster;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    private RestTemplate plainRestTemplate;

    @Autowired
    private WebClient plainWebClient;

    @Autowired
    private RestClient plainRestClient;

    @Autowired
    private feign.Client feignClient;

    @LocalServerPort
    private int port;

    @Test
    void recordsInboundOutboundMultipartAndPolicy() throws Exception {
        TestRestTemplate http = new TestRestTemplate();
        String base = "http://127.0.0.1:" + port;

        ResponseEntity<String> form = http.postForEntity(base + "/form",
                form("name=annexe"), String.class);
        assertThat(form.getStatusCode().value()).isEqualTo(200);
        assertThat(form.getBody()).contains("annexe");

        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", file("annexe.xlsx", "PK-demo".getBytes(StandardCharsets.UTF_8)));
        ResponseEntity<String> upload = http.postForEntity(base + "/upload", parts, String.class);
        assertThat(upload.getStatusCode().value()).isEqualTo(200);
        assertThat(upload.getBody()).contains("annexe.xlsx");

        String echoed = webClientBuilder.build()
                .post()
                .uri(base + "/echo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer secret")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello-webclient")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5));
        assertThat(echoed).contains("hello-webclient");

        String fromRestClient = restClientBuilder.build()
                .post()
                .uri(base + "/echo")
                .contentType(MediaType.TEXT_PLAIN)
                .body("hello-restclient")
                .retrieve()
                .body(String.class);
        assertThat(fromRestClient).contains("hello-restclient");

        RestTemplate restTemplate = restTemplateBuilder.build();
        String fromTemplate = restTemplate.postForObject(base + "/echo",
                new HttpEntity<>("hello-resttemplate", textHeaders()), String.class);
        assertThat(fromTemplate).contains("hello-resttemplate");

        String fromPlainTemplate = plainRestTemplate.postForObject(base + "/echo",
                new HttpEntity<>("hello-plain-template", textHeaders()), String.class);
        assertThat(fromPlainTemplate).contains("hello-plain-template");

        String fromPlainWeb = plainWebClient.post()
                .uri(base + "/echo")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello-plain-webclient")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5));
        assertThat(fromPlainWeb).contains("hello-plain-webclient");

        String fromPlainRest = plainRestClient.post()
                .uri(base + "/echo")
                .contentType(MediaType.TEXT_PLAIN)
                .body("hello-plain-restclient")
                .retrieve()
                .body(String.class);
        assertThat(fromPlainRest).contains("hello-plain-restclient");

        byte[] feignBody = "hello-feign".getBytes(StandardCharsets.UTF_8);
        Map<String, Collection<String>> feignHeaders = new LinkedHashMap<>();
        feignHeaders.put(HttpHeaders.CONTENT_TYPE, List.of(MediaType.TEXT_PLAIN_VALUE));
        feign.Request feignRequest = feign.Request.create(
                feign.Request.HttpMethod.POST,
                base + "/echo",
                feignHeaders,
                feignBody,
                StandardCharsets.UTF_8);
        try (feign.Response feignResponse = feignClient.execute(feignRequest, new feign.Request.Options())) {
            assertThat(feignResponse.status()).isEqualTo(200);
            assertThat(new String(feignResponse.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8))
                    .contains("hello-feign");
        }

        List<JsonNode> events = awaitCapturedCalls();

        assertThat(events).anyMatch(event ->
                "capture.policy".equals(event.path("kind").asText())
                        && text(event.path("attributes").path("hooksInstalled")).contains("servlet-inbound")
                        && text(event.path("attributes").path("hooksInstalled")).contains("webclient")
                        && text(event.path("attributes").path("hooksInstalled")).contains("rabbit-template")
                        && text(event.path("attributes").path("hooksInstalled")).contains("feign"));
        assertThat(events).anyMatch(event ->
                inbound(event, "/upload") && event.path("request").path("filename").asText().equals("annexe.xlsx"));
        assertThat(events).anyMatch(event ->
                inbound(event, "/form") && event.path("request").path("body").asText().contains("name=annexe"));
        assertThat(events).anyMatch(event ->
                "webclient".equals(event.path("hook").asText())
                        && event.path("request").path("body").asText().contains("hello-webclient")
                        && event.path("request").path("headers").path("Authorization").asText().equals("[redacted]"));
        assertThat(events).anyMatch(event ->
                "restclient".equals(event.path("hook").asText())
                        && event.path("request").path("body").asText().contains("hello-restclient"));
        assertThat(events).anyMatch(event ->
                "resttemplate".equals(event.path("hook").asText())
                        && event.path("request").path("body").asText().contains("hello-resttemplate"));
        assertThat(countHook(events, "resttemplate", "hello-resttemplate")).isEqualTo(1);
        assertThat(countHook(events, "resttemplate", "hello-plain-template")).isEqualTo(1);
        assertThat(countHook(events, "webclient", "hello-webclient")).isEqualTo(1);
        assertThat(countHook(events, "webclient", "hello-plain-webclient")).isEqualTo(1);
        assertThat(countHook(events, "restclient", "hello-restclient")).isEqualTo(1);
        assertThat(countHook(events, "restclient", "hello-plain-restclient")).isEqualTo(1);
        assertThat(countHook(events, "feign", "hello-feign")).isEqualTo(1);
        assertThat(events).noneMatch(event -> event.toString().contains("Bearer secret"));
    }

    private List<JsonNode> awaitCapturedCalls() throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        List<JsonNode> events = List.of();
        while (System.nanoTime() < end) {
            events = events();
            boolean policy = events.stream().anyMatch(event -> "capture.policy".equals(event.path("kind").asText()));
            boolean upload = events.stream().anyMatch(event -> inbound(event, "/upload"));
            boolean form = events.stream().anyMatch(event -> inbound(event, "/form"));
            if (policy && upload && form
                    && countHook(events, "resttemplate", "hello-resttemplate") == 1
                    && countHook(events, "resttemplate", "hello-plain-template") == 1
                    && countHook(events, "webclient", "hello-webclient") == 1
                    && countHook(events, "webclient", "hello-plain-webclient") == 1
                    && countHook(events, "restclient", "hello-restclient") == 1
                    && countHook(events, "restclient", "hello-plain-restclient") == 1
                    && countHook(events, "feign", "hello-feign") == 1) {
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

    private static long countHook(List<JsonNode> events, String hook, String body) {
        return events.stream()
                .filter(event -> hook.equals(event.path("hook").asText())
                        && event.path("request").path("body").asText().contains(body))
                .count();
    }

    private static boolean inbound(JsonNode event, String path) {
        return "IN".equals(event.path("direction").asText())
                && "servlet".equals(event.path("hook").asText())
                && event.path("url").asText().contains(path);
    }

    private static String text(JsonNode node) {
        return node.toString();
    }

    private static HttpEntity<String> form(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return new HttpEntity<>(body, headers);
    }

    private static HttpHeaders textHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return headers;
    }

    private static ByteArrayResource file(String name, byte[] bytes) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class PosterConfig {
        @Bean
        RecordingPoster eventPoster() {
            return new RecordingPoster();
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class ClientsConfig {
        @Bean
        RestTemplate plainRestTemplate() {
            return new RestTemplate();
        }

        @Bean
        WebClient plainWebClient() {
            return WebClient.create();
        }

        @Bean
        RestClient plainRestClient() {
            return RestClient.create();
        }

        @Bean
        feign.Client feignClient() {
            return new feign.Client.Default(null, null);
        }
    }
}
