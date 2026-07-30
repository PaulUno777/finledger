package com.pauluno.finledger.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
class AuditTrailIntegrationTest {

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
        registry.add("finledger.audit.integrity-interval-ms", () -> "3600000");
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_write_audit_chain_on_post_and_detect_tamper() throws Exception {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        MvcResult tenantResult = mockMvc.perform(post("/api/v1/tenants")
                        .with(adminJwt())
                        .header("traceparent", traceparent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "audit-shop",
                                "type", "STANDALONE"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID tenantId = UUID.fromString(objectMapper.readTree(tenantResult.getResponse().getContentAsString())
                .get("tenantId").asText());

        UUID fromId = createAccount(tenantId, "from");
        UUID toId = createAccount(tenantId, "to");

        String body = """
                {
                  "transactionReference": "tx-audit-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-10.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "10.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(fromId, toId);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "audit-" + UUID.randomUUID())
                        .header("traceparent", traceparent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit/integrity", tenantId)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.checkedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var disable = connection.createStatement();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE audit_log SET payload_hash = ? WHERE tenant_id = ? AND action = 'POST_TRANSACTION'");
             var enable = connection.createStatement()) {
            disable.execute("ALTER TABLE audit_log DISABLE TRIGGER audit_log_no_update");
            statement.setString(1, "a".repeat(64));
            statement.setObject(2, tenantId);
            int updated = statement.executeUpdate();
            assertThat(updated).isGreaterThanOrEqualTo(1);
            enable.execute("ALTER TABLE audit_log ENABLE TRIGGER audit_log_no_update");
        }

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/audit/integrity", tenantId)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.breakAt").isNumber());
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
}
