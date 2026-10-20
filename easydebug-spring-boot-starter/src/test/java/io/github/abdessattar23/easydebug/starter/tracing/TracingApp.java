package io.github.abdessattar23.easydebug.starter.tracing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootApplication
@RestController
public class TracingApp {

    private final WebClient boot;
    private final WebClient manual;
    private final Environment environment;

    public TracingApp(@Qualifier("boot") WebClient boot, @Qualifier("manual") WebClient manual,
                      Environment environment) {
        this.boot = boot;
        this.manual = manual;
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(TracingApp.class, args);
    }

    @GetMapping("/who")
    public Map<String, String> who(HttpServletRequest request) {
        String traceparent = request.getHeader("traceparent");
        return Map.of("traceparent", traceparent == null ? "" : traceparent);
    }

    @GetMapping("/relay")
    public Map<String, String> relay(HttpServletRequest request, @RequestParam String client) {
        int port = Integer.parseInt(environment.getProperty("local.server.port"));
        WebClient selected = "boot".equals(client) ? boot : manual;
        String downstream = selected.get()
                .uri("http://127.0.0.1:" + port + "/who")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5));
        String incoming = request.getHeader("traceparent");
        Map<String, String> body = new LinkedHashMap<>();
        body.put("incoming", incoming == null ? "" : incoming);
        body.put("downstream", downstream == null ? "" : downstream);
        return body;
    }

    @Configuration
    static class Clients {
        @Bean(name = "boot")
        WebClient boot(WebClient.Builder builder) {
            return builder.build();
        }

        @Bean(name = "manual")
        WebClient manual() {
            return WebClient.builder().build();
        }
    }
}
