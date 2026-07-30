package com.pauluno.finledger.domain.model;

import java.util.Currency;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Per-tenant FX settings (plan §4.2).
 */
public record TenantFxConfig(
        UUID tenantId,
        Currency pivotCurrency,
        int spreadBps,
        Set<Currency> supportedCurrencies
) {
    public TenantFxConfig {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(pivotCurrency, "pivotCurrency");
        Objects.requireNonNull(supportedCurrencies, "supportedCurrencies");
        if (spreadBps < 0) {
            throw new IllegalArgumentException("spreadBps must be >= 0");
        }
        supportedCurrencies = Set.copyOf(supportedCurrencies);
        if (supportedCurrencies.isEmpty()) {
            throw new IllegalArgumentException("supportedCurrencies must not be empty");
        }
        if (!supportedCurrencies.contains(pivotCurrency)) {
            throw new IllegalArgumentException("pivotCurrency must be in supportedCurrencies");
        }
    }

    public boolean supports(Currency currency) {
        return supportedCurrencies.contains(currency);
    }
}
