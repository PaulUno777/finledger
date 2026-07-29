package com.pauluno.finledger.shared.money;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
    public Money {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Amount cannot be negative");
        }
    }
}
