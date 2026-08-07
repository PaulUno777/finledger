package com.pauluno.finledger.infrastructure.security.internal;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * In-box JWT mint + JWKS + decoder (sandbox ephemeral or normal persistent).
 */
public interface InternalJwtIssuer {

    record AccessToken(String value, Duration ttl, String scope) {
    }

    String issuer();

    Duration maxTokenTtl();

    JwtDecoder jwtDecoder();

    Map<String, Object> jwks();

    /**
     * Mints a short-lived access token. Lifetime is capped by {@link #maxTokenTtl()}.
     *
     * @param tenantIdOrNull sandbox only: optional override (null → default seed tenant).
     *                       Persistent issuer must receive {@code null} or throw
     *                       {@link TenantIdNotAllowedException}.
     * @throws InvalidClientCredentialsException when client_id / client_secret do not match
     * @throws TenantIdNotAllowedException when persistent issuer is given a tenant_id
     */
    AccessToken mintAccessToken(String clientId, String clientSecret, UUID tenantIdOrNull);
}
