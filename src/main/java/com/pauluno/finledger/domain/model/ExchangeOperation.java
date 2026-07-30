package com.pauluno.finledger.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

import com.pauluno.finledger.domain.exception.CurrencyMismatchException;

/**
 * Explicit, auditable conversion between currencies. The only legal way to derive
 * a {@link Money} amount in another currency.
 */
public final class ExchangeOperation {

    private ExchangeOperation() {
    }

    /**
     * Converts {@code source} using {@code rate}: source currency must equal the pair base;
     * result is in the pair quote.
     */
    public static Money convert(Money source, ExchangeRate rate) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(rate, "rate");
        if (!source.currency().equals(rate.pair().base())) {
            throw new CurrencyMismatchException(
                    "Cannot convert " + source.currency().getCurrencyCode()
                            + " with rate " + rate.pair()
                            + " (base must match source currency)");
        }
        BigDecimal converted = source.amount().multiply(rate.rate());
        return Money.of(converted, rate.pair().quote());
    }
}
