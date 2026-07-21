package com.pauluno.finledger.presentation.rest.health;

import java.time.OffsetDateTime;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController implements HealthIndicator {

    @GetMapping
    public Health getStatus() {
        return health();
    }

    @Override
    public Health health() {
        // Custom ping logic or default UP indicator
        return Health.up()
                .withDetail("timestamp", OffsetDateTime.now())
                .withDetail("service", "FinLedger API")
                .withDetail("version", "1.0.0")
                .withDetail("system", "Operational")
                .withDetail("status", "UP")
                .build();
    }
}