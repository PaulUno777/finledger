package com.pauluno.finledger.infrastructure.persistence.jpa.mapper;

import java.util.Currency;
import java.util.UUID;

import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.AccountBalanceEntity;

public final class AccountBalanceMapper {

    private AccountBalanceMapper() {
    }

    public static AccountBalanceEntity toNewEntity(AccountBalance balance, UUID tenantId) {
        AccountBalanceEntity entity = new AccountBalanceEntity();
        entity.setAccountId(balance.accountId());
        entity.setTenantId(tenantId);
        applyAmounts(balance, entity);
        return entity;
    }

    public static void copyToExisting(AccountBalance balance, AccountBalanceEntity entity) {
        applyAmounts(balance, entity);
    }

    public static AccountBalance toDomain(AccountBalanceEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return new AccountBalance(
                entity.getAccountId(),
                currency,
                Money.of(entity.getAvailable(), currency),
                Money.of(entity.getPending(), currency),
                Money.of(entity.getHeld(), currency)
        );
    }

    private static void applyAmounts(AccountBalance balance, AccountBalanceEntity entity) {
        entity.setCurrency(balance.currency().getCurrencyCode());
        entity.setAvailable(balance.available().amount());
        entity.setPending(balance.pending().amount());
        entity.setHeld(balance.held().amount());
    }
}
