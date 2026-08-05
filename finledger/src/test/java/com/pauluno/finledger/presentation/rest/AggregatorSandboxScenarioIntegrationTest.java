package com.pauluno.finledger.presentation.rest;

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
import com.pauluno.finledger.security.policy.SandboxScenarioIds;

/**
 * FL-157: aggregator scenario seed + mint with sub-merchant tenant_id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sandbox")
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class AggregatorSandboxScenarioIntegrationTest {

    private static final String CLIENT_ID = "sandbox";
    private static final String CLIENT_SECRET = "aggregator-it-secret";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("finledger")
            .withUsername("finledger")
            .withPassword("finledger");

    static final String DUMP_PATH;

    static {
        try {
            DUMP_PATH = Files.createTempFile("sandbox-agg-", ".txt").toString();
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
        registry.add("finledger.sandbox.scenario", () -> "aggregator");
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_mint_for_sub_merchant_and_post_journal() throws Exception {
        UUID tenantId = SandboxScenarioIds.SUB_MERCHANT_TENANT_ID;
        UUID fromId = SandboxScenarioIds.SUB_MERCHANT_FROM_ACCOUNT_ID;
        UUID toId = SandboxScenarioIds.SUB_MERCHANT_TO_ACCOUNT_ID;
        String token = mintToken(tenantId);

        String body = """
                {
                  "transactionReference": "agg-sandbox-it-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-3.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "3.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(fromId, toId);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "agg-sandbox-it-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.journalEntryId").isNotEmpty());
    }

    @Test
    void should_reject_unknown_tenant_on_mint() throws Exception {
        UUID unknown = UUID.fromString("00000000-0000-0000-0000-000000000099");
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grant_type":"client_credentials","client_id":"%s","client_secret":"%s","tenant_id":"%s"}
                                """.formatted(CLIENT_ID, CLIENT_SECRET, unknown)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown_tenant"));
    }

    private String mintToken(UUID tenantId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grant_type":"client_credentials","client_id":"%s","client_secret":"%s","tenant_id":"%s"}
                                """.formatted(CLIENT_ID, CLIENT_SECRET, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("access_token").asText();
    }
}
