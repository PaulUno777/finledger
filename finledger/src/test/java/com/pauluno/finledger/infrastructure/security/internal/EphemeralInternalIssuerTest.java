package com.pauluno.finledger.infrastructure.security.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pauluno.finledger.infrastructure.security.TenantClaimAuthorizationFilter;
import com.pauluno.finledger.security.policy.SandboxIds;

@Tag("unit")
class EphemeralInternalIssuerTest {

    private EphemeralInternalIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = EphemeralInternalIssuer.generate(
                "http://localhost:8080/internal/sandbox",
                "sandbox",
                "test-secret",
                Duration.ofMinutes(15));
    }

    @Test
    void should_mint_and_decode_token_with_tenant_and_scopes() {
        String token = issuer.mintAccessToken("sandbox", "test-secret");
        Jwt jwt = issuer.jwtDecoder().decode(token);

        assertThat(jwt.getIssuer().toString()).isEqualTo(issuer.issuer());
        assertThat(jwt.getClaimAsString(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM))
                .isEqualTo(SandboxIds.TENANT_ID.toString());
        assertThat(jwt.getClaimAsString("scope")).contains("ledger:write");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isLessThanOrEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void should_reject_invalid_client_secret() {
        assertThatThrownBy(() -> issuer.mintAccessToken("sandbox", "wrong"))
                .isInstanceOf(EphemeralInternalIssuer.InvalidClientCredentialsException.class);
    }

    @Test
    void should_export_jwks_with_public_key() {
        Map<String, Object> jwks = issuer.jwks();
        assertThat(jwks).containsKey("keys");
        @SuppressWarnings("unchecked")
        var keys = (java.util.List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst()).containsKeys("kty", "kid", "n", "e");
    }

    @Test
    void should_reject_token_whose_lifetime_exceeds_max_ttl() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("overlong").algorithm(JWSAlgorithm.RS256).generate();
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer.issuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(2))))
                .claim(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM, SandboxIds.TENANT_ID.toString())
                .claim("scope", EphemeralInternalIssuer.SCOPE_CLAIM)
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(key));

        EphemeralInternalIssuer.MaxTokenLifetimeValidator validator =
                new EphemeralInternalIssuer.MaxTokenLifetimeValidator(Duration.ofMinutes(15));
        Jwt springJwt = Jwt.withTokenValue(jwt.serialize())
                .header("alg", "RS256")
                .issuer(issuer.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(2)))
                .claim(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM, SandboxIds.TENANT_ID.toString())
                .claim("scope", EphemeralInternalIssuer.SCOPE_CLAIM)
                .build();

        OAuth2TokenValidatorResult result = validator.validate(springJwt);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void should_reject_blank_client_secret_at_generate() {
        assertThatThrownBy(() -> EphemeralInternalIssuer.generate(
                        EphemeralInternalIssuer.DEFAULT_ISSUER, "sandbox", "  ", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_should_accept_freshly_minted_token() {
        JwtDecoder decoder = issuer.jwtDecoder();
        String token = issuer.mintAccessToken(issuer.clientId(), issuer.clientSecret());
        assertThat(decoder.decode(token).getSubject()).isEqualTo("sandbox");
    }

    @Test
    void decoder_should_reject_token_signed_by_other_key() throws Exception {
        RSAKey other = new RSAKeyGenerator(2048).keyID("other").algorithm(JWSAlgorithm.RS256).generate();
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer.issuer())
                .subject("sandbox")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
                .claim(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM, SandboxIds.TENANT_ID.toString())
                .claim("scope", EphemeralInternalIssuer.SCOPE_CLAIM)
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(other.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(other));

        assertThatThrownBy(() -> issuer.jwtDecoder().decode(jwt.serialize()))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
