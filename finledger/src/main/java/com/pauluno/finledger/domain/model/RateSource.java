package com.pauluno.finledger.domain.model;

/**
 * Where an {@link ExchangeRate} came from (frozen onto the journal entry).
 */
public enum RateSource {
    OVERRIDE,
    EXTERNAL,
    FALLBACK
}
