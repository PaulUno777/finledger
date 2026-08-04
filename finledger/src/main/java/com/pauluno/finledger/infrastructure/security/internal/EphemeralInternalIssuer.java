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
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pauluno.finledger.infrastructure.security.AlgorithmAllowlistingJwtDecoder;
import com.pauluno.finledger.infrastructure.security.TenantClaimAuthorizationFilter;
import com.pauluno.finledger.security.policy.SandboxIds;

/**
 * Ephemeral RSA issuer for sandbox (FL-155 / ADR-016). Keys regenerate each JVM boot.
 */
public final class EphemeralInternalIssuer {

    public static final String DEFAULT_ISSUER = "http://localhost:8080/internal/sandbox";
    public static final String DEFAULT_CLIENT_ID = "sandbox";
    public static final String SCOPE_CLAIM =
            "ledger:read ledger:write ledger:admin";

    private final RSAKey rsaKey;
    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final Duration maxTokenTtl;
    private final JwtDecoder jwtDecoder;

    private EphemeralInternalIssuer(
            RSAKey rsaKey,
            String issuer,
            String clientId,
            String clientSecret,
            Duration maxTokenTtl
    ) {
        this.rsaKey = rsaKey;
        this.issuer = issuer;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.maxTokenTtl = maxTokenTtl;
        this.jwtDecoder = buildDecoder(rsaKey, issuer, maxTokenTtl);
    }

    public static EphemeralInternalIssuer generate(
            String issuer,
            String clientId,
            String clientSecret,
            Duration maxTokenTtl
    ) {
        Objects.requireNonNull(clientSecret, "clientSecret");
        if (clientSecret.isBlank()) {
            throw new IllegalArgumentException("clientSecret must not be blank");
        }
        Duration ttl = maxTokenTtl == null || maxTokenTtl.isZero() || maxTokenTtl.isNegative()
                ? Duration.ofMinutes(15)
                : maxTokenTtl;
        try {
            RSAKey key = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
            return new EphemeralInternalIssuer(
                    key,
                    issuer == null || issuer.isBlank() ? DEFAULT_ISSUER : issuer,
                    clientId == null || clientId.isBlank() ? DEFAULT_CLIENT_ID : clientId,
                    clientSecret,
                    ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate sandbox RSA keypair", ex);
        }
    }

    public String issuer() {
        return issuer;
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public Duration maxTokenTtl() {
        return maxTokenTtl;
    }

    public JwtDecoder jwtDecoder() {
        return jwtDecoder;
    }

    public Map<String, Object> jwks() {
        try {
            Map<String, Object> jwk = rsaKey.toPublicJWK().toJSONObject();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("keys", List.of(jwk));
            return body;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to export JWKS", ex);
        }
    }

    /**
     * Mints a short-lived access token. Lifetime is min(requested, maxTokenTtl).
     */
    public String mintAccessToken(String requestedClientId, String requestedClientSecret) {
        if (!clientId.equals(requestedClientId) || !clientSecret.equals(requestedClientSecret)) {
            throw new InvalidClientCredentialsException("Invalid client_id or client_secret");
        }
        Instant now = Instant.now();
        Instant exp = now.plus(maxTokenTtl);
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(clientId)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .claim(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM, SandboxIds.TENANT_ID.toString())
                    .claim("scope", SCOPE_CLAIM)
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(rsaKey));
            return jwt.serialize();
        } catch (InvalidClientCredentialsException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to mint sandbox JWT", ex);
        }
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
            throw new IllegalStateException("Unable to build sandbox JwtDecoder", ex);
        }
    }

    /**
     * Rejects tokens whose lifetime (exp − iat) exceeds the ledger max TTL.
     */
    static final class MaxTokenLifetimeValidator implements OAuth2TokenValidator<Jwt> {

        private final Duration maxTtl;

        MaxTokenLifetimeValidator(Duration maxTtl) {
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

    public static final class InvalidClientCredentialsException extends RuntimeException {
        public InvalidClientCredentialsException(String message) {
            super(message);
        }
    }
}
