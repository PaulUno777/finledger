package com.pauluno.finledger.infrastructure.observability;

import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.LedgerMetrics;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer-backed ledger business metrics (FL-150).
 */
@Component
public class MicrometerLedgerMetrics implements LedgerMetrics {

    private final MeterRegistry meterRegistry;

    public MicrometerLedgerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void journalPosted() {
        meterRegistry.counter("finledger.journal.posted").increment();
    }

    @Override
    public void riskDenied(String reasonCode) {
        meterRegistry.counter("finledger.risk.denied", "reason", sanitize(reasonCode)).increment();
    }

    private static String sanitize(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "unknown";
        }
        return reasonCode.length() > 64 ? reasonCode.substring(0, 64) : reasonCode;
    }
}
