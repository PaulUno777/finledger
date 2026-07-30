package com.pauluno.finledger.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PutFxRateOverrideCommand(
        UUID tenantId,
        String baseCurrencyCode,
        String quoteCurrencyCode,
        BigDecimal rate,
        Instant validFrom,
        Instant validTo
) {
}
