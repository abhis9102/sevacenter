package app.sevacenter.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness endpoint for M0 — proves the app boots and serves traffic.
 * Deep health (DB, etc.) is covered by Spring Boot Actuator at {@code /actuator/health}.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of(
                "status", "ok",
                "service", "sevacenter-backend");
    }
}
