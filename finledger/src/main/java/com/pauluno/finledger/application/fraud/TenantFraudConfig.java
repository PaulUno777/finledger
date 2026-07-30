package com.pauluno.finledger.application.fraud;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TenantFraudConfig(
        UUID tenantId,
        boolean enabled,
        FraudFailMode failMode,
        BigDecimal maxAmount,
        int velocityMax,
        int velocityWindowSeconds,
        UUID holdAccountId,
        List<String> denylistOwnerRefs
) {
    public TenantFraudConfig {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(failMode, "failMode");
        denylistOwnerRefs = denylistOwnerRefs == null ? List.of() : List.copyOf(denylistOwnerRefs);
        if (velocityMax < 0) {
            throw new IllegalArgumentException("velocityMax must be >= 0");
        }
        if (velocityWindowSeconds <= 0) {
            throw new IllegalArgumentException("velocityWindowSeconds must be > 0");
        }
    }

    public static TenantFraudConfig defaults(UUID tenantId) {
        return new TenantFraudConfig(
                tenantId,
                false,
                FraudFailMode.OPEN,
                null,
                0,
                3600,
                null,
                List.of()
        );
    }
}
