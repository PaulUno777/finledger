package com.pauluno.finledger.infrastructure.persistence.jpa.mapper;

import java.util.Currency;

import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.LedgerAccountEntity;

public final class LedgerAccountMapper {

    private LedgerAccountMapper() {
    }

    public static LedgerAccountEntity toEntity(LedgerAccount account) {
        LedgerAccountEntity entity = new LedgerAccountEntity();
        entity.setId(account.id());
        entity.setTenantId(account.tenantId());
        entity.setOwnerRef(account.ownerRef());
        entity.setCurrency(account.currency().getCurrencyCode());
        entity.setAccountType(account.type().name());
        entity.setStatus(account.status().name());
        entity.setAllowsOverdraft(account.allowsOverdraft());
        return entity;
    }

    public static void copyToExisting(LedgerAccount account, LedgerAccountEntity entity) {
        entity.setOwnerRef(account.ownerRef());
        entity.setCurrency(account.currency().getCurrencyCode());
        entity.setAccountType(account.type().name());
        entity.setStatus(account.status().name());
        entity.setAllowsOverdraft(account.allowsOverdraft());
    }

    public static LedgerAccount toDomain(LedgerAccountEntity entity) {
        return new LedgerAccount(
                entity.getId(),
                entity.getTenantId(),
                entity.getOwnerRef(),
                Currency.getInstance(entity.getCurrency()),
                AccountType.valueOf(entity.getAccountType()),
                AccountStatus.valueOf(entity.getStatus()),
                entity.isAllowsOverdraft()
        );
    }
}
