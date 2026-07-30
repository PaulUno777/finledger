package com.pauluno.finledger.infrastructure.exchangerate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.ExchangeRateProvider;
import com.pauluno.finledger.application.port.out.ExternalRateClient;
import com.pauluno.finledger.application.port.out.FxRateOverrideRepository;
import com.pauluno.finledger.application.port.out.RateCache;
import com.pauluno.finledger.application.port.out.TenantFxConfigRepository;
import com.pauluno.finledger.domain.exception.ExchangeRateNotFoundException;
import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;
import com.pauluno.finledger.domain.model.RateSource;
import com.pauluno.finledger.domain.model.TenantFxConfig;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Chain: DB override → external (circuit-breaker) → stale cache fallback.
 * Applies tenant spread on the resolved mid rate.
 */
@Component
public class CompositeExchangeRateProvider implements ExchangeRateProvider {

    private final FxRateOverrideRepository overrideRepository;
    private final ExternalRateClient externalRateClient;
    private final RateCache rateCache;
    private final TenantFxConfigRepository fxConfigRepository;
    private final CircuitBreaker circuitBreaker;

    public CompositeExchangeRateProvider(
            FxRateOverrideRepository overrideRepository,
            ExternalRateClient externalRateClient,
            RateCache rateCache,
            TenantFxConfigRepository fxConfigRepository
    ) {
        this.overrideRepository = overrideRepository;
        this.externalRateClient = externalRateClient;
        this.rateCache = rateCache;
        this.fxConfigRepository = fxConfigRepository;
        this.circuitBreaker = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .build()
        ).circuitBreaker("externalFx");
    }

    @Override
    public ExchangeRate getRate(UUID tenantId, CurrencyPair pair, Instant asOf) {
        TenantFxConfig config = fxConfigRepository.findByTenantId(tenantId).orElse(null);
        if (config != null) {
            if (!config.supports(pair.base()) || !config.supports(pair.quote())) {
                throw new ExchangeRateNotFoundException(
                        "Currency pair " + pair + " not supported for tenant " + tenantId);
            }
        }

        ExchangeRate resolved = resolveRaw(tenantId, pair, asOf)
                .or(() -> resolveRaw(tenantId, pair.inverse(), asOf).map(ExchangeRate::inverse))
                .orElseThrow(() -> new ExchangeRateNotFoundException(
                        "No FX rate for " + pair + " at " + asOf + " (tenant " + tenantId + ")"));

        int spread = config == null ? 0 : config.spreadBps();
        ExchangeRate withSpread = resolved.withSpreadBps(spread);
        if (withSpread.source() != RateSource.FALLBACK) {
            rateCache.put(tenantId, stripSpreadForCache(resolved));
        }
        return withSpread;
    }

    private static ExchangeRate stripSpreadForCache(ExchangeRate mid) {
        return mid;
    }

    private Optional<ExchangeRate> resolveRaw(UUID tenantId, CurrencyPair pair, Instant asOf) {
        Optional<ExchangeRate> override = overrideRepository.findActive(tenantId, pair, asOf);
        if (override.isPresent()) {
            return override;
        }

        Optional<ExchangeRate> external = fetchExternal(tenantId, pair, asOf);
        if (external.isPresent()) {
            return external;
        }

        return rateCache.get(tenantId, pair, asOf)
                .map(cached -> new ExchangeRate(
                        cached.pair(),
                        cached.rate(),
                        RateSource.FALLBACK,
                        cached.asOf(),
                        true
                ));
    }

    private Optional<ExchangeRate> fetchExternal(UUID tenantId, CurrencyPair pair, Instant asOf) {
        try {
            return circuitBreaker.executeSupplier(
                    () -> externalRateClient.fetch(tenantId, pair, asOf));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
