package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantEntity;

public interface SpringDataTenantRepository extends JpaRepository<TenantEntity, UUID> {
}
