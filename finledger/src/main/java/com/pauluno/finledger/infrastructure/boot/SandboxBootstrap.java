package com.pauluno.finledger.infrastructure.boot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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
import com.pauluno.finledger.infrastructure.boot.sandbox.AggregatorSandboxSeeder;
import com.pauluno.finledger.infrastructure.boot.sandbox.RemittanceSandboxSeeder;
import com.pauluno.finledger.infrastructure.boot.sandbox.SandboxScenario;
import com.pauluno.finledger.infrastructure.boot.sandbox.SandboxScenarioSeeder;
import com.pauluno.finledger.infrastructure.boot.sandbox.SandboxSeedSnapshot;
import com.pauluno.finledger.infrastructure.boot.sandbox.SimpleSandboxSeeder;
import com.pauluno.finledger.infrastructure.security.internal.EphemeralInternalIssuer;
import com.pauluno.finledger.infrastructure.security.internal.InternalJwtIssuer;
import com.pauluno.finledger.security.policy.SandboxIds;

/**
 * Seeds sandbox scenario data and dumps mint credentials (FL-157 / ADR-016).
 */
@Component
@Profile("sandbox")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SandboxBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SandboxBootstrap.class);

    private final String dumpPath;
    private final String baseUrl;
    private final SandboxScenario scenario;
    private final TenantRepository tenantRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final EphemeralInternalIssuer issuer;
    private final TransactionTemplate transactionTemplate;

    public SandboxBootstrap(
            @Value("${finledger.sandbox.dump-path:config/sandbox-ready.txt}") String dumpPath,
            @Value("${finledger.sandbox.base-url:http://localhost:8080}") String baseUrl,
            @Value("${finledger.sandbox.scenario:simple}") String scenarioConfig,
            TenantRepository tenantRepository,
            LedgerAccountRepository ledgerAccountRepository,
            InternalJwtIssuer issuer,
            TransactionTemplate transactionTemplate
    ) {
        if (!(issuer instanceof EphemeralInternalIssuer ephemeral)) {
            throw new IllegalStateException("sandbox profile requires EphemeralInternalIssuer");
        }
        this.dumpPath = dumpPath;
        this.baseUrl = baseUrl;
        this.scenario = SandboxScenario.fromConfig(scenarioConfig);
        this.tenantRepository = tenantRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.issuer = ephemeral;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.enableBypass();
        try {
            SandboxSeedSnapshot[] holder = new SandboxSeedSnapshot[1];
            transactionTemplate.executeWithoutResult(status -> holder[0] = seeder().seed(
                    tenantRepository, ledgerAccountRepository));
            String content = buildDump(holder[0]);
            log.info("\n{}", content);
            writeDump(content);
        } finally {
            TenantContext.clear();
        }
    }

    private SandboxScenarioSeeder seeder() {
        return switch (scenario) {
            case SIMPLE -> new SimpleSandboxSeeder();
            case AGGREGATOR -> new AggregatorSandboxSeeder();
            case REMITTANCE -> new RemittanceSandboxSeeder();
        };
    }

    private String buildDump(SandboxSeedSnapshot snap) {
        UUID defaultTenant = snap.tenants().isEmpty()
                ? SandboxIds.TENANT_ID
                : snap.tenants().getFirst().tenantId();
        String demoToken = issuer.mintAccessToken(
                issuer.clientId(), issuer.clientSecret(), defaultTenant).value();

        StringBuilder sb = new StringBuilder();
        sb.append("=== FinLedger sandbox ready (FL-157) ===\n");
        sb.append("scenario=").append(scenario.configValue()).append('\n');
        sb.append("issuer=").append(issuer.issuer()).append('\n');
        sb.append("baseUrl=").append(baseUrl).append('\n');
        sb.append("clientId=").append(issuer.clientId()).append('\n');
        sb.append("clientSecret=").append(issuer.clientSecret()).append('\n');
        sb.append("maxTokenTtl=").append(issuer.maxTokenTtl()).append('\n');
        sb.append('\n');
        sb.append("# Seeded tenants:\n");
        for (var t : snap.tenants()) {
            sb.append("tenant.").append(t.type()).append('=')
                    .append(t.tenantId()).append(' ').append(t.name()).append('\n');
        }
        sb.append("# Seeded accounts:\n");
        for (var a : snap.accounts()) {
            sb.append("account.").append(a.ownerRef()).append('=')
                    .append(a.accountId())
                    .append(" tenant=").append(a.tenantId())
                    .append(" currency=").append(a.currency())
                    .append('\n');
        }
        sb.append('\n');
        sb.append("# Mint JWT (optional tenant_id for any seeded tenant):\n");
        sb.append("curl -s -X POST '").append(baseUrl).append("/api/v1/auth/token' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '{\"grant_type\":\"client_credentials\",\"client_id\":\"")
                .append(issuer.clientId())
                .append("\",\"client_secret\":\"")
                .append(issuer.clientSecret())
                .append("\",\"tenant_id\":\"")
                .append(defaultTenant)
                .append("\"}'\n");
        sb.append("# Or: ./bin/finledger-cli auth token --client-secret '<from dump>' --tenant-id ")
                .append(defaultTenant).append('\n');
        sb.append('\n');
        if (snap.accounts().size() >= 2) {
            var from = snap.accounts().get(0);
            var to = snap.accounts().stream()
                    .filter(a -> a.tenantId().equals(from.tenantId()) && !a.accountId().equals(from.accountId()))
                    .findFirst()
                    .orElse(snap.accounts().get(1));
            sb.append("# Demo journal on tenant ").append(from.tenantId()).append(":\n");
            sb.append("TOKEN='").append(demoToken).append("'\n");
            sb.append("curl -s -X POST '").append(baseUrl).append("/api/v1/tenants/")
                    .append(from.tenantId()).append("/journal-entries' \\\n");
            sb.append("  -H \"Authorization: Bearer $TOKEN\" \\\n");
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -H 'Idempotency-Key: sandbox-demo-1' \\\n");
            sb.append("  -d '{\"transactionReference\":\"sandbox-tx-1\",\"postings\":[")
                    .append("{\"accountId\":\"").append(from.accountId())
                    .append("\",\"amount\":\"-10.00\",\"currencyCode\":\"")
                    .append(from.currency())
                    .append("\",\"settlementStatus\":\"SETTLED\"},")
                    .append("{\"accountId\":\"").append(to.accountId())
                    .append("\",\"amount\":\"10.00\",\"currencyCode\":\"")
                    .append(to.currency())
                    .append("\",\"settlementStatus\":\"SETTLED\"}]}'\n");
        }
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
