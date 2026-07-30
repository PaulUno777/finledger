package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.TenantFeeConfigRepository;
import com.pauluno.finledger.domain.model.FeeReversalPolicyType;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantFeeConfigEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataTenantFeeConfigRepository;

@Component
public class TenantFeeConfigJpaAdapter implements TenantFeeConfigRepository {

    private final SpringDataTenantFeeConfigRepository repository;

    public TenantFeeConfigJpaAdapter(SpringDataTenantFeeConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public FeeReversalPolicyType save(UUID tenantId, FeeReversalPolicyType policy) {
        TenantFeeConfigEntity entity = repository.findById(tenantId).orElseGet(TenantFeeConfigEntity::new);
        entity.setTenantId(tenantId);
        entity.setFeeReversalPolicy(policy.name());
        return FeeReversalPolicyType.valueOf(repository.save(entity).getFeeReversalPolicy());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FeeReversalPolicyType> findByTenantId(UUID tenantId) {
        return repository.findById(tenantId)
                .map(e -> FeeReversalPolicyType.valueOf(e.getFeeReversalPolicy()));
    }
}
