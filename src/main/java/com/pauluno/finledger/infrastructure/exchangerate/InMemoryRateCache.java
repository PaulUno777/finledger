package com.pauluno.finledger.infrastructure.exchangerate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.RateCache;
import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;

/**
 * In-box {@link RateCache} (plan §2.3 default: memory). ConcurrentHashMap is enough for
 * single-node fallback; Redis adapter can replace later.
 */
@Component
public class InMemoryRateCache implements RateCache {

    private final ConcurrentHashMap<String, ExchangeRate> rates = new ConcurrentHashMap<>();

    @Override
    public void put(UUID tenantId, ExchangeRate rate) {
        rates.put(key(tenantId, rate.pair()), rate);
        rates.put(key(tenantId, rate.pair().inverse()), rate.inverse());
    }

    @Override
    public Optional<ExchangeRate> get(UUID tenantId, CurrencyPair pair, Instant asOf) {
        ExchangeRate cached = rates.get(key(tenantId, pair));
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.of(cached);
    }

    private static String key(UUID tenantId, CurrencyPair pair) {
        return tenantId + ":" + pair.base().getCurrencyCode() + "/" + pair.quote().getCurrencyCode();
    }
}
