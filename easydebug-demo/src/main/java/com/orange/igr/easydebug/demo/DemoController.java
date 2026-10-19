package com.orange.igr.easydebug.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@RestController
public class DemoController {

    private final WebClient webClient;
    private final int port;

    public DemoController(WebClient.Builder builder, @Value("${server.port:8080}") int port) {
        this.webClient = builder.build();
        this.port = port;
    }

    @GetMapping("/demo/outbound")
    public Map<String, Object> outbound() {
        String body = webClient.get()
                .uri("http://127.0.0.1:" + port + "/demo/downstream")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return Map.of("downstream", body == null ? "" : body);
    }

    @GetMapping("/demo/downstream")
    public Map<String, Object> downstream() {
        return Map.of("ok", true);
    }

    @GetMapping("/demo/boom")
    public String boom() {
        throw new IllegalStateException("demo failure");
    }
}
