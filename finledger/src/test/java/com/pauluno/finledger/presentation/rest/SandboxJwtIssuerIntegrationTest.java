package com.pauluno.finledger.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pauluno.finledger.security.policy.SandboxIds;

/**
 * FL-155: sandbox profile + ephemeral internal issuer (replaces auth-off DisabledSecurity IT).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sandbox")
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class SandboxJwtIssuerIntegrationTest {

    private static final String CLIENT_ID = "sandbox";
    private static final String CLIENT_SECRET = "integration-test-secret";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("finledger")
            .withUsername("finledger")
            .withPassword("finledger");

    static final String DUMP_PATH;

    static {
        try {
            DUMP_PATH = Files.createTempFile("sandbox-ready-", ".txt").toString();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
        registry.add("finledger.security.issuer", () -> "internal");
        registry.add("finledger.security.max-token-ttl", () -> "15m");
        registry.add("finledger.env", () -> "local");
        registry.add("finledger.sandbox.dump-path", () -> DUMP_PATH);
        registry.add("finledger.sandbox.client-id", () -> CLIENT_ID);
        registry.add("finledger.sandbox.client-secret", () -> CLIENT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_reject_unauthenticated_api_with_401() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", SandboxIds.TENANT_ID)
                        .header("Idempotency-Key", "no-auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionReference\":\"x\",\"postings\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_mint_jwt_and_post_journal_on_sandbox_tenant() throws Exception {
        String token = mintToken();
        UUID fromId = SandboxIds.FROM_ACCOUNT_ID;
        UUID toId = SandboxIds.TO_ACCOUNT_ID;

        String body = """
                {
                  "transactionReference": "sandbox-jwt-it-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-5.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "5.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(fromId, toId);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", SandboxIds.TENANT_ID)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "sandbox-jwt-it-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.journalEntryId").isNotEmpty());
    }

    @Test
    void should_forbid_tenant_claim_mismatch() throws Exception {
        String token = mintToken();
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000099");
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", other)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionReference\":\"x\",\"postings\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_reject_invalid_client_credentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grant_type":"client_credentials","client_id":"sandbox","client_secret":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void should_expose_jwks_without_auth() throws Exception {
        mockMvc.perform(get("/api/v1/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    void should_expose_swagger_ui_without_auth() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    private String mintToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grant_type":"client_credentials","client_id":"%s","client_secret":"%s"}
                                """.formatted(CLIENT_ID, CLIENT_SECRET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("access_token").asText();
    }
}
