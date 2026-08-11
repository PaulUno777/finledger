package com.pauluno.finledger.presentation.rest;

import static com.pauluno.finledger.support.TestJwtAuth.platformAdminJwt;
import static com.pauluno.finledger.support.TestJwtAuth.tenantReadWriteJwt;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pauluno.finledger.support.IntegrationTestSecurityConfig;

@SpringBootTest
@Import(IntegrationTestSecurityConfig.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class PlatformProvisionIntegrationTest {

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
    void should_provision_standalone_and_replay() throws Exception {
        UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01");
        String body = objectMapper.writeValueAsString(Map.of(
                "recipe", "STANDALONE",
                "name", "EcoPay",
                "tenantId", tenantId.toString(),
                "currencyCode", "USD"
        ));

        mockMvc.perform(post("/api/v1/platform/provision")
                        .with(platformAdminJwt())
                        .header("Idempotency-Key", "prov-standalone-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.recipe").value("STANDALONE"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.accounts.length()").value(3));

        mockMvc.perform(post("/api/v1/platform/provision")
                        .with(platformAdminJwt())
                        .header("Idempotency-Key", "prov-standalone-1-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.accounts.length()").value(3));
    }

    @Test
    void should_provision_aggregator_without_sub_merchants() throws Exception {
        mockMvc.perform(post("/api/v1/platform/provision")
                        .with(platformAdminJwt())
                        .header("Idempotency-Key", "prov-agg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipe", "AGGREGATOR",
                                "name", "EcoPay Network"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipe").value("AGGREGATOR"))
                .andExpect(jsonPath("$.accounts.length()").value(3));
    }

    @Test
    void should_reject_sub_merchant_recipe() throws Exception {
        mockMvc.perform(post("/api/v1/platform/provision")
                        .with(platformAdminJwt())
                        .header("Idempotency-Key", "prov-bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("recipe", "SUB_MERCHANT"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void tenant_worker_cannot_provision() throws Exception {
        mockMvc.perform(post("/api/v1/platform/provision")
                        .with(tenantReadWriteJwt(UUID.randomUUID()))
                        .header("Idempotency-Key", "prov-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("recipe", "STANDALONE"))))
                .andExpect(status().isForbidden());
    }
}
