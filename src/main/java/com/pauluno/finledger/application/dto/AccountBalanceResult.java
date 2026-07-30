package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record AccountBalanceResult(
        UUID accountId,
        UUID tenantId,
        String currencyCode,
        String accountType,
        String available,
        String pending,
        String held
) {
}
