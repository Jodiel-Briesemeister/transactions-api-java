package br.com.jodiel.transactionsapi.presentation.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "Liveness and dependency checks")
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    public HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping
    @Operation(summary = "Liveness probe: answers as long as the process is up")
    public Map<String, String> liveness() {
        return Map.of("status", "ok");
    }

    @GetMapping("/dependencies")
    @Operation(summary = "Readiness probe: reports Postgres, Redis and RabbitMQ status")
    public ResponseEntity<Map<String, Object>> dependencies() {
        HealthComponent health = healthEndpoint.health();
        boolean up = Status.UP.equals(health.getStatus());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", up ? "ok" : "degraded");
        body.put("dependencies", describeComponents(health));

        return ResponseEntity
                .status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    /**
     * HealthEndpoint#health() is typed as HealthComponent; only the composite variant carries the
     * per-dependency breakdown, so the cast has to be guarded.
     */
    private Map<String, String> describeComponents(HealthComponent health) {
        if (!(health instanceof CompositeHealth composite)) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        composite.getComponents()
                .forEach((name, component) -> result.put(name, component.getStatus().getCode()));
        return result;
    }
}
