package com.pauluno.finledger.infrastructure.security.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.pauluno.finledger.infrastructure.security.TenantClaimAuthorizationFilter;

@Tag("unit")
class PersistentInternalIssuerTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private static String privatePem;
    private static PersistentInternalIssuer issuer;

    @BeforeAll
    static void generateKey() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test").generate();
        privatePem = toPkcs8Pem(key.toRSAPrivateKey());
        issuer = PersistentInternalIssuer.fromPemAndClients(
                "http://localhost:8080/internal",
                privatePem,
                null,
                List.of(
                        new InternalClientCredentials("ci-a", "secret-a", TENANT_A, null),
                        new InternalClientCredentials(
                                "ci-b",
                                "secret-b",
                                TENANT_B,
                                "ledger:read ledger:write")),
                Duration.ofMinutes(10));
    }

    @Test
    void should_mint_token_bound_to_client_tenant() {
        InternalJwtIssuer.AccessToken token = issuer.mintAccessToken("ci-a", "secret-a", null);
        Jwt jwt = issuer.jwtDecoder().decode(token.value());

        assertThat(jwt.getIssuer().toString()).isEqualTo(issuer.issuer());
        assertThat(jwt.getSubject()).isEqualTo("ci-a");
        assertThat(jwt.getClaimAsString(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM))
                .isEqualTo(TENANT_A.toString());
        assertThat(token.scope()).contains("ledger:admin");
    }

    @Test
    void should_mint_with_client_specific_scopes() {
        InternalJwtIssuer.AccessToken token = issuer.mintAccessToken("ci-b", "secret-b", null);
        Jwt jwt = issuer.jwtDecoder().decode(token.value());

        assertThat(jwt.getClaimAsString(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM))
                .isEqualTo(TENANT_B.toString());
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("ledger:read ledger:write");
        assertThat(jwt.getClaimAsString("scope")).doesNotContain("admin");
    }

    @Test
    void should_reject_wrong_secret() {
        assertThatThrownBy(() -> issuer.mintAccessToken("ci-a", "wrong", null))
                .isInstanceOf(InvalidClientCredentialsException.class);
    }

    @Test
    void should_reject_unknown_client() {
        assertThatThrownBy(() -> issuer.mintAccessToken("unknown", "secret-a", null))
                .isInstanceOf(InvalidClientCredentialsException.class);
    }

    @Test
    void should_reject_body_tenant_id_explicitly() {
        assertThatThrownBy(() -> issuer.mintAccessToken("ci-a", "secret-a", TENANT_A))
                .isInstanceOf(TenantIdNotAllowedException.class)
                .hasMessageContaining("tenant_id must not be passed");
    }

    @Test
    void should_reject_blank_pem() {
        assertThatThrownBy(() -> PersistentInternalIssuer.fromPemAndClients(
                        PersistentInternalIssuer.DEFAULT_ISSUER,
                        "  ",
                        null,
                        List.of(new InternalClientCredentials("c", "s", TENANT_A, null)),
                        Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_empty_clients() {
        assertThatThrownBy(() -> PersistentInternalIssuer.fromPemAndClients(
                        PersistentInternalIssuer.DEFAULT_ISSUER,
                        privatePem,
                        null,
                        List.of(),
                        Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_pkcs1_pem() {
        String pkcs1 = """
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA0Z3VS5JJcds3xfn/ygWyF6PZX6W+example
                -----END RSA PRIVATE KEY-----
                """;
        assertThatThrownBy(() -> PersistentInternalIssuer.fromPemAndClients(
                        PersistentInternalIssuer.DEFAULT_ISSUER,
                        pkcs1,
                        null,
                        List.of(new InternalClientCredentials("c", "s", TENANT_A, null)),
                        Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PKCS#1");
    }

    private static String toPkcs8Pem(java.security.PrivateKey key) {
        assertThat(key).isInstanceOf(RSAPrivateCrtKey.class);
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }
}
