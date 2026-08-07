package com.pauluno.finledger.presentation.rest;

import static com.pauluno.finledger.support.TestJwtAuth.adminJwt;
import static com.pauluno.finledger.support.TestJwtAuth.tenantReadWriteJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import com.pauluno.finledger.support.OpenApiPathInventory;
import com.pauluno.finledger.support.OpenApiPathInventory.Entry;

/**
 * FL-160 public API contracts: OpenAPI path/operation inventory + behavioral status codes.
 */
@SpringBootTest
@Import(IntegrationTestSecurityConfig.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@Tag("contract")
@SuppressWarnings("resource")
class ApiContractIntegrationTest {

    private static final String WEBHOOK_SECRET = "contract-rail-hmac-secret";

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
    void openapi_path_inventory_matches_committed_snapshot() throws Exception {
        MvcResult docs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode live = objectMapper.readTree(docs.getResponse().getContentAsString());
        List<Entry> actual = OpenApiPathInventory.extract(live);

        Path snapshotPath = resolveSnapshotPath();
        if (Boolean.getBoolean("finledger.contracts.write")) {
            Files.createDirectories(snapshotPath.getParent());
            Files.writeString(snapshotPath, OpenApiPathInventory.toSnapshotJson(actual));
            return;
        }

        assertThat(Files.isRegularFile(snapshotPath))
                .as("Missing snapshot at %s — regenerate with -Dfinledger.contracts.write=true", snapshotPath)
                .isTrue();
        List<Entry> expected = OpenApiPathInventory.fromSnapshotJson(Files.readString(snapshotPath));
        if (!OpenApiPathInventory.equalInventories(expected, actual)) {
            throw new AssertionError(OpenApiPathInventory.driftMessage(expected, actual));
        }
    }

    @Test
    void unauthenticated_mutation_returns_401() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .header("Idempotency-Key", "contract-noauth-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void idempotency_key_reuse_with_different_body_returns_409() throws Exception {
        UUID tenantId = createTenant("contract-idem");
        UUID fromId = createAccount(tenantId, "from");
        UUID toId = createAccount(tenantId, "to");
        String key = "contract-idem-" + UUID.randomUUID();
        String body1 = journalBody(fromId, toId, "tx-a", "5.00");
        String body2 = journalBody(fromId, toId, "tx-b", "6.00");

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict());
    }

    @Test
    void tenant_claim_mismatch_returns_403() throws Exception {
        UUID tenantA = createTenant("contract-tenant-a");
        UUID tenantB = createTenant("contract-tenant-b");
        UUID fromId = createAccount(tenantA, "from");
        UUID toId = createAccount(tenantA, "to");

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantB)
                        .with(tenantReadWriteJwt(tenantA))
                        .header("Idempotency-Key", "contract-mismatch-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalBody(fromId, toId, "tx-mm", "1.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void settlement_webhook_bad_hmac_returns_401() throws Exception {
        UUID tenantId = createTenant("contract-webhook");
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/rails/webhooks/settlement", tenantId)
                        .header("X-Finledger-Timestamp", "1710000000")
                        .header("X-Finledger-Nonce", UUID.randomUUID().toString())
                        .header("X-Finledger-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"railReference\":\"missing\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    private static Path resolveSnapshotPath() {
        String override = System.getProperty("finledger.contracts.snapshot");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = cwd.resolve("docs/contracts/openapi-paths.json");
        if (Files.isDirectory(cwd.resolve("finledger")) || Files.isRegularFile(fromRoot)) {
            return fromRoot.normalize();
        }
        return cwd.resolve("../docs/contracts/openapi-paths.json").normalize();
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

    private UUID createAccount(UUID tenantId, String ownerRef) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ownerRef", ownerRef,
                                "currencyCode", "USD",
                                "type", "MERCHANT_WALLET",
                                "allowsOverdraft", true))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accountId").asText());
    }

    private static String journalBody(UUID fromId, UUID toId, String ref, String amount) {
        return """
                {
                  "transactionReference": "%s",
                  "postings": [
                    {"accountId": "%s", "amount": "-%s", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "%s", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(ref, fromId, amount, toId, amount);
    }
}
