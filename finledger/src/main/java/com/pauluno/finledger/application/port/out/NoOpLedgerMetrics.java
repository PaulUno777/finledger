package com.pauluno.finledger.application.port.out;

/**
 * No-op metrics for unit tests.
 */
public final class NoOpLedgerMetrics implements LedgerMetrics {

    @Override
    public void journalPosted() {
        // no-op
    }

    @Override
    public void riskDenied(String reasonCode) {
        // no-op
    }
}
