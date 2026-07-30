package com.pauluno.finledger.domain.rail;

/**
 * Lifecycle status of a rail payment instruction (plan §7).
 */
public enum RailSettlementStatus {
    INITIATED,
    SETTLED,
    FAILED
}
