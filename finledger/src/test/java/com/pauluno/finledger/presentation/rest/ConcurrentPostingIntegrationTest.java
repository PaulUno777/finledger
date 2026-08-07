package com.pauluno.finledger.presentation.rest;

import static com.pauluno.finledger.support.TestJwtAuth.adminJwt;
import static com.pauluno.finledger.support.TestJwtAuth.tenantReadWriteJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pauluno.finledger.support.IntegrationTestSecurityConfig;

/**
 * FL-170: concurrent posts must not overdraw when allowsOverdraft=false (§8.3 / §13).
 */
@SpringBootTest
@Import(IntegrationTestSecurityConfig.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class ConcurrentPostingIntegrationTest {

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
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parallel_debits_do_not_overdraw_when_overdraft_disallowed() throws Exception {
        UUID tenantId = createTenant("race-tenant");
        UUID fromId = createAccount(tenantId, "from", false);
        UUID toId = createAccount(tenantId, "to", true);

        // Seed 10.00 available on from
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "seed-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalBody(toId, fromId, "seed", "10.00")))
                .andExpect(status().isCreated());

        int workers = 8;
        AtomicInteger created = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                int idx = i;
                tasks.add(() -> {
                    String body = journalBody(fromId, toId, "race-" + idx, "5.00");
                    int status = mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                                    .with(tenantReadWriteJwt(tenantId))
                                    .header("Idempotency-Key", "race-key-" + idx)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (status == 201) {
                        created.incrementAndGet();
                    } else if (status == 422) {
                        rejected.incrementAndGet();
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(created.get()).isLessThanOrEqualTo(2);
        assertThat(created.get() + rejected.get()).isEqualTo(workers);

        MvcResult balance = mockMvc.perform(get("/api/v1/tenants/{tenantId}/accounts/{id}/balance", tenantId, fromId)
                        .with(tenantReadWriteJwt(tenantId)))
                .andExpect(status().isOk())
                .andReturn();
        BigDecimal available = new BigDecimal(
                objectMapper.readTree(balance.getResponse().getContentAsString()).get("available").asText());
        assertThat(available).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(available).isEqualTo(new BigDecimal("10.00").subtract(new BigDecimal("5.00").multiply(BigDecimal.valueOf(created.get()))));
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

    private UUID createAccount(UUID tenantId, String ownerRef, boolean allowsOverdraft) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ownerRef", ownerRef,
                                "currencyCode", "USD",
                                "type", "MERCHANT_WALLET",
                                "allowsOverdraft", allowsOverdraft))))
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
