package com.pauluno.finledger.application.rail;

import com.pauluno.finledger.domain.rail.RailSettlementStatus;

/**
 * Result of initiating a rail payment.
 */
public record RailTransactionResult(
        String railReference,
        RailSettlementStatus status
) {
}
