package io.github.abdessattar23.easydebug.starter.reactivetest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SpringBootApplication
@RestController
public class ReactiveApp {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveApp.class, args);
    }

    @PostMapping("/echo")
    public Mono<String> echo(@RequestBody String body) {
        return Mono.just(body);
    }
}
