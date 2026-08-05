package com.pauluno.finledger.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.exception.PlatformBootstrapAlreadyClaimedException;
import com.pauluno.finledger.application.port.out.PlatformBootstrapClaimRepository;

@Tag("unit")
class ClaimPlatformBootstrapServiceTest {

    @Test
    void should_claim_once() {
        AtomicBoolean claimed = new AtomicBoolean(false);
        PlatformBootstrapClaimRepository repo = new PlatformBootstrapClaimRepository() {
            @Override
            public boolean isClaimed() {
                return claimed.get();
            }

            @Override
            public boolean tryClaim(UUID jti, byte[] bootstrapSecretSha256) {
                return claimed.compareAndSet(false, true);
            }
        };
        ClaimPlatformBootstrapService service = new ClaimPlatformBootstrapService(repo);
        UUID jti = service.claim(new byte[] {1, 2, 3});
        assertThat(jti).isNotNull();
        assertThatThrownBy(() -> service.claim(new byte[] {1, 2, 3}))
                .isInstanceOf(PlatformBootstrapAlreadyClaimedException.class);
    }
}
