package com.example.demo;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of("application", "devops-demo", "status", "UP", "timestamp", Instant.now().toString());
    }

    @GetMapping("/api/status")
    public Map<String, String> status() {
        return Map.of("status", "UP", "service", "demo-api");
    }
}
