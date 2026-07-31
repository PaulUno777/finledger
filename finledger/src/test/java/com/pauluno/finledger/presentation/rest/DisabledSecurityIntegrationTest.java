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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.pauluno.finledger.security.policy.SandboxIds;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class DisabledSecurityIntegrationTest {

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
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("finledger.outbox.poll-interval-ms", () -> "3600000");
        registry.add("finledger.security.mode", () -> "disabled");
        registry.add("finledger.env", () -> "local");
        registry.add("spring.profiles.active", () -> "sandbox");
        registry.add("finledger.sandbox.dump-path", () -> DUMP_PATH);
        registry.add("finledger.security.warn-interval-ms", () -> "3600000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_post_journal_without_auth_on_sandbox_tenant() throws Exception {
        UUID tenantId = SandboxIds.TENANT_ID;
        UUID fromId = SandboxIds.FROM_ACCOUNT_ID;
        UUID toId = SandboxIds.TO_ACCOUNT_ID;

        String body = """
                {
                  "transactionReference": "sandbox-it-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-5.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "5.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(fromId, toId);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .header("Idempotency-Key", "sandbox-it-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.journalEntryId").isNotEmpty());
    }

    @Test
    void should_forbid_non_sandbox_tenant_path() throws Exception {
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000099");
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", other)
                        .header("Idempotency-Key", "x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionReference\":\"x\",\"postings\":[]}"))
                .andExpect(status().isForbidden());
    }
}
