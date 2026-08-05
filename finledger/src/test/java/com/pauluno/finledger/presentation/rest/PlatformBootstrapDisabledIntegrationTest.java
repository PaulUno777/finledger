package com.pauluno.finledger.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

/**
 * FL-158: blank bootstrap secret disables the endpoint (404).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("normal")
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class PlatformBootstrapDisabledIntegrationTest {

    private static final String SIGNING_KEY_PEM;

    static {
        try {
            RSAKey key = new RSAKeyGenerator(2048).keyID("bootstrap-disabled").generate();
            SIGNING_KEY_PEM = toPkcs8Pem(key.toRSAPrivateKey());
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("finledger")
            .withUsername("finledger")
            .withPassword("finledger");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "finledger_app");
        registry.add("spring.datasource.password", () -> "finledger");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("finledger.outbox.poll-interval-ms", () -> "3600000");
        registry.add("finledger.env", () -> "local");
        registry.add("finledger.security.issuer", () -> "internal");
        registry.add("finledger.security.max-token-ttl", () -> "15m");
        registry.add("finledger.security.internal.issuer-uri", () -> "http://localhost:8080/internal");
        registry.add("finledger.security.internal.signing-key-pem", () -> SIGNING_KEY_PEM);
        registry.add("finledger.security.internal.clients[0].client-id", () -> "ci");
        registry.add("finledger.security.internal.clients[0].client-secret", () -> "secret");
        registry.add("finledger.security.internal.clients[0].tenant-id",
                () -> UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee").toString());
        registry.add("finledger.platform.bootstrap-secret", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_return_404_when_bootstrap_secret_blank() throws Exception {
        mockMvc.perform(post("/api/v1/platform/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bootstrap_secret\":\"anything\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("bootstrap_disabled"));
    }

    private static String toPkcs8Pem(java.security.PrivateKey privateKey) {
        String b64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
    }
}
