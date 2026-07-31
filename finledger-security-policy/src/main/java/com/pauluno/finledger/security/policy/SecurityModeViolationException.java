package com.pauluno.finledger.security.policy;

/**
 * Thrown when a security mode is forbidden for the current environment.
 */
public final class SecurityModeViolationException extends IllegalStateException {

    public static final String CODE = "SECURITY_MODE_FORBIDDEN_IN_PRODUCTION";

    public SecurityModeViolationException(String message) {
        super(message);
    }
}
