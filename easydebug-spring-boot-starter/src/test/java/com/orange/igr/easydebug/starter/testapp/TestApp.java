package com.orange.igr.easydebug.starter.testapp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@SpringBootApplication
@RestController
public class TestApp {

    public static void main(String[] args) {
        SpringApplication.run(TestApp.class, args);
    }

    @GetMapping("/in")
    public Map<String, Object> in() {
        return Map.of("ok", true);
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody String body) {
        return Map.of("echo", body);
    }

    @PostMapping(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> form(@RequestParam String name) {
        return Map.of("name", name);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        return Map.of("filename", file.getOriginalFilename(), "size", file.getSize());
    }

    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("boom");
    }

    @GetMapping("/headers")
    public Map<String, String> headers(HttpServletRequest request) {
        String traceparent = request.getHeader("traceparent");
        return Map.of("traceparent", traceparent == null ? "" : traceparent);
    }

    @GetMapping("/status/{code}")
    public ResponseEntity<String> status(@PathVariable int code) {
        return ResponseEntity.status(code).body("status-" + code);
    }

    @GetMapping("/slow")
    public String slow() throws InterruptedException {
        Thread.sleep(1_500L);
        return "late";
    }
}
