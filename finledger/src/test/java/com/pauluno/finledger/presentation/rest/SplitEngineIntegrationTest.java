package com.pauluno.finledger.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class SplitEngineIntegrationTest {

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
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_configure_rules_post_split_and_refund_no_reverse() throws Exception {
        UUID tenantId = createTenant("split-shop");
        UUID source = createAccount(tenantId, "rail", "RAIL_CLEARING", true);
        UUID merchant = createAccount(tenantId, "merchant", "MERCHANT_WALLET", true);
        UUID fee = createAccount(tenantId, "fee", "FEE_PLATFORM_REVENUE", true);

        mockMvc.perform(put("/api/v1/tenants/{tenantId}/split-rules/{key}", tenantId, "default")
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    {"targetAccountType": "MERCHANT_WALLET", "percentage": "95"},
                                    {"targetAccountType": "FEE_PLATFORM_REVENUE", "percentage": "5"}
                                  ],
                                  "remainderTarget": "MERCHANT_WALLET"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleSetKey").value("default"));

        mockMvc.perform(put("/api/v1/tenants/{tenantId}/fee-config", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feeReversalPolicy\": \"NO_REVERSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeReversalPolicy").value("NO_REVERSE"));

        String splitBody = """
                {
                  "transactionReference": "split-1",
                  "totalAmount": "100.00",
                  "currencyCode": "USD",
                  "sourceAccountId": "%s",
                  "accountsByType": {
                    "MERCHANT_WALLET": "%s",
                    "FEE_PLATFORM_REVENUE": "%s"
                  },
                  "ruleSetKey": "default"
                }
                """.formatted(source, merchant, fee);

        MvcResult splitResult = mockMvc.perform(post("/api/v1/tenants/{tenantId}/splits", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "split-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(splitBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("POSTING"))
                .andExpect(jsonPath("$.postings.length()").value(3))
                .andReturn();

        String journalEntryId = objectMapper.readTree(splitResult.getResponse().getContentAsString())
                .get("journalEntryId").asText();

        String refundBody = """
                {
                  "transactionReference": "refund-1",
                  "originalJournalEntryId": "%s",
                  "refundAmount": "95.00",
                  "currencyCode": "USD"
                }
                """.formatted(journalEntryId);

        MvcResult refundResult = mockMvc.perform(post("/api/v1/tenants/{tenantId}/refunds", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "refund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REFUND"))
                .andReturn();

        JsonNode refund = objectMapper.readTree(refundResult.getResponse().getContentAsString());
        assertThat(refund.get("postings")).isNotEmpty();
        boolean feeTouched = false;
        for (JsonNode posting : refund.get("postings")) {
            if (posting.get("accountId").asText().equals(fee.toString())) {
                feeTouched = true;
            }
        }
        assertThat(feeTouched).as("NO_REVERSE must not reverse fee legs").isFalse();
    }

    @Test
    void should_refund_pro_rata_including_fees() throws Exception {
        UUID tenantId = createTenant("split-prorata");
        UUID source = createAccount(tenantId, "rail", "RAIL_CLEARING", true);
        UUID merchant = createAccount(tenantId, "merchant", "MERCHANT_WALLET", true);
        UUID fee = createAccount(tenantId, "fee", "FEE_PLATFORM_REVENUE", true);

        mockMvc.perform(put("/api/v1/tenants/{tenantId}/split-rules/{key}", tenantId, "default")
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    {"targetAccountType": "MERCHANT_WALLET", "percentage": "95"},
                                    {"targetAccountType": "FEE_PLATFORM_REVENUE", "percentage": "5"}
                                  ],
                                  "remainderTarget": "MERCHANT_WALLET"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/tenants/{tenantId}/fee-config", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feeReversalPolicy\": \"PRO_RATA\"}"))
                .andExpect(status().isOk());

        String splitBody = """
                {
                  "transactionReference": "split-pr",
                  "totalAmount": "100.00",
                  "currencyCode": "USD",
                  "sourceAccountId": "%s",
                  "accountsByType": {
                    "MERCHANT_WALLET": "%s",
                    "FEE_PLATFORM_REVENUE": "%s"
                  },
                  "ruleSetKey": "default"
                }
                """.formatted(source, merchant, fee);

        MvcResult splitResult = mockMvc.perform(post("/api/v1/tenants/{tenantId}/splits", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "split-pr-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(splitBody))
                .andExpect(status().isCreated())
                .andReturn();

        String journalEntryId = objectMapper.readTree(splitResult.getResponse().getContentAsString())
                .get("journalEntryId").asText();

        MvcResult refundResult = mockMvc.perform(post("/api/v1/tenants/{tenantId}/refunds", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "refund-pr-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionReference": "refund-pr",
                                  "originalJournalEntryId": "%s",
                                  "refundAmount": "50.00",
                                  "currencyCode": "USD"
                                }
                                """.formatted(journalEntryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REFUND"))
                .andReturn();

        JsonNode refund = objectMapper.readTree(refundResult.getResponse().getContentAsString());
        boolean feeTouched = false;
        for (JsonNode posting : refund.get("postings")) {
            if (posting.get("accountId").asText().equals(fee.toString())) {
                feeTouched = true;
                assertThat(posting.get("amount").asText()).isEqualTo("-2.50");
            }
        }
        assertThat(feeTouched).isTrue();
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
