package com.pauluno.finledger.domain;

import java.util.Currency;
import java.util.Map;
import java.util.UUID;

import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;

public final class DomainFixtures {

    public static final Currency USD = Currency.getInstance("USD");
    public static final Currency EUR = Currency.getInstance("EUR");

    private DomainFixtures() {
    }

    public static LedgerAccount openAccount(UUID id, UUID tenantId, Currency currency, boolean overdraft) {
        return new LedgerAccount(
                id,
                tenantId,
                "owner-" + id,
                currency,
                AccountType.MERCHANT_WALLET,
                AccountStatus.OPEN,
                overdraft
        );
    }

    public static LedgerAccount holdAccount(UUID id, UUID tenantId, Currency currency) {
        return new LedgerAccount(
                id,
                tenantId,
                "hold-" + id,
                currency,
                AccountType.SUSPENSE_HOLD,
                AccountStatus.OPEN,
                false
        );
    }

    public static LedgerAccount closedAccount(UUID id, UUID tenantId, Currency currency) {
        return new LedgerAccount(
                id,
                tenantId,
                "closed-" + id,
                currency,
                AccountType.MERCHANT_WALLET,
                AccountStatus.CLOSED,
                false
        );
    }

    public static Map<UUID, AccountBalance> funded(LedgerAccount account, String availableAmount) {
        Money available = Money.of(availableAmount, account.currency());
        Money zero = Money.zero(account.currency());
        return Map.of(
                account.id(),
                new AccountBalance(account.id(), account.currency(), available, zero, zero)
        );
    }
}
