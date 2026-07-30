package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantFeeConfigEntity;

public interface SpringDataTenantFeeConfigRepository extends JpaRepository<TenantFeeConfigEntity, UUID> {
}
