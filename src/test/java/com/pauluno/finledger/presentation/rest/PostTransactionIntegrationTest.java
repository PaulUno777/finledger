package com.pauluno.finledger.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class PostTransactionIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("finledger")
            .withUsername("finledger")
            .withPassword("finledger");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_create_accounts_post_transfer_replay_and_conflict_on_body_change() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        UUID tenantId = UUID.randomUUID();

        UUID fromId = createAccount(mockMvc, tenantId, "merchant-a", true);
        UUID toId = createAccount(mockMvc, tenantId, "merchant-b", true);

        String idempotencyKey = "idem-" + UUID.randomUUID();
        String body = """
                {
                  "transactionReference": "tx-transfer-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-25.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "25.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(fromId, toId);

        MvcResult created = mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.journalEntryId").isNotEmpty())
                .andReturn();

        String journalEntryId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("journalEntryId").asText();

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.journalEntryId").value(journalEntryId));

        String differentBody = """
                {
                  "transactionReference": "tx-transfer-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-30.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "30.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(fromId, toId);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/journal-entries/{id}", tenantId, journalEntryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journalEntryId").value(journalEntryId))
                .andExpect(jsonPath("$.postings.length()").value(2));
    }

    private UUID createAccount(MockMvc mockMvc, UUID tenantId, String ownerRef, boolean allowsOverdraft)
            throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "ownerRef", ownerRef,
                "currencyCode", "USD",
                "type", "MERCHANT_WALLET",
                "allowsOverdraft", allowsOverdraft
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("accountId").asText()).isNotBlank();
        return UUID.fromString(json.get("accountId").asText());
    }
}
