package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.fraud.FraudFailMode;
import com.pauluno.finledger.application.fraud.TenantFraudConfig;
import com.pauluno.finledger.application.port.out.TenantFraudConfigRepository;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantFraudConfigEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataTenantFraudConfigRepository;

@Component
public class TenantFraudConfigJpaAdapter implements TenantFraudConfigRepository {

    private final SpringDataTenantFraudConfigRepository repository;

    public TenantFraudConfigJpaAdapter(SpringDataTenantFraudConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public TenantFraudConfig save(TenantFraudConfig config) {
        TenantFraudConfigEntity entity = repository.findById(config.tenantId()).orElseGet(TenantFraudConfigEntity::new);
        entity.setTenantId(config.tenantId());
        entity.setEnabled(config.enabled());
        entity.setFailMode(config.failMode().name());
        entity.setMaxAmount(config.maxAmount());
        entity.setVelocityMax(config.velocityMax());
        entity.setVelocityWindowSeconds(config.velocityWindowSeconds());
        entity.setHoldAccountId(config.holdAccountId());
        entity.setDenylistOwnerRefs(String.join(",", config.denylistOwnerRefs()));
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantFraudConfig> findByTenantId(UUID tenantId) {
        return repository.findById(tenantId).map(TenantFraudConfigJpaAdapter::toDomain);
    }

    private static TenantFraudConfig toDomain(TenantFraudConfigEntity entity) {
        List<String> denylist = Arrays.stream(entity.getDenylistOwnerRefs().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return new TenantFraudConfig(
                entity.getTenantId(),
                entity.isEnabled(),
                FraudFailMode.valueOf(entity.getFailMode()),
                entity.getMaxAmount(),
                entity.getVelocityMax(),
                entity.getVelocityWindowSeconds(),
                entity.getHoldAccountId(),
                denylist
        );
    }
}
