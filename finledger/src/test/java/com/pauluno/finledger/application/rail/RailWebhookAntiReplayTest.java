package com.pauluno.finledger.application.rail;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.exception.BusinessRuleException;

@Tag("unit")
class RailWebhookAntiReplayTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);
    private final RailWebhookAntiReplay antiReplay = new RailWebhookAntiReplay(Duration.ofMinutes(5), clock);

    @Test
    void accepts_fresh_nonce_within_skew() {
        String ts = String.valueOf(clock.instant().getEpochSecond());
        assertDoesNotThrow(() -> antiReplay.verify(ts, "n-1"));
    }

    @Test
    void rejects_skew() {
        String ts = String.valueOf(clock.instant().minus(Duration.ofMinutes(10)).getEpochSecond());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> antiReplay.verify(ts, "n-skew"));
        assertEquals("WEBHOOK_TIMESTAMP_SKEW", ex.code());
    }

    @Test
    void rejects_replayed_nonce() {
        String ts = String.valueOf(clock.instant().getEpochSecond());
        antiReplay.verify(ts, "n-replay");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> antiReplay.verify(ts, "n-replay"));
        assertEquals("WEBHOOK_REPLAY", ex.code());
    }
}
