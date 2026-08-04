package com.pauluno.finledger.infrastructure.security.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pauluno.finledger.infrastructure.security.AlgorithmAllowlistingJwtDecoder;
import com.pauluno.finledger.infrastructure.security.TenantClaimAuthorizationFilter;

/**
 * Shared RSA mint / JWKS / decoder helpers for in-box issuers.
 */
final class RsaInternalJwtSupport {

    private final RSAKey rsaKey;
    private final String issuer;
    private final Duration maxTokenTtl;
    private final JwtDecoder jwtDecoder;

    RsaInternalJwtSupport(RSAKey rsaKey, String issuer, Duration maxTokenTtl) {
        this.rsaKey = Objects.requireNonNull(rsaKey, "rsaKey");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.maxTokenTtl = normalizeTtl(maxTokenTtl);
        this.jwtDecoder = buildDecoder(rsaKey, this.issuer, this.maxTokenTtl);
    }

    static Duration normalizeTtl(Duration maxTokenTtl) {
        if (maxTokenTtl == null || maxTokenTtl.isZero() || maxTokenTtl.isNegative()) {
            return Duration.ofMinutes(15);
        }
        return maxTokenTtl;
    }

    String issuer() {
        return issuer;
    }

    Duration maxTokenTtl() {
        return maxTokenTtl;
    }

    JwtDecoder jwtDecoder() {
        return jwtDecoder;
    }

    Map<String, Object> jwks() {
        try {
            Map<String, Object> jwk = rsaKey.toPublicJWK().toJSONObject();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("keys", List.of(jwk));
            return body;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to export JWKS", ex);
        }
    }

    InternalJwtIssuer.AccessToken mint(InternalClientCredentials client) {
        Instant now = Instant.now();
        Instant exp = now.plus(maxTokenTtl);
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(client.clientId())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .claim(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM, client.tenantId().toString())
                    .claim("scope", client.scopes())
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(rsaKey));
            return new InternalJwtIssuer.AccessToken(jwt.serialize(), maxTokenTtl, client.scopes());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to mint internal JWT", ex);
        }
    }

    static RSAKey requireKeyId(RSAKey key) {
        if (key.getKeyID() == null || key.getKeyID().isBlank()) {
            return new RSAKey.Builder(key).keyID(UUID.randomUUID().toString()).build();
        }
        return key;
    }

    private static JwtDecoder buildDecoder(RSAKey rsaKey, String issuer, Duration maxTokenTtl) {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
            OAuth2TokenValidator<Jwt> maxTtl = new MaxTokenLifetimeValidator(maxTokenTtl);
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, maxTtl));
            return new AlgorithmAllowlistingJwtDecoder(decoder);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build internal JwtDecoder", ex);
        }
    }
}
