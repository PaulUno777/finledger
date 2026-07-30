package com.pauluno.finledger.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.dto.LedgerAccountResult;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.in.GetLedgerAccountUseCase;
import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.LedgerAccount;

@Service
public class GetLedgerAccountService implements GetLedgerAccountUseCase {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    public GetLedgerAccountService(
            LedgerAccountRepository ledgerAccountRepository,
            AccountBalanceRepository accountBalanceRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerAccountResult execute(UUID tenantId, UUID accountId) {
        LedgerAccount account = ledgerAccountRepository.findByIdForTenant(accountId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found for tenant: " + accountId));
        AccountBalance balance = accountBalanceRepository.findByAccountId(accountId)
                .orElse(AccountBalance.zero(account.id(), account.currency()));
        return toResult(account, balance);
    }

    static LedgerAccountResult toResult(LedgerAccount account, AccountBalance balance) {
        return new LedgerAccountResult(
                account.id(),
                account.tenantId(),
                account.ownerRef(),
                account.currency().getCurrencyCode(),
                account.type().name(),
                account.status().name(),
                account.allowsOverdraft(),
                balance.available().amount().toPlainString(),
                balance.pending().amount().toPlainString(),
                balance.held().amount().toPlainString()
        );
    }
}
