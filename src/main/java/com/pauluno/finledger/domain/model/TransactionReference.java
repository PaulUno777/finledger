package com.pauluno.finledger.domain.model;

import java.util.Objects;

/**
 * External business reference, distinct from the technical journal-entry UUID.
 */
public record TransactionReference(String value) {

    public TransactionReference {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Transaction reference must not be blank");
        }
    }
}
