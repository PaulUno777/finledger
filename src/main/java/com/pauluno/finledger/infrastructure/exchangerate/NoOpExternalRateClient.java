package com.pauluno.finledger.infrastructure.exchangerate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.ExternalRateClient;
import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;

/**
 * Default external client: no vendor wired (non-presumption). Returns empty so the
 * composite falls through to cache / fails closed when no override exists.
 */
@Component
@ConditionalOnProperty(name = "finledger.fx.external-enabled", havingValue = "false", matchIfMissing = true)
public class NoOpExternalRateClient implements ExternalRateClient {

    @Override
    public Optional<ExchangeRate> fetch(UUID tenantId, CurrencyPair pair, Instant asOf) {
        return Optional.empty();
    }
}
