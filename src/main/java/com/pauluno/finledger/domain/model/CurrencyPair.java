package com.pauluno.finledger.domain.model;

import java.util.Currency;
import java.util.Objects;

/**
 * Ordered currency pair for FX quotes: {@code rate} means 1 unit of {@code base} = rate units of {@code quote}.
 */
public record CurrencyPair(Currency base, Currency quote) {

    public CurrencyPair {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(quote, "quote");
        if (base.equals(quote)) {
            throw new IllegalArgumentException("Currency pair base and quote must differ");
        }
    }

    public static CurrencyPair of(Currency base, Currency quote) {
        return new CurrencyPair(base, quote);
    }

    public static CurrencyPair of(String baseCode, String quoteCode) {
        return of(Currency.getInstance(baseCode), Currency.getInstance(quoteCode));
    }

    public CurrencyPair inverse() {
        return new CurrencyPair(quote, base);
    }

    @Override
    public String toString() {
        return base.getCurrencyCode() + "/" + quote.getCurrencyCode();
    }
}
