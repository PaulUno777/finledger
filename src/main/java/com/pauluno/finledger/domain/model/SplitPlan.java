package com.pauluno.finledger.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolved credit legs for a split (account id + positive amount).
 */
public record SplitPlan(List<SplitLeg> legs) {

    public SplitPlan {
        Objects.requireNonNull(legs, "legs");
        legs = List.copyOf(legs);
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("SplitPlan legs must not be empty");
        }
    }

    public record SplitLeg(UUID accountId, Money amount) {
        public SplitLeg {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(amount, "amount");
            if (amount.isNegative() || amount.isZero()) {
                throw new IllegalArgumentException("Split leg amount must be positive");
            }
        }
    }
}
