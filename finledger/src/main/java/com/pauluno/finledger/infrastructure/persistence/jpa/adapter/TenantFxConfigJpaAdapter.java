package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.util.Arrays;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.TenantFxConfigRepository;
import com.pauluno.finledger.domain.model.TenantFxConfig;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantFxConfigEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataTenantFxConfigRepository;

@Component
public class TenantFxConfigJpaAdapter implements TenantFxConfigRepository {

    private final SpringDataTenantFxConfigRepository repository;

    public TenantFxConfigJpaAdapter(SpringDataTenantFxConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public TenantFxConfig save(TenantFxConfig config) {
        TenantFxConfigEntity entity = repository.findById(config.tenantId()).orElseGet(TenantFxConfigEntity::new);
        entity.setTenantId(config.tenantId());
        entity.setPivotCurrency(config.pivotCurrency().getCurrencyCode());
        entity.setSpreadBps(config.spreadBps());
        entity.setSupportedCurrencies(config.supportedCurrencies().stream()
                .map(Currency::getCurrencyCode)
                .sorted()
                .collect(Collectors.joining(",")));
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantFxConfig> findByTenantId(UUID tenantId) {
        return repository.findById(tenantId).map(TenantFxConfigJpaAdapter::toDomain);
    }

    private static TenantFxConfig toDomain(TenantFxConfigEntity entity) {
        Set<Currency> supported = Arrays.stream(entity.getSupportedCurrencies().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Currency::getInstance)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new TenantFxConfig(
                entity.getTenantId(),
                Currency.getInstance(entity.getPivotCurrency()),
                entity.getSpreadBps(),
                supported
        );
    }
}
