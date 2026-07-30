package com.pauluno.finledger.application.dto;

import java.util.Currency;
import java.util.UUID;

public record CreateLedgerAccountCommand(
        UUID tenantId,
        String ownerRef,
        String currencyCode,
        String type,
        boolean allowsOverdraft
) {
    public Currency currency() {
        return Currency.getInstance(currencyCode);
    }
}
