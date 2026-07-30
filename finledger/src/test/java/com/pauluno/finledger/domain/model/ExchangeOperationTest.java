package com.pauluno.finledger.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.domain.exception.CurrencyMismatchException;

@Tag("unit")
class ExchangeOperationTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void should_convert_using_rate() {
        ExchangeRate rate = new ExchangeRate(
                CurrencyPair.of(USD, EUR),
                new BigDecimal("0.920000"),
                RateSource.OVERRIDE,
                Instant.parse("2026-07-30T00:00:00Z"),
                false
        );
        Money euros = ExchangeOperation.convert(Money.of("100.00", USD), rate);
        assertThat(euros.currency()).isEqualTo(EUR);
        assertThat(euros.amount()).isEqualByComparingTo("92.00");
    }

    @Test
    void should_reject_currency_mismatch() {
        ExchangeRate rate = new ExchangeRate(
                CurrencyPair.of(USD, EUR),
                new BigDecimal("0.92"),
                RateSource.OVERRIDE,
                Instant.parse("2026-07-30T00:00:00Z"),
                false
        );
        assertThatThrownBy(() -> ExchangeOperation.convert(Money.of("10.00", EUR), rate))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void should_apply_spread_bps() {
        ExchangeRate mid = new ExchangeRate(
                CurrencyPair.of(USD, EUR),
                new BigDecimal("1.000000"),
                RateSource.OVERRIDE,
                Instant.parse("2026-07-30T00:00:00Z"),
                false
        );
        ExchangeRate withSpread = mid.withSpreadBps(100); // +1%
        assertThat(withSpread.rate()).isEqualByComparingTo("1.010000000000");
    }
}
