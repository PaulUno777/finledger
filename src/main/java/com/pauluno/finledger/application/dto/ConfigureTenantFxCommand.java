package com.pauluno.finledger.application.dto;

import java.util.List;
import java.util.UUID;

public record ConfigureTenantFxCommand(
        UUID tenantId,
        String pivotCurrencyCode,
        int spreadBps,
        List<String> supportedCurrencyCodes
) {
}
