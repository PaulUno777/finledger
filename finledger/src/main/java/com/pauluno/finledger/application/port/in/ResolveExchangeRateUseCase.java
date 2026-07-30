package com.pauluno.finledger.application.port.in;

import java.time.Instant;
import java.util.UUID;

import com.pauluno.finledger.application.dto.ExchangeRateResult;

public interface ResolveExchangeRateUseCase {

    ExchangeRateResult execute(UUID tenantId, String baseCurrency, String quoteCurrency, Instant asOf);
}
