package com.pauluno.finledger.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;

/**
 * Optional external FX source (ECB, OpenExchangeRates, …). In-box default is a no-op.
 */
public interface ExternalRateClient {

    Optional<ExchangeRate> fetch(UUID tenantId, CurrencyPair pair, Instant asOf);
}
