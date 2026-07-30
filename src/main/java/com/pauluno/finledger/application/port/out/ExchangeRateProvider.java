package com.pauluno.finledger.application.port.out;

import java.time.Instant;
import java.util.UUID;

import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;

/**
 * Resolves an FX rate for a tenant (plan §4.1). Implementations form a chain:
 * override → optional external → fallback cache.
 */
public interface ExchangeRateProvider {

    ExchangeRate getRate(UUID tenantId, CurrencyPair pair, Instant asOf);
}
