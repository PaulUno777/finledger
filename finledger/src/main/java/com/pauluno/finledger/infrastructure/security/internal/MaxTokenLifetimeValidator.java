package com.pauluno.finledger.infrastructure.security.internal;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects tokens whose lifetime ({@code exp − iat}) exceeds the ledger max TTL.
 */
public final class MaxTokenLifetimeValidator implements OAuth2TokenValidator<Jwt> {

    private final Duration maxTtl;

    public MaxTokenLifetimeValidator(Duration maxTtl) {
        this.maxTtl = maxTtl;
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
        if (Duration.between(iat, exp).compareTo(maxTtl) > 0) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "JWT lifetime exceeds finledger.security.max-token-ttl (" + maxTtl + ")",
                    null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
