package com.pauluno.finledger.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;

/**
 * Last-known rate cache used by the fallback provider (plan §4.1 / §2.3 RateCache).
 */
public interface RateCache {

    void put(UUID tenantId, ExchangeRate rate);

    Optional<ExchangeRate> get(UUID tenantId, CurrencyPair pair, Instant asOf);
}
