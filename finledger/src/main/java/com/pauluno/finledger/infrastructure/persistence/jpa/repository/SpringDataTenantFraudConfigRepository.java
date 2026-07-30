package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantFraudConfigEntity;

public interface SpringDataTenantFraudConfigRepository extends JpaRepository<TenantFraudConfigEntity, UUID> {
}
