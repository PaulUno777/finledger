package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.PlatformBootstrapClaimRepository;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.PlatformAdminCredentialEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataPlatformAdminCredentialRepository;

@Component
public class PlatformBootstrapClaimJpaAdapter implements PlatformBootstrapClaimRepository {

    private static final short SINGLETON_ID = 1;

    private final SpringDataPlatformAdminCredentialRepository repository;

    public PlatformBootstrapClaimJpaAdapter(SpringDataPlatformAdminCredentialRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isClaimed() {
        return repository.existsById(SINGLETON_ID);
    }

    @Override
    public boolean tryClaim(UUID jti, byte[] bootstrapSecretSha256) {
        if (repository.existsById(SINGLETON_ID)) {
            return false;
        }
        PlatformAdminCredentialEntity entity = new PlatformAdminCredentialEntity();
        entity.setId(SINGLETON_ID);
        entity.setClaimedAt(Instant.now());
        entity.setJti(jti);
        entity.setBootstrapSecretSha256(bootstrapSecretSha256);
        try {
            repository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
