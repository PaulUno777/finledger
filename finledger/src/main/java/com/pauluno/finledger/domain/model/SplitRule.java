package com.pauluno.finledger.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One percentage leg of a declarative split (plan §5.1).
 */
public record SplitRule(AccountType targetAccountType, BigDecimal percentage) {

    public SplitRule {
        Objects.requireNonNull(targetAccountType, "targetAccountType");
        Objects.requireNonNull(percentage, "percentage");
        if (percentage.signum() < 0) {
            throw new IllegalArgumentException("percentage must be >= 0");
        }
        if (percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("percentage must be <= 100");
        }
    }
}
