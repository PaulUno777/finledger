package com.pauluno.finledger.infrastructure.security.internal;

/**
 * Body {@code tenant_id} is not allowed for the persistent (client-bound) issuer.
 */
public final class TenantIdNotAllowedException extends RuntimeException {

    public TenantIdNotAllowedException(String message) {
        super(message);
    }
}
