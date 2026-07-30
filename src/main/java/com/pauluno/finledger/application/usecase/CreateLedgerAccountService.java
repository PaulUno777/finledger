package com.pauluno.finledger.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.dto.CreateLedgerAccountCommand;
import com.pauluno.finledger.application.dto.CreateLedgerAccountResult;
import com.pauluno.finledger.application.port.in.CreateLedgerAccountUseCase;
import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;

@Service
public class CreateLedgerAccountService implements CreateLedgerAccountUseCase {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    public CreateLedgerAccountService(
            LedgerAccountRepository ledgerAccountRepository,
            AccountBalanceRepository accountBalanceRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
    }

    @Override
    @Transactional
    public CreateLedgerAccountResult execute(CreateLedgerAccountCommand command) {
        LedgerAccount account = new LedgerAccount(
                UUID.randomUUID(),
                command.tenantId(),
                command.ownerRef(),
                command.currency(),
                AccountType.valueOf(command.type()),
                AccountStatus.OPEN,
                command.allowsOverdraft()
        );
        LedgerAccount saved = ledgerAccountRepository.save(account);
        AccountBalance balance = accountBalanceRepository.findByAccountId(saved.id())
                .orElse(AccountBalance.zero(saved.id(), saved.currency()));
        return new CreateLedgerAccountResult(
                saved.id(),
                saved.tenantId(),
                saved.ownerRef(),
                saved.currency().getCurrencyCode(),
                saved.type().name(),
                saved.status().name(),
                saved.allowsOverdraft(),
                balance.available().amount().toPlainString(),
                balance.pending().amount().toPlainString(),
                balance.held().amount().toPlainString()
        );
    }
}
