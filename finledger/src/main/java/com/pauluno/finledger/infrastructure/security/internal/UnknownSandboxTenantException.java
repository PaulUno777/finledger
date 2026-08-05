package com.pauluno.finledger.infrastructure.security.internal;

/**
 * Sandbox mint requested a {@code tenant_id} that is not present in the database.
 */
public final class UnknownSandboxTenantException extends RuntimeException {

    public UnknownSandboxTenantException(String message) {
        super(message);
    }
}
