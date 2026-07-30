package com.pauluno.finledger.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable FX quote. Never recalculated after being stamped on a {@link JournalEntry}.
 */
public record ExchangeRate(
        CurrencyPair pair,
        BigDecimal rate,
        RateSource source,
        Instant asOf,
        boolean stale
) {
    private static final int RATE_SCALE = 12;

    public ExchangeRate {
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(asOf, "asOf");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        rate = rate.setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
    }

    public ExchangeRate withSpreadBps(int spreadBps) {
        if (spreadBps == 0) {
            return this;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(spreadBps).divide(BigDecimal.valueOf(10_000), RATE_SCALE, RoundingMode.HALF_EVEN));
        return new ExchangeRate(pair, rate.multiply(multiplier), source, asOf, stale);
    }

    public ExchangeRate inverse() {
        return new ExchangeRate(
                pair.inverse(),
                BigDecimal.ONE.divide(rate, RATE_SCALE, RoundingMode.HALF_EVEN),
                source,
                asOf,
                stale
        );
    }
}
