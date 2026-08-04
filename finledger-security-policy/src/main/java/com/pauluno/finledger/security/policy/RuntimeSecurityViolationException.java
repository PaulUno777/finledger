package com.pauluno.finledger.security.policy;

/**
 * Thrown when a runtime security rule is violated (e.g. sandbox in production).
 */
public final class RuntimeSecurityViolationException extends IllegalStateException {

    public static final String CODE = "RUNTIME_SECURITY_VIOLATION";

    public RuntimeSecurityViolationException(String message) {
        super(message);
    }
}
