package com.pauluno.finledger.infrastructure.tenant;

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
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.application.tenant.TenantContext;

@SpringBootTest
@Import(IntegrationTestSecurityConfig.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class TenantRlsIntegrationTest {

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

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_let_aggregator_see_sub_merchant_journal_and_hide_from_sibling() throws Exception {
        UUID aggregatorId = createTenant("Agg", "AGGREGATOR", null);
        UUID subId = createTenant("Shop", "SUB_MERCHANT", aggregatorId);
        UUID otherId = createTenant("Other", "STANDALONE", null);

        UUID fromId = createAccount(subId, "from");
        UUID toId = createAccount(subId, "to");
        UUID journalEntryId = postTransfer(subId, fromId, toId);

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/journal-entries/{id}", aggregatorId, journalEntryId)
                        .with(tenantReadWriteJwt(aggregatorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journalEntryId").value(journalEntryId.toString()));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/journal-entries/{id}", otherId, journalEntryId)
                        .with(tenantReadWriteJwt(otherId)))
                .andExpect(status().isNotFound());

        TenantContext.set(aggregatorId);
        try {
            assertThat(journalEntryRepository.findById(journalEntryId)).isPresent();
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(otherId);
        try {
            assertThat(journalEntryRepository.findById(journalEntryId)).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    private UUID createTenant(String name, String type, UUID parentTenantId) throws Exception {
        String payload = parentTenantId == null
                ? objectMapper.writeValueAsString(Map.of("name", name, "type", type))
                : objectMapper.writeValueAsString(Map.of(
                        "name", name,
                        "type", type,
                        "parentTenantId", parentTenantId.toString()));
        MvcResult result = mockMvc.perform(post("/api/v1/tenants")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("tenantId").asText());
    }

    private UUID createAccount(UUID tenantId, String ownerRef) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "ownerRef", ownerRef,
                "currencyCode", "USD",
                "type", "MERCHANT_WALLET",
                "allowsOverdraft", true
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

    private UUID postTransfer(UUID tenantId, UUID fromId, UUID toId) throws Exception {
        String body = """
                {
                  "transactionReference": "tx-rls-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-5.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "5.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(fromId, toId);
        MvcResult created = mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "rls-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("journalEntryId").asText());
    }
}
