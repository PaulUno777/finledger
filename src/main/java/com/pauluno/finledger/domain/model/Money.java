package com.pauluno.finledger.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

import com.pauluno.finledger.domain.exception.CurrencyMismatchException;

/**
 * Immutable monetary amount. Amounts are signed so a {@code Posting} can encode
 * debit (negative) and credit (positive) in a single value.
 * Scale follows ISO 4217 default fraction digits; rounding is {@link RoundingMode#HALF_EVEN}.
 * Arithmetic across different currencies is forbidden.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(currency.getDefaultFractionDigits(), ROUNDING);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, Currency currency) {
        return of(new BigDecimal(amount), currency);
    }

    public static Money zero(Currency currency) {
        return of(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return of(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return of(amount.subtract(other.amount), currency);
    }

    public Money negated() {
        return of(amount.negate(), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: " + currency.getCurrencyCode()
                            + " vs " + other.currency.getCurrencyCode());
        }
    }
}
