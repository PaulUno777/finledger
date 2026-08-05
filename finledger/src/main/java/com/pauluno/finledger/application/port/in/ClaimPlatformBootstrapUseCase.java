package com.pauluno.finledger.application.port.in;

import java.util.UUID;

/**
 * One-shot platform bootstrap claim (FL-158). Caller verifies the env secret first.
 */
public interface ClaimPlatformBootstrapUseCase {

    /**
     * @return jti of the claim (also used as JWT {@code jti})
     * @throws com.pauluno.finledger.application.exception.PlatformBootstrapAlreadyClaimedException if claimed
     */
    UUID claim(byte[] bootstrapSecretSha256);
}
