package com.pauluno.finledger.infrastructure.security.internal;

import java.util.Objects;
import java.util.UUID;

/**
 * Machine client bound to a single tenant (FL-156 / ADR-016).
 */
public record InternalClientCredentials(
        String clientId,
        String clientSecret,
        UUID tenantId,
        String scopes
) {
    public static final String DEFAULT_SCOPES = "ledger:read ledger:write ledger:admin";

    public InternalClientCredentials {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientSecret, "clientSecret");
        Objects.requireNonNull(tenantId, "tenantId");
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        if (clientSecret.isBlank()) {
            throw new IllegalArgumentException("clientSecret must not be blank");
        }
        scopes = (scopes == null || scopes.isBlank()) ? DEFAULT_SCOPES : scopes.trim();
    }
}
