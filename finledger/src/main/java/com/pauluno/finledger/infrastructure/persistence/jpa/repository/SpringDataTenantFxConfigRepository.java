package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantFxConfigEntity;

public interface SpringDataTenantFxConfigRepository extends JpaRepository<TenantFxConfigEntity, UUID> {
}
