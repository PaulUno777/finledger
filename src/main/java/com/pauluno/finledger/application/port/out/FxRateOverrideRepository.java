package com.pauluno.finledger.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;

public interface FxRateOverrideRepository {

    void saveOverride(
            UUID tenantId,
            CurrencyPair pair,
            BigDecimal rate,
            Instant validFrom,
            Instant validTo
    );

    Optional<ExchangeRate> findActive(UUID tenantId, CurrencyPair pair, Instant asOf);
}
