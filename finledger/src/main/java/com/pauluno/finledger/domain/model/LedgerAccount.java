package com.pauluno.finledger.domain.model;

import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Ledger account — no mutable balance field; balance is always a projection
 * of {@link Posting} sums ({@link AccountBalance}).
 */
public record LedgerAccount(
        UUID id,
        UUID tenantId,
        String ownerRef,
        Currency currency,
        AccountType type,
        AccountStatus status,
        boolean allowsOverdraft
) {

    public LedgerAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ownerRef, "ownerRef");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        if (ownerRef.isBlank()) {
            throw new IllegalArgumentException("ownerRef must not be blank");
        }
    }

    public boolean isOpen() {
        return status == AccountStatus.OPEN;
    }

    public boolean isHoldAccount() {
        return type == AccountType.SUSPENSE_HOLD || type == AccountType.RESERVE_HOLD;
    }
}
