package com.pauluno.finledger.infrastructure.security;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * External issuer JWKS readiness: empty/unavailable JWKS fails readiness, not boot.
 */
@Component("jwks")
@ConditionalOnProperty(prefix = "finledger.security", name = "issuer", havingValue = "external", matchIfMissing = true)
public class JwksReadiness implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(JwksReadiness.class);

    private final AtomicBoolean ready = new AtomicBoolean(true);
    private final AtomicReference<String> detail = new AtomicReference<>("JWKS not fetched yet");

    public boolean isReady() {
        return ready.get();
    }

    public void markReady(String uri) {
        if (ready.compareAndSet(false, true)) {
            log.info("JWKS ready from {}", uri);
        }
        detail.set("JWKS ready from " + uri);
    }

    public void markEmpty(String uri) {
        ready.set(false);
        detail.set("JWKS empty from " + uri);
        log.warn("JWKS empty from {}, retrying", uri);
    }

    public void markFailed(String uri, String reason) {
        ready.set(false);
        detail.set("JWKS unavailable from " + uri + ": " + reason);
        log.warn("JWKS unavailable from {}: {}", uri, reason);
    }

    @Override
    public Health health() {
        if (ready.get()) {
            return Health.up().withDetail("jwks", detail.get()).build();
        }
        return Health.down().withDetail("jwks", detail.get()).build();
    }
}
