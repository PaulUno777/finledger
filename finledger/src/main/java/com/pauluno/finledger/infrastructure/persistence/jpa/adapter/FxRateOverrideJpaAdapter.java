package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.FxRateOverrideRepository;
import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;
import com.pauluno.finledger.domain.model.RateSource;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.FxRateOverrideEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataFxRateOverrideRepository;

@Component
public class FxRateOverrideJpaAdapter implements FxRateOverrideRepository {

    private final SpringDataFxRateOverrideRepository repository;

    public FxRateOverrideJpaAdapter(SpringDataFxRateOverrideRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void saveOverride(
            UUID tenantId,
            CurrencyPair pair,
            BigDecimal rate,
            Instant validFrom,
            Instant validTo
    ) {
        FxRateOverrideEntity entity = new FxRateOverrideEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setBaseCurrency(pair.base().getCurrencyCode());
        entity.setQuoteCurrency(pair.quote().getCurrencyCode());
        entity.setRate(rate);
        entity.setValidFrom(validFrom);
        entity.setValidTo(validTo);
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExchangeRate> findActive(UUID tenantId, CurrencyPair pair, Instant asOf) {
        return repository.findActive(
                        tenantId,
                        pair.base().getCurrencyCode(),
                        pair.quote().getCurrencyCode(),
                        asOf
                ).stream()
                .findFirst()
                .map(entity -> new ExchangeRate(
                        CurrencyPair.of(
                                Currency.getInstance(entity.getBaseCurrency()),
                                Currency.getInstance(entity.getQuoteCurrency())),
                        entity.getRate(),
                        RateSource.OVERRIDE,
                        asOf,
                        false
                ));
    }
}
