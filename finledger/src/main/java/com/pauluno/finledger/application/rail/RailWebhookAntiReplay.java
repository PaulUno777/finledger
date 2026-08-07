package com.pauluno.finledger.application.rail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.pauluno.finledger.application.exception.BusinessRuleException;

/**
 * Inbound rail webhook anti-replay: timestamp skew window + single-use nonce (in-memory, single-node).
 */
public final class RailWebhookAntiReplay {

    private final Duration maxSkew;
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();
    private final Clock clock;

    public RailWebhookAntiReplay(Duration maxSkew, Clock clock) {
        this.maxSkew = Objects.requireNonNull(maxSkew, "maxSkew");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void verify(String timestampHeader, String nonce) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new BusinessRuleException("WEBHOOK_TIMESTAMP_SKEW", "Missing or blank webhook timestamp");
        }
        if (nonce == null || nonce.isBlank()) {
            throw new BusinessRuleException("WEBHOOK_REPLAY", "Missing or blank webhook nonce");
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessRuleException("WEBHOOK_TIMESTAMP_SKEW", "Webhook timestamp must be unix epoch seconds");
        }
        Instant now = clock.instant();
        Instant ts = Instant.ofEpochSecond(epochSeconds);
        long skewSeconds = Math.abs(Duration.between(now, ts).getSeconds());
        if (skewSeconds > maxSkew.getSeconds()) {
            throw new BusinessRuleException(
                    "WEBHOOK_TIMESTAMP_SKEW",
                    "Webhook timestamp outside allowed skew window");
        }
        purgeExpired(now.getEpochSecond());
        Long previous = seenNonces.putIfAbsent(nonce, epochSeconds);
        if (previous != null) {
            throw new BusinessRuleException("WEBHOOK_REPLAY", "Webhook nonce already used");
        }
    }

    private void purgeExpired(long nowEpochSeconds) {
        long min = nowEpochSeconds - maxSkew.getSeconds();
        seenNonces.entrySet().removeIf(e -> e.getValue() < min);
    }
}
