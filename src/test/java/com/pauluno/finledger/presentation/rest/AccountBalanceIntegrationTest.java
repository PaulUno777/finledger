package com.pauluno.finledger.presentation.rest;

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
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.pauluno.finledger.support.IntegrationTestSecurityConfig;
import static com.pauluno.finledger.support.TestJwtAuth.adminJwt;
import static com.pauluno.finledger.support.TestJwtAuth.tenantReadWriteJwt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(IntegrationTestSecurityConfig.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class AccountBalanceIntegrationTest {

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
        registry.add("spring.datasource.username", () -> "finledger_app");
        registry.add("spring.datasource.password", () -> "finledger");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("finledger.outbox.poll-interval-ms", () -> "3600000");
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_expose_available_and_pending_after_mixed_settlement_postings() throws Exception {
        UUID tenantId = createTenant("balance-shop");
        UUID rail = createAccount(tenantId, "rail", "RAIL_CLEARING", true);
        UUID merchant = createAccount(tenantId, "merchant", "MERCHANT_WALLET", true);

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/accounts/{id}/balance", tenantId, merchant)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value("0.00"))
                .andExpect(jsonPath("$.pending").value("0.00"))
                .andExpect(jsonPath("$.held").value("0.00"));

        String body = """
                {
                  "transactionReference": "tx-mixed-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-15.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "10.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "5.00", "currencyCode": "USD", "settlementStatus": "PENDING"}
                  ]
                }
                """.formatted(rail, merchant, merchant);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "bal-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/accounts/{id}/balance", tenantId, merchant)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value("10.00"))
                .andExpect(jsonPath("$.pending").value("5.00"))
                .andExpect(jsonPath("$.held").value("0.00"))
                .andExpect(jsonPath("$.accountType").value("MERCHANT_WALLET"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/accounts/{id}", tenantId, merchant)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value("10.00"))
                .andExpect(jsonPath("$.pending").value("5.00"))
                .andExpect(jsonPath("$.held").value("0.00"))
                .andExpect(jsonPath("$.type").value("MERCHANT_WALLET"));
    }

    @Test
    void should_project_held_on_suspense_hold_account() throws Exception {
        UUID tenantId = createTenant("hold-shop");
        UUID rail = createAccount(tenantId, "rail", "RAIL_CLEARING", true);
        UUID hold = createAccount(tenantId, "hold", "SUSPENSE_HOLD", true);

        String body = """
                {
                  "transactionReference": "tx-hold-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-10.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "7.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "3.00", "currencyCode": "USD", "settlementStatus": "PENDING"}
                  ]
                }
                """.formatted(rail, hold, hold);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "hold-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/accounts/{id}/balance", tenantId, hold)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value("7.00"))
                .andExpect(jsonPath("$.pending").value("3.00"))
                .andExpect(jsonPath("$.held").value("10.00"))
                .andExpect(jsonPath("$.accountType").value("SUSPENSE_HOLD"));
    }

    @Test
    void should_return_zero_balances_on_create() throws Exception {
        UUID tenantId = createTenant("create-bal");
        String payload = objectMapper.writeValueAsString(Map.of(
                "ownerRef", "fresh",
                "currencyCode", "USD",
                "type", "MERCHANT_WALLET",
                "allowsOverdraft", true
        ));
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value("0.00"))
                .andExpect(jsonPath("$.pending").value("0.00"))
                .andExpect(jsonPath("$.held").value("0.00"));
    }

    private UUID createTenant(String name) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "type", "STANDALONE"
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/tenants")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("tenantId").asText());
    }

    private UUID createAccount(UUID tenantId, String ownerRef, String type, boolean allowsOverdraft)
            throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "ownerRef", ownerRef,
                "currencyCode", "USD",
                "type", type,
                "allowsOverdraft", allowsOverdraft
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("accountId").asText());
    }
}
