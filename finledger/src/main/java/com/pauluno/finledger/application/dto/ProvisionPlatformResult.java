package com.pauluno.finledger.application.dto;

import java.util.List;
import java.util.UUID;

public record ProvisionPlatformResult(
        UUID tenantId,
        String tenantType,
        String name,
        String recipe,
        boolean replayed,
        String feeReversalPolicy,
        List<LedgerAccountResult> accounts
) {
}
