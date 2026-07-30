package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantSplitRuleSetEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantSplitRuleSetId;

public interface SpringDataTenantSplitRuleSetRepository
        extends JpaRepository<TenantSplitRuleSetEntity, TenantSplitRuleSetId> {
}
