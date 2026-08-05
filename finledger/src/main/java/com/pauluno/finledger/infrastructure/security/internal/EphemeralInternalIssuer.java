package com.pauluno.finledger.infrastructure.security.internal;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.pauluno.finledger.security.policy.SandboxIds;

/**
 * Ephemeral RSA issuer for sandbox (FL-155 / ADR-016). Keys regenerate each JVM boot.
 * Tenant claim is always {@link SandboxIds#TENANT_ID}.
 */
public final class EphemeralInternalIssuer implements InternalJwtIssuer {

    public static final String DEFAULT_ISSUER = "http://localhost:8080/internal/sandbox";
    public static final String DEFAULT_CLIENT_ID = "sandbox";
    public static final String SCOPE_CLAIM = InternalClientCredentials.DEFAULT_SCOPES;

    private final RsaInternalJwtSupport support;
    private final InternalClientCredentials client;

    private EphemeralInternalIssuer(RsaInternalJwtSupport support, InternalClientCredentials client) {
        this.support = support;
        this.client = client;
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
        try {
            RSAKey key = new RSAKeyGenerator(2048)
                    .keyID(java.util.UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
            String resolvedIssuer = issuer == null || issuer.isBlank() ? DEFAULT_ISSUER : issuer;
            String resolvedClientId = clientId == null || clientId.isBlank() ? DEFAULT_CLIENT_ID : clientId;
            InternalClientCredentials credentials = new InternalClientCredentials(
                    resolvedClientId,
                    clientSecret,
                    SandboxIds.TENANT_ID,
                    InternalClientCredentials.DEFAULT_SCOPES);
            return new EphemeralInternalIssuer(
                    new RsaInternalJwtSupport(key, resolvedIssuer, maxTokenTtl),
                    credentials);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate sandbox RSA keypair", ex);
        }
    }

    public String clientId() {
        return client.clientId();
    }

    public String clientSecret() {
        return client.clientSecret();
    }

    @Override
    public String issuer() {
        return support.issuer();
    }

    @Override
    public Duration maxTokenTtl() {
        return support.maxTokenTtl();
    }

    @Override
    public JwtDecoder jwtDecoder() {
        return support.jwtDecoder();
    }

    @Override
    public Map<String, Object> jwks() {
        return support.jwks();
    }

    @Override
    public AccessToken mintAccessToken(String requestedClientId, String requestedClientSecret) {
        if (!client.clientId().equals(requestedClientId)
                || !client.clientSecret().equals(requestedClientSecret)) {
            throw new InvalidClientCredentialsException("Invalid client_id or client_secret");
        }
        return support.mint(client);
    }
}
