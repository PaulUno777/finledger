package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record ConfigureFeeConfigCommand(
        UUID tenantId,
        String feeReversalPolicy
) {
}
