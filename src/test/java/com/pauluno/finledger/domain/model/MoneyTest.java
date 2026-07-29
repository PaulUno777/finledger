package com.pauluno.finledger.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.domain.exception.CurrencyMismatchException;

@Tag("unit")
class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void should_scale_to_iso_fraction_digits_with_half_even() {
        Money money = Money.of(new BigDecimal("10.005"), USD);
        assertEquals(new BigDecimal("10.00"), money.amount());
        assertEquals(2, money.amount().scale());
    }

    @Test
    void should_add_same_currency() {
        Money sum = Money.of("10.00", USD).plus(Money.of("2.50", USD));
        assertEquals(new BigDecimal("12.50"), sum.amount());
    }

    @Test
    void should_subtract_same_currency() {
        Money result = Money.of("10.00", USD).minus(Money.of("3.00", USD));
        assertEquals(new BigDecimal("7.00"), result.amount());
    }

    @Test
    void should_reject_cross_currency_arithmetic() {
        assertThrows(
                CurrencyMismatchException.class,
                () -> Money.of("1.00", USD).plus(Money.of("1.00", EUR))
        );
    }

    @Test
    void should_negate() {
        Money negated = Money.of("5.00", USD).negated();
        assertTrue(negated.isNegative());
        assertEquals(new BigDecimal("-5.00"), negated.amount());
    }
}
