package com.pauluno.finledger.presentation.rest;

import static com.pauluno.finledger.support.TestJwtAuth.adminJwt;
import static com.pauluno.finledger.support.TestJwtAuth.tenantReadWriteJwt;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import com.pauluno.finledger.application.rail.RailWebhookHmac;
import com.pauluno.finledger.support.IntegrationTestSecurityConfig;

@SpringBootTest
@Import(IntegrationTestSecurityConfig.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class RailPaymentIntegrationTest {

    private static final String WEBHOOK_SECRET = "integration-rail-hmac-secret";

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
        registry.add("finledger.audit.integrity-interval-ms", () -> "3600000");
        System.setProperty(RailWebhookHmac.SECRET_KEY, WEBHOOK_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_initiate_settle_and_reconcile() throws Exception {
        UUID tenantId = createTenant("rail-shop");
        UUID clearing = createAccount(tenantId, "clearing", "RAIL_CLEARING");
        UUID merchant = createAccount(tenantId, "merchant", "MERCHANT_WALLET");

        String initiateBody = objectMapper.writeValueAsString(Map.of(
                "railCode", "MANUAL",
                "amount", "25.00",
                "currencyCode", "USD",
                "clearingAccountId", clearing.toString(),
                "counterpartyAccountId", merchant.toString(),
                "clientReference", "ord-1"
        ));

        MvcResult initiated = mockMvc.perform(post("/api/v1/tenants/{tenantId}/rails/payments", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "rail-init-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiateBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andReturn();

        String railReference = objectMapper.readTree(initiated.getResponse().getContentAsString())
                .get("railReference").asText();

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/accounts/{id}/balance", tenantId, merchant)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value("25.00"))
                .andExpect(jsonPath("$.available").value("0.00"));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/rails/payments/{ref}/settle", tenantId, railReference)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "rail-settle-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/accounts/{id}/balance", tenantId, merchant)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value("0.00"))
                .andExpect(jsonPath("$.available").value("25.00"));

        // Matching report → no breaks
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/reconciliation/reports", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lines": [
                                    {"railReference": "%s", "amount": "25.00", "currencyCode": "USD"}
                                  ]
                                }
                                """.formatted(railReference)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.breakCount").value(0));

        // Mismatch → break
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/reconciliation/reports", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lines": [
                                    {"railReference": "%s", "amount": "20.00", "currencyCode": "USD"}
                                  ]
                                }
                                """.formatted(railReference)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.breakCount").value(1));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/reconciliation/breaks", tenantId)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("AMOUNT_MISMATCH"));
    }

    @Test
    void should_settle_via_hmac_webhook() throws Exception {
        UUID tenantId = createTenant("rail-webhook");
        UUID clearing = createAccount(tenantId, "clearing", "RAIL_CLEARING");
        UUID merchant = createAccount(tenantId, "merchant", "MERCHANT_WALLET");

        MvcResult initiated = mockMvc.perform(post("/api/v1/tenants/{tenantId}/rails/payments", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "wh-init-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "railCode", "MANUAL",
                                "amount", "10.00",
                                "currencyCode", "USD",
                                "clearingAccountId", clearing.toString(),
                                "counterpartyAccountId", merchant.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String railReference = objectMapper.readTree(initiated.getResponse().getContentAsString())
                .get("railReference").asText();

        String body = objectMapper.writeValueAsString(Map.of(
                "railReference", railReference,
                "idempotencyKey", "wh-settle-" + UUID.randomUUID()
        ));
		String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = RailWebhookHmac.sign(WEBHOOK_SECRET, timestamp, nonce, body);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/rails/webhooks/settlement", tenantId)
                        .header("X-Finledger-Timestamp", timestamp)
                        .header("X-Finledger-Nonce", nonce)
                        .header("X-Finledger-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/rails/webhooks/settlement", tenantId)
                        .header("X-Finledger-Timestamp", timestamp)
                        .header("X-Finledger-Nonce", nonce)
                        .header("X-Finledger-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    private UUID createTenant(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tenants")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "type", "STANDALONE"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("tenantId").asText());
    }

    private UUID createAccount(UUID tenantId, String ownerRef, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ownerRef", ownerRef,
                                "currencyCode", "USD",
                                "type", type,
                                "allowsOverdraft", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("accountId").asText()).isNotBlank();
        return UUID.fromString(json.get("accountId").asText());
    }
}
