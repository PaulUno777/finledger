package com.pauluno.finledger.domain.model;

import java.util.Objects;

/**
 * Typed idempotency key — never a raw String at domain boundaries.
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
    }
}
