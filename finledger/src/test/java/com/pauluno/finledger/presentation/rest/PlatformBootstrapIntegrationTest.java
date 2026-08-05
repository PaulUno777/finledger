package com.pauluno.finledger.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.tenant.TenantContext;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.infrastructure.security.TenantClaimAuthorizationFilter;
import com.pauluno.finledger.support.TestJwtAuth;

/**
 * FL-158: one-shot platform bootstrap + create tenant with client-supplied id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("normal")
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class PlatformBootstrapIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID FROM_ACCOUNT = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd1");
    private static final UUID TO_ACCOUNT = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd2");
    private static final String CLIENT_ID = "ci-bootstrap";
    private static final String CLIENT_SECRET = "ci-bootstrap-secret";
    private static final String BOOTSTRAP_SECRET = "platform-bootstrap-it-secret";
    private static final String SIGNING_KEY_PEM;
    private static final Currency USD = Currency.getInstance("USD");

    static {
        try {
            RSAKey key = new RSAKeyGenerator(2048).keyID("bootstrap-it").generate();
            SIGNING_KEY_PEM = toPkcs8Pem(key.toRSAPrivateKey());
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
        registry.add("finledger.env", () -> "local");
        registry.add("finledger.security.issuer", () -> "internal");
        registry.add("finledger.security.max-token-ttl", () -> "15m");
        registry.add("finledger.security.internal.issuer-uri", () -> "http://localhost:8080/internal");
        registry.add("finledger.security.internal.signing-key-pem", () -> SIGNING_KEY_PEM);
        registry.add("finledger.security.internal.clients[0].client-id", () -> CLIENT_ID);
        registry.add("finledger.security.internal.clients[0].client-secret", () -> CLIENT_SECRET);
        registry.add("finledger.security.internal.clients[0].tenant-id", TENANT_ID::toString);
        registry.add(
                "finledger.security.internal.clients[0].scopes",
                () -> "ledger:read ledger:write ledger:admin");
        registry.add("finledger.platform.bootstrap-secret", () -> BOOTSTRAP_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_bootstrap_create_tenant_with_id_and_mint_for_journal() throws Exception {
        MvcResult bootstrap = mockMvc.perform(post("/api/v1/platform/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bootstrap_secret\":\"" + BOOTSTRAP_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.scope").value("platform:admin"))
                .andReturn();

        String platformToken = objectMapper.readTree(bootstrap.getResponse().getContentAsString())
                .get("access_token").asText();

        String[] parts = platformToken.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payload = objectMapper.readTree(payloadJson);
        org.assertj.core.api.Assertions.assertThat(payload.has(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM))
                .isFalse();

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"ColdStart","type":"STANDALONE","id":"%s"}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()));

        mockMvc.perform(post("/api/v1/platform/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bootstrap_secret\":\"" + BOOTSTRAP_SECRET + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("bootstrap_already_claimed"));

        mockMvc.perform(post("/api/v1/tenants")
                        .with(TestJwtAuth.adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nope","type":"STANDALONE","id":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());

        seedAccounts();
        String merchantToken = mintMerchantToken();
        String body = """
                {
                  "transactionReference": "bootstrap-it-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-2.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "2.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(FROM_ACCOUNT, TO_ACCOUNT);
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", TENANT_ID)
                        .header("Authorization", "Bearer " + merchantToken)
                        .header("Idempotency-Key", "bootstrap-it-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void should_reject_wrong_bootstrap_secret() throws Exception {
        mockMvc.perform(post("/api/v1/platform/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bootstrap_secret\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_bootstrap_secret"));
    }

    private void seedAccounts() {
        TenantContext.enableBypass();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                ensureAccount(FROM_ACCOUNT, "boot-from");
                ensureAccount(TO_ACCOUNT, "boot-to");
            });
        } finally {
            TenantContext.clear();
        }
    }

    private void ensureAccount(UUID accountId, String ownerRef) {
        if (ledgerAccountRepository.findById(accountId).isPresent()) {
            return;
        }
        ledgerAccountRepository.save(new LedgerAccount(
                accountId,
                TENANT_ID,
                ownerRef,
                USD,
                AccountType.MERCHANT_WALLET,
                AccountStatus.OPEN,
                true));
    }

    private String mintMerchantToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grant_type":"client_credentials","client_id":"%s","client_secret":"%s"}
                                """.formatted(CLIENT_ID, CLIENT_SECRET)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
    }

    private static String toPkcs8Pem(java.security.PrivateKey privateKey) {
        String b64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
    }
}
