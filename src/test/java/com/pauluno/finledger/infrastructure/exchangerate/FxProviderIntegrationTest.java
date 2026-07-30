package com.pauluno.finledger.infrastructure.exchangerate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.pauluno.finledger.support.IntegrationTestSecurityConfig;
import static com.pauluno.finledger.support.TestJwtAuth.adminJwt;
import static com.pauluno.finledger.support.TestJwtAuth.tenantReadWriteJwt;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestSecurityConfig.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class FxProviderIntegrationTest {

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
    void should_configure_override_resolve_rate_and_stamp_on_journal() throws Exception {
        UUID tenantId = createTenant("fx-shop");

        mockMvc.perform(put("/api/v1/tenants/{tenantId}/fx/config", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pivotCurrencyCode": "USD",
                                  "spreadBps": 100,
                                  "supportedCurrencyCodes": ["USD", "EUR"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spreadBps").value(100));

        mockMvc.perform(put("/api/v1/tenants/{tenantId}/fx/overrides", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseCurrencyCode": "USD",
                                  "quoteCurrencyCode": "EUR",
                                  "rate": "0.900000",
                                  "validFrom": "2020-01-01T00:00:00Z",
                                  "validTo": "2030-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isNoContent());

        MvcResult rateResult = mockMvc.perform(get("/api/v1/tenants/{tenantId}/fx/rates", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .param("base", "USD")
                        .param("quote", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("OVERRIDE"))
                .andReturn();
        // mid 0.9 + 100 bps = 0.909
        assertThat(objectMapper.readTree(rateResult.getResponse().getContentAsString())
                .get("rate").decimalValue()).isEqualByComparingTo("0.909000000000");

        UUID fromId = createAccount(tenantId, "usd-a");
        UUID toId = createAccount(tenantId, "usd-b");
        String body = """
                {
                  "transactionReference": "tx-fx-1",
                  "exchange": {
                    "baseCurrencyCode": "USD",
                    "quoteCurrencyCode": "EUR",
                    "asOf": "2026-07-30T12:00:00Z"
                  },
                  "postings": [
                    {"accountId": "%s", "amount": "-10.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "10.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(fromId, toId);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .with(tenantReadWriteJwt(tenantId))
                        .header("Idempotency-Key", "fx-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private UUID createTenant(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tenants")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "type", "STANDALONE"
                        ))))
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
                                "allowsOverdraft", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accountId").asText());
    }
}
