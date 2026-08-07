package com.pauluno.finledger.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.exception.PlatformBootstrapAlreadyClaimedException;
import com.pauluno.finledger.application.port.in.ClaimPlatformBootstrapUseCase;
import com.pauluno.finledger.application.port.out.PlatformBootstrapClaimRepository;

@Service
public class ClaimPlatformBootstrapService implements ClaimPlatformBootstrapUseCase {

    private final PlatformBootstrapClaimRepository claimRepository;

    public ClaimPlatformBootstrapService(PlatformBootstrapClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    @Override
    @Transactional
    public UUID claim(byte[] bootstrapSecretSha256) {
        if (claimRepository.isClaimed()) {
            throw new PlatformBootstrapAlreadyClaimedException();
        }
        UUID jti = UUID.randomUUID();
        if (!claimRepository.tryClaim(jti, bootstrapSecretSha256)) {
            throw new PlatformBootstrapAlreadyClaimedException();
        }
        return jti;
    }
}
