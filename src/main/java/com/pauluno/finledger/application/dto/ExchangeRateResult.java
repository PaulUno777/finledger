package com.pauluno.finledger.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRateResult(
        String baseCurrencyCode,
        String quoteCurrencyCode,
        BigDecimal rate,
        String source,
        Instant asOf,
        boolean stale
) {
}
