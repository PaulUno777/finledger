package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.PlatformAdminCredentialEntity;

public interface SpringDataPlatformAdminCredentialRepository
        extends JpaRepository<PlatformAdminCredentialEntity, Short> {
}
