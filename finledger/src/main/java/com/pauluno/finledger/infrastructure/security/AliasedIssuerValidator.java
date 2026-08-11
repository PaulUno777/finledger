package com.pauluno.finledger.infrastructure.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Accepts {@code iss} equal to the configured issuer or an explicit alias
 * (e.g. {@code http://zitadel} vs {@code http://zitadel:8080}).
 */
public final class AliasedIssuerValidator implements OAuth2TokenValidator<Jwt> {

    private final Set<String> acceptedIssuers;

    public AliasedIssuerValidator(String primaryIssuer, Collection<String> aliases) {
        this.acceptedIssuers = new LinkedHashSet<>();
        if (primaryIssuer != null && !primaryIssuer.isBlank()) {
            acceptedIssuers.add(primaryIssuer);
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    acceptedIssuers.add(alias);
                }
            }
        }
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String iss = token.getIssuer() == null ? token.getClaimAsString("iss") : token.getIssuer().toString();
        if (iss != null && acceptedIssuers.contains(iss)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "JWT iss is not an accepted issuer (configure issuer-uri / issuer-aliases to match the token)",
                null));
    }
}
