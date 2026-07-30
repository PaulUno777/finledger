package com.pauluno.finledger.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Single line of a journal entry.
 * <p>
 * Sign convention: credit is positive, debit is negative.
 */
public record Posting(
        UUID accountId,
        Money amount,
        SettlementStatus settlementStatus
) {

    public Posting {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(settlementStatus, "settlementStatus");
        if (amount.isZero()) {
            throw new IllegalArgumentException("Posting amount must not be zero");
        }
    }

    public Posting reversed() {
        return new Posting(accountId, amount.negated(), settlementStatus);
    }
}
