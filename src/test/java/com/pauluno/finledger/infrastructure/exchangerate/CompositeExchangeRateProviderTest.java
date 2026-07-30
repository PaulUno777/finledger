package com.pauluno.finledger.infrastructure.exchangerate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.port.out.ExternalRateClient;
import com.pauluno.finledger.application.port.out.FxRateOverrideRepository;
import com.pauluno.finledger.application.port.out.RateCache;
import com.pauluno.finledger.application.port.out.TenantFxConfigRepository;
import com.pauluno.finledger.domain.exception.ExchangeRateNotFoundException;
import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;
import com.pauluno.finledger.domain.model.RateSource;
import com.pauluno.finledger.domain.model.TenantFxConfig;

@Tag("unit")
class CompositeExchangeRateProviderTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    private InMemoryOverrides overrides;
    private InMemoryRateCache cache;
    private InMemoryFxConfig configs;
    private RecordingExternalClient external;
    private CompositeExchangeRateProvider provider;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        overrides = new InMemoryOverrides();
        cache = new InMemoryRateCache();
        configs = new InMemoryFxConfig();
        external = new RecordingExternalClient();
        provider = new CompositeExchangeRateProvider(overrides, external, cache, configs);
        tenantId = UUID.randomUUID();
        configs.save(new TenantFxConfig(tenantId, USD, 0, Set.of(USD, EUR)));
    }

    @Test
    void should_prefer_override_over_external() {
        Instant asOf = Instant.parse("2026-07-30T12:00:00Z");
        overrides.saveOverride(tenantId, CurrencyPair.of(USD, EUR), new BigDecimal("0.91"),
                asOf.minusSeconds(60), asOf.plusSeconds(3600));
        external.rate = new ExchangeRate(
                CurrencyPair.of(USD, EUR), new BigDecimal("0.99"), RateSource.EXTERNAL, asOf, false);

        ExchangeRate rate = provider.getRate(tenantId, CurrencyPair.of(USD, EUR), asOf);
        assertThat(rate.source()).isEqualTo(RateSource.OVERRIDE);
        assertThat(rate.rate()).isEqualByComparingTo("0.91");
        assertThat(external.calls).isZero();
    }

    @Test
    void should_fall_back_to_cache_when_external_empty() {
        Instant asOf = Instant.parse("2026-07-30T12:00:00Z");
        cache.put(tenantId, new ExchangeRate(
                CurrencyPair.of(USD, EUR), new BigDecimal("0.90"), RateSource.EXTERNAL, asOf.minusSeconds(10), false));

        ExchangeRate rate = provider.getRate(tenantId, CurrencyPair.of(USD, EUR), asOf);
        assertThat(rate.source()).isEqualTo(RateSource.FALLBACK);
        assertThat(rate.stale()).isTrue();
        assertThat(rate.rate()).isEqualByComparingTo("0.90");
    }

    @Test
    void should_fail_when_no_source() {
        assertThatThrownBy(() -> provider.getRate(
                tenantId, CurrencyPair.of(USD, EUR), Instant.parse("2026-07-30T12:00:00Z")))
                .isInstanceOf(ExchangeRateNotFoundException.class);
    }

    private static final class InMemoryOverrides implements FxRateOverrideRepository {
        private final Map<String, ExchangeRate> active = new ConcurrentHashMap<>();

        @Override
        public void saveOverride(UUID tenantId, CurrencyPair pair, BigDecimal rate, Instant validFrom, Instant validTo) {
            active.put(tenantId + ":" + pair, new ExchangeRate(pair, rate, RateSource.OVERRIDE, validFrom, false));
        }

        @Override
        public Optional<ExchangeRate> findActive(UUID tenantId, CurrencyPair pair, Instant asOf) {
            return Optional.ofNullable(active.get(tenantId + ":" + pair));
        }
    }

    private static final class InMemoryFxConfig implements TenantFxConfigRepository {
        private final Map<UUID, TenantFxConfig> byTenant = new ConcurrentHashMap<>();

        @Override
        public TenantFxConfig save(TenantFxConfig config) {
            byTenant.put(config.tenantId(), config);
            return config;
        }

        @Override
        public Optional<TenantFxConfig> findByTenantId(UUID tenantId) {
            return Optional.ofNullable(byTenant.get(tenantId));
        }
    }

    private static final class RecordingExternalClient implements ExternalRateClient {
        ExchangeRate rate;
        int calls;

        @Override
        public Optional<ExchangeRate> fetch(UUID tenantId, CurrencyPair pair, Instant asOf) {
            calls++;
            return Optional.ofNullable(rate);
        }
    }
}
