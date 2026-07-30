package com.pauluno.finledger.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ConfigureTenantFraudCommand(
        UUID tenantId,
        boolean enabled,
        String failMode,
        BigDecimal maxAmount,
        int velocityMax,
        int velocityWindowSeconds,
        UUID holdAccountId,
        List<String> denylistOwnerRefs
) {
}
