package com.luckledger.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness/readiness probe. Returns {@code 200} with {@code {"status":"UP"}} when the service is
 * functional. (There is no external datastore — the game state is in-memory — so there are no
 * critical dependencies to be down; the {@code 503} path is reserved for future wiring.)
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
