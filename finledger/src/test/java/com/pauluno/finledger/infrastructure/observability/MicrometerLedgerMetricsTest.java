package com.pauluno.finledger.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Tag("unit")
class MicrometerLedgerMetricsTest {

    @Test
    void should_increment_journal_posted_and_risk_denied() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerLedgerMetrics metrics = new MicrometerLedgerMetrics(registry);

        metrics.journalPosted();
        metrics.journalPosted();
        metrics.riskDenied("AMOUNT_THRESHOLD");

        assertThat(registry.counter("finledger.journal.posted").count()).isEqualTo(2.0);
        assertThat(registry.counter("finledger.risk.denied", "reason", "AMOUNT_THRESHOLD").count())
                .isEqualTo(1.0);
    }
}
