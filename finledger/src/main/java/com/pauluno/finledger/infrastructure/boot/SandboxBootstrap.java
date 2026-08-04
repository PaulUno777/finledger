package com.pauluno.finledger.infrastructure.boot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Currency;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.application.tenant.TenantContext;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Tenant;
import com.pauluno.finledger.domain.model.TenantType;
import com.pauluno.finledger.infrastructure.security.internal.EphemeralInternalIssuer;
import com.pauluno.finledger.security.policy.SandboxIds;

/**
 * Seeds sandbox tenant/accounts and dumps mint credentials (FL-155 / ADR-016).
 */
@Component
@Profile("sandbox")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SandboxBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SandboxBootstrap.class);
    private static final Currency USD = Currency.getInstance("USD");

    private final String dumpPath;
    private final String baseUrl;
    private final TenantRepository tenantRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final EphemeralInternalIssuer issuer;
    private final TransactionTemplate transactionTemplate;

    public SandboxBootstrap(
            @Value("${finledger.sandbox.dump-path:config/sandbox-ready.txt}") String dumpPath,
            @Value("${finledger.sandbox.base-url:http://localhost:8080}") String baseUrl,
            TenantRepository tenantRepository,
            LedgerAccountRepository ledgerAccountRepository,
            EphemeralInternalIssuer issuer,
            TransactionTemplate transactionTemplate
    ) {
        this.dumpPath = dumpPath;
        this.baseUrl = baseUrl;
        this.tenantRepository = tenantRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.issuer = issuer;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.enableBypass();
        try {
            transactionTemplate.executeWithoutResult(status -> seedSandboxData());
            String content = buildDump();
            log.info("\n{}", content);
            writeDump(content);
        } finally {
            TenantContext.clear();
        }
    }

    private void seedSandboxData() {
        if (tenantRepository.findById(SandboxIds.TENANT_ID).isEmpty()) {
            Tenant tenant = new Tenant(
                    SandboxIds.TENANT_ID, TenantType.STANDALONE, null, SandboxIds.TENANT_NAME);
            tenantRepository.save(tenant);
            tenantRepository.replaceAncestry(SandboxIds.TENANT_ID, List.of(SandboxIds.TENANT_ID));
        }
        ensureAccount(SandboxIds.FROM_ACCOUNT_ID, SandboxIds.FROM_OWNER_REF);
        ensureAccount(SandboxIds.TO_ACCOUNT_ID, SandboxIds.TO_OWNER_REF);
    }

    private void ensureAccount(java.util.UUID accountId, String ownerRef) {
        if (ledgerAccountRepository.findById(accountId).isPresent()) {
            return;
        }
        ledgerAccountRepository.save(new LedgerAccount(
                accountId,
                SandboxIds.TENANT_ID,
                ownerRef,
                USD,
                AccountType.MERCHANT_WALLET,
                AccountStatus.OPEN,
                true
        ));
    }

    private String buildDump() {
        String tenant = SandboxIds.TENANT_ID.toString();
        String from = SandboxIds.FROM_ACCOUNT_ID.toString();
        String to = SandboxIds.TO_ACCOUNT_ID.toString();
        String demoToken = issuer.mintAccessToken(issuer.clientId(), issuer.clientSecret());
        StringBuilder sb = new StringBuilder();
        sb.append("=== FinLedger sandbox ready (FL-155) ===\n");
        sb.append("issuer=").append(issuer.issuer()).append('\n');
        sb.append("baseUrl=").append(baseUrl).append('\n');
        sb.append("tenantId=").append(tenant).append('\n');
        sb.append("fromAccountId=").append(from).append('\n');
        sb.append("toAccountId=").append(to).append('\n');
        sb.append("clientId=").append(issuer.clientId()).append('\n');
        sb.append("clientSecret=").append(issuer.clientSecret()).append('\n');
        sb.append("maxTokenTtl=").append(issuer.maxTokenTtl()).append('\n');
        sb.append('\n');
        sb.append("# Mint a short-lived JWT (re-run when expired):\n");
        sb.append("curl -s -X POST '").append(baseUrl).append("/api/v1/auth/token' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '{\"grant_type\":\"client_credentials\",\"client_id\":\"")
                .append(issuer.clientId())
                .append("\",\"client_secret\":\"")
                .append(issuer.clientSecret())
                .append("\"}'\n");
        sb.append("# Or: ./bin/finledger-cli auth token --client-id ")
                .append(issuer.clientId())
                .append(" --client-secret '<from dump>'\n");
        sb.append('\n');
        sb.append("# Post a demo transfer (token expires — remint as needed):\n");
        sb.append("TOKEN='").append(demoToken).append("'\n");
        sb.append("curl -s -X POST '").append(baseUrl).append("/api/v1/tenants/").append(tenant)
                .append("/journal-entries' \\\n");
        sb.append("  -H \"Authorization: Bearer $TOKEN\" \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -H 'Idempotency-Key: sandbox-demo-1' \\\n");
        sb.append("  -d '{\"transactionReference\":\"sandbox-tx-1\",\"postings\":[")
                .append("{\"accountId\":\"").append(from)
                .append("\",\"amount\":\"-10.00\",\"currencyCode\":\"USD\",\"settlementStatus\":\"SETTLED\"},")
                .append("{\"accountId\":\"").append(to)
                .append("\",\"amount\":\"10.00\",\"currencyCode\":\"USD\",\"settlementStatus\":\"SETTLED\"}]}'\n");
        sb.append("=== end ===\n");
        return sb.toString();
    }

    private void writeDump(String content) {
        try {
            Path path = Path.of(dumpPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            log.info("Wrote sandbox credentials to {}", path.toAbsolutePath());
        } catch (IOException ex) {
            log.warn("Could not write sandbox dump to {}: {}", dumpPath, ex.getMessage());
        }
    }
}
