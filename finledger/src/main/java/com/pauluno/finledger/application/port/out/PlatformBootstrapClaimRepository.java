package com.pauluno.finledger.application.port.out;

import java.util.UUID;

/**
 * Singleton claim store for platform bootstrap (FL-158). One successful insert forever.
 */
public interface PlatformBootstrapClaimRepository {

    boolean isClaimed();

    /**
     * Atomically claim bootstrap. Returns {@code false} if already claimed (unique conflict).
     */
    boolean tryClaim(UUID jti, byte[] bootstrapSecretSha256);
}
