package com.pauluno.finledger.infrastructure.security.internal;

/**
 * Unknown or mismatched client_id / client_secret for the in-box issuer.
 */
public final class InvalidClientCredentialsException extends RuntimeException {

    public InvalidClientCredentialsException(String message) {
        super(message);
    }
}
