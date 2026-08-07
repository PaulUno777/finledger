package com.pauluno.finledger.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Base64;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.application.tenant.TenantContext;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Tenant;
import com.pauluno.finledger.domain.model.TenantType;

/**
 * FL-156: normal profile + durable internal issuer with client-bound tenant_id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("normal")
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class PersistentInternalIssuerIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FROM_ACCOUNT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID TO_ACCOUNT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final String CLIENT_ID = "ci-service";
    private static final String CLIENT_SECRET = "ci-secret";
    private static final String SIGNING_KEY_PEM;
    private static final Currency USD = Currency.getInstance("USD");

    static {
        try {
            RSAKey key = new RSAKeyGenerator(2048).keyID("it").generate();
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
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void seedTenant() {
        TenantContext.enableBypass();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (tenantRepository.findById(TENANT_ID).isEmpty()) {
                    tenantRepository.save(new Tenant(TENANT_ID, TenantType.STANDALONE, null, "ci-tenant"));
                    tenantRepository.replaceAncestry(TENANT_ID, List.of(TENANT_ID));
                }
                ensureAccount(FROM_ACCOUNT, "ci-from");
                ensureAccount(TO_ACCOUNT, "ci-to");
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void should_mint_bound_jwt_and_post_journal() throws Exception {
        String token = mintToken();
        String body = """
                {
                  "transactionReference": "persistent-jwt-it-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-3.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "3.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(FROM_ACCOUNT, TO_ACCOUNT);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", TENANT_ID)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "persistent-jwt-it-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.journalEntryId").isNotEmpty());
    }

    @Test
    void should_expose_jwks_without_auth() throws Exception {
        mockMvc.perform(get("/api/v1/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    void should_reject_invalid_client_credentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grant_type":"client_credentials","client_id":"ci-service","client_secret":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void should_reject_body_tenant_id_on_persistent_mint() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grant_type":"client_credentials","client_id":"%s","client_secret":"%s","tenant_id":"%s"}
                                """.formatted(CLIENT_ID, CLIENT_SECRET, TENANT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("tenant_id_not_allowed"));
    }

    private String mintToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grant_type":"client_credentials","client_id":"%s","client_secret":"%s"}
                                """.formatted(CLIENT_ID, CLIENT_SECRET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("access_token").asText();
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

    private static String toPkcs8Pem(java.security.PrivateKey key) {
        if (!(key instanceof RSAPrivateCrtKey)) {
            throw new IllegalStateException("expected RSA CRT private key");
        }
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }
}
