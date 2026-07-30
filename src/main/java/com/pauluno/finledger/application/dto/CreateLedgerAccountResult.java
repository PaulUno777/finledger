package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record CreateLedgerAccountResult(
        UUID accountId,
        UUID tenantId,
        String ownerRef,
        String currencyCode,
        String type,
        String status,
        boolean allowsOverdraft,
        String available,
        String pending,
        String held
) {
}
