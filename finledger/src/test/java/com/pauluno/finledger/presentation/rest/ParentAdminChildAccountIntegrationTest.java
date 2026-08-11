package com.pauluno.finledger.presentation.rest;

import static com.pauluno.finledger.support.TestJwtAuth.adminJwt;
import static com.pauluno.finledger.support.TestJwtAuth.tenantAdminJwt;
import static com.pauluno.finledger.support.TestJwtAuth.tenantReadWriteJwt;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pauluno.finledger.support.IntegrationTestSecurityConfig;

@SpringBootTest
@Import(IntegrationTestSecurityConfig.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class ParentAdminChildAccountIntegrationTest {

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
    void parent_admin_may_create_direct_child_wallet() throws Exception {
        UUID aggregator = createTenant("agg-parent", "AGGREGATOR", null);
        UUID child = createTenant("child-sm", "SUB_MERCHANT", aggregator);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", child)
                        .with(tenantAdminJwt(aggregator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("child-wallet", "MERCHANT_WALLET")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MERCHANT_WALLET"));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/accounts", child)
                        .with(tenantAdminJwt(aggregator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerRef").value("child-wallet"));
    }

    @Test
    void parent_write_without_admin_cannot_create_child_wallet() throws Exception {
        UUID aggregator = createTenant("agg-write", "AGGREGATOR", null);
        UUID child = createTenant("child-write", "SUB_MERCHANT", aggregator);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", child)
                        .with(tenantReadWriteJwt(aggregator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("blocked", "MERCHANT_WALLET")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_CLAIM_MISMATCH"));
    }

    @Test
    void parent_admin_cannot_call_child_rails() throws Exception {
        UUID aggregator = createTenant("agg-rails", "AGGREGATOR", null);
        UUID child = createTenant("child-rails", "SUB_MERCHANT", aggregator);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/rails/payments", child)
                        .with(tenantAdminJwt(aggregator))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_CLAIM_MISMATCH"));
    }

    @Test
    void standalone_token_cannot_act_on_another_standalone() throws Exception {
        UUID a = createTenant("stand-a", "STANDALONE", null);
        UUID b = createTenant("stand-b", "STANDALONE", null);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", b)
                        .with(tenantAdminJwt(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("nope", "MERCHANT_WALLET")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_CLAIM_MISMATCH"));
    }

    @Test
    void child_token_can_create_own_wallet() throws Exception {
        UUID aggregator = createTenant("agg-own", "AGGREGATOR", null);
        UUID child = createTenant("child-own", "SUB_MERCHANT", aggregator);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", child)
                        .with(tenantReadWriteJwt(child))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("own", "MERCHANT_WALLET")))
                .andExpect(status().isCreated());
    }

    private UUID createTenant(String name, String type, UUID parentId) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("name", name);
        body.put("type", type);
        if (parentId != null) {
            body.put("parentTenantId", parentId.toString());
        }
        MvcResult result = mockMvc.perform(post("/api/v1/tenants")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("tenantId").asText());
    }

    private static String accountBody(String ownerRef, String type) {
        return """
                {"ownerRef":"%s","currencyCode":"USD","type":"%s","allowsOverdraft":false}
                """.formatted(ownerRef, type);
    }
}
