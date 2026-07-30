package com.pauluno.finledger.application.port.out;

/**
 * Thin business meters for ledger operations (plan §2.3 observability).
 */
public interface LedgerMetrics {

    void journalPosted();

    void riskDenied(String reasonCode);
}
