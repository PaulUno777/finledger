package com.pauluno.finledger.application.usecase;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.pauluno.finledger.application.dto.CreateLedgerAccountCommand;
import com.pauluno.finledger.application.dto.CreateTenantCommand;
import com.pauluno.finledger.application.dto.CreateTenantResult;
import com.pauluno.finledger.application.dto.LedgerAccountResult;
import com.pauluno.finledger.application.dto.ProvisionPlatformCommand;
import com.pauluno.finledger.application.dto.ProvisionPlatformResult;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.port.in.CreateLedgerAccountUseCase;
import com.pauluno.finledger.application.port.in.CreateTenantUseCase;
import com.pauluno.finledger.application.port.in.ListLedgerAccountsUseCase;
import com.pauluno.finledger.application.port.in.ProvisionPlatformUseCase;
import com.pauluno.finledger.application.port.out.TenantFeeConfigRepository;
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.application.tenant.TenantContext;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.FeeReversalPolicyType;
import com.pauluno.finledger.domain.model.Tenant;
import com.pauluno.finledger.domain.model.TenantType;

/**
 * Control-plane root recipe: create STANDALONE or AGGREGATOR when absent,
 * plus default clearing / wallets / fee-config (FL-181).
 */
@Service
public class ProvisionPlatformService implements ProvisionPlatformUseCase {

    private final TenantRepository tenantRepository;
    private final CreateTenantUseCase createTenantUseCase;
    private final CreateLedgerAccountUseCase createLedgerAccountUseCase;
    private final ListLedgerAccountsUseCase listLedgerAccountsUseCase;
    private final TenantFeeConfigRepository tenantFeeConfigRepository;
    private final TransactionTemplate transactionTemplate;

    public ProvisionPlatformService(
            TenantRepository tenantRepository,
            CreateTenantUseCase createTenantUseCase,
            CreateLedgerAccountUseCase createLedgerAccountUseCase,
            ListLedgerAccountsUseCase listLedgerAccountsUseCase,
            TenantFeeConfigRepository tenantFeeConfigRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.createTenantUseCase = createTenantUseCase;
        this.createLedgerAccountUseCase = createLedgerAccountUseCase;
        this.listLedgerAccountsUseCase = listLedgerAccountsUseCase;
        this.tenantFeeConfigRepository = tenantFeeConfigRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public ProvisionPlatformResult execute(ProvisionPlatformCommand command) {
        TenantType type = parseRecipe(command.recipe());
        String currency = command.currencyCode() == null || command.currencyCode().isBlank()
                ? "USD"
                : command.currencyCode().trim().toUpperCase(Locale.ROOT);
        String name = command.name() == null || command.name().isBlank()
                ? defaultName(type)
                : command.name().trim();

        TenantContext.enableBypass();
        try {
            ProvisionPlatformResult result =
                    transactionTemplate.execute(status -> provision(command, type, currency, name));
            if (result == null) {
                throw new IllegalStateException("platform provision returned no result");
            }
            return result;
        } finally {
            TenantContext.clear();
        }
    }

    private ProvisionPlatformResult provision(
            ProvisionPlatformCommand command,
            TenantType type,
            String currency,
            String name
    ) {
        boolean replayed = false;
        UUID tenantId;
        String tenantName = name;
        if (command.tenantId() != null) {
            var existing = tenantRepository.findById(command.tenantId());
            if (existing.isPresent()) {
                Tenant tenant = existing.get();
                if (tenant.type() != type) {
                    throw new BusinessRuleException(
                            "TENANT_ID_CONFLICT",
                            "Tenant already exists with a different type: " + tenant.type());
                }
                replayed = true;
                tenantId = tenant.id();
                tenantName = tenant.name();
            } else {
                CreateTenantResult created = createTenantUseCase.execute(
                        new CreateTenantCommand(name, type.name(), null, command.tenantId()));
                tenantId = created.tenantId();
                tenantName = created.name();
            }
        } else {
            CreateTenantResult created = createTenantUseCase.execute(
                    new CreateTenantCommand(name, type.name(), null, null));
            tenantId = created.tenantId();
            tenantName = created.name();
        }

        ensureAccounts(tenantId, type, currency);
        tenantFeeConfigRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantFeeConfigRepository.save(tenantId, FeeReversalPolicyType.NO_REVERSE));

        List<LedgerAccountResult> accounts = listLedgerAccountsUseCase.execute(tenantId);
        return new ProvisionPlatformResult(
                tenantId,
                type.name(),
                tenantName,
                type.name(),
                replayed,
                FeeReversalPolicyType.NO_REVERSE.name(),
                accounts
        );
    }

    private void ensureAccounts(UUID tenantId, TenantType type, String currency) {
        List<LedgerAccountResult> existing = listLedgerAccountsUseCase.execute(tenantId);
        if (type == TenantType.STANDALONE) {
            ensureAccount(tenantId, existing, "merchant-wallet", currency, AccountType.MERCHANT_WALLET);
            ensureAccount(tenantId, existing, "rail-clearing", currency, AccountType.RAIL_CLEARING);
            ensureAccount(tenantId, existing, "fee-platform", currency, AccountType.FEE_PLATFORM_REVENUE);
        } else {
            ensureAccount(tenantId, existing, "aggregator-pool", currency, AccountType.AGGREGATOR_POOL);
            ensureAccount(tenantId, existing, "rail-clearing", currency, AccountType.RAIL_CLEARING);
            ensureAccount(tenantId, existing, "fee-platform", currency, AccountType.FEE_PLATFORM_REVENUE);
        }
    }

    private void ensureAccount(
            UUID tenantId,
            List<LedgerAccountResult> existing,
            String ownerRef,
            String currency,
            AccountType type
    ) {
        boolean present = existing.stream()
                .anyMatch(a -> ownerRef.equals(a.ownerRef()) && type.name().equals(a.type()));
        if (present) {
            return;
        }
        createLedgerAccountUseCase.execute(new CreateLedgerAccountCommand(
                tenantId,
                ownerRef,
                currency,
                type.name(),
                type == AccountType.RAIL_CLEARING
        ));
    }

    private static TenantType parseRecipe(String recipe) {
        if (recipe == null || recipe.isBlank()) {
            throw new IllegalArgumentException("recipe is required (STANDALONE or AGGREGATOR)");
        }
        String normalized = recipe.trim().toUpperCase(Locale.ROOT);
        if ("SUB_MERCHANT".equals(normalized)) {
            throw new BusinessRuleException(
                    "INVALID_ARGUMENT",
                    "SUB_MERCHANT is not a root recipe; POST /api/v1/tenants with parentTenantId");
        }
        try {
            TenantType type = TenantType.valueOf(normalized);
            if (type == TenantType.SUB_MERCHANT) {
                throw new BusinessRuleException(
                        "INVALID_ARGUMENT",
                        "SUB_MERCHANT is not a root recipe");
            }
            return type;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown recipe '" + recipe + "' (expected STANDALONE or AGGREGATOR)");
        }
    }

    private static String defaultName(TenantType type) {
        return type == TenantType.AGGREGATOR ? "Aggregator" : "Standalone";
    }
}
