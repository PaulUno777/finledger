package com.pauluno.finledger.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.dto.LedgerAccountResult;
import com.pauluno.finledger.application.port.in.ListLedgerAccountsUseCase;
import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.LedgerAccount;

@Service
public class ListLedgerAccountsService implements ListLedgerAccountsUseCase {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    public ListLedgerAccountsService(
            LedgerAccountRepository ledgerAccountRepository,
            AccountBalanceRepository accountBalanceRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerAccountResult> execute(UUID tenantId) {
        return ledgerAccountRepository.listByTenant(tenantId).stream()
                .map(this::toResult)
                .toList();
    }

    private LedgerAccountResult toResult(LedgerAccount account) {
        AccountBalance balance = accountBalanceRepository.findByAccountId(account.id())
                .orElse(AccountBalance.zero(account.id(), account.currency()));
        return GetLedgerAccountService.toResult(account, balance);
    }
}
