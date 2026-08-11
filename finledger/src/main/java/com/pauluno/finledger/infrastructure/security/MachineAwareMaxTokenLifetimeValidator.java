package com.pauluno.finledger.infrastructure.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * User / pass-through tokens use {@code max-token-ttl}; machine tokens
 * ({@code token_use=machine} or allowlisted {@code azp}) use the machine cap.
 */
public final class MachineAwareMaxTokenLifetimeValidator implements OAuth2TokenValidator<Jwt> {

    private final Duration userTtl;
    private final Duration machineTtl;
    private final Set<String> azpAllowlist;

    public MachineAwareMaxTokenLifetimeValidator(
            Duration userTtl,
            Duration machineTtl,
            Collection<String> azpAllowlist
    ) {
        this.userTtl = userTtl == null || userTtl.isZero() || userTtl.isNegative()
                ? Duration.ofMinutes(15) : userTtl;
        this.machineTtl = machineTtl == null || machineTtl.isZero() || machineTtl.isNegative()
                ? Duration.ofHours(1) : machineTtl;
        this.azpAllowlist = new HashSet<>();
        if (azpAllowlist != null) {
            for (String azp : azpAllowlist) {
                if (azp != null && !azp.isBlank()) {
                    this.azpAllowlist.add(azp);
                }
            }
        }
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant iat = token.getIssuedAt();
        Instant exp = token.getExpiresAt();
        if (iat == null || exp == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "JWT must include iat and exp",
                    null));
        }
        Duration max = isMachine(token) ? machineTtl : userTtl;
        if (Duration.between(iat, exp).compareTo(max) > 0) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "JWT lifetime exceeds allowed max TTL (" + max + ")",
                    null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    boolean isMachine(Jwt token) {
        String tokenUse = token.getClaimAsString("token_use");
        if (tokenUse != null && "machine".equalsIgnoreCase(tokenUse.trim())) {
            return true;
        }
        String azp = token.getClaimAsString("azp");
        return azp != null && azpAllowlist.contains(azp);
    }
}
