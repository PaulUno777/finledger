package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.AccountBalanceEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.mapper.AccountBalanceMapper;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataAccountBalanceRepository;

@Component
public class AccountBalanceJpaAdapter implements AccountBalanceRepository {

    private final SpringDataAccountBalanceRepository springData;

    public AccountBalanceJpaAdapter(SpringDataAccountBalanceRepository springData) {
        this.springData = springData;
    }

    @Override
    @Transactional
    public AccountBalance save(AccountBalance balance, UUID tenantId) {
        Optional<AccountBalanceEntity> existing = springData.findById(balance.accountId());
        AccountBalanceEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            AccountBalanceMapper.copyToExisting(balance, entity);
        } else {
            entity = AccountBalanceMapper.toNewEntity(balance, tenantId);
        }
        return AccountBalanceMapper.toDomain(springData.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountBalance> findByAccountId(UUID accountId) {
        return springData.findById(accountId).map(AccountBalanceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AccountBalance> findByAccountIds(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        return springData.findByAccountIdIn(accountIds).stream()
                .map(AccountBalanceMapper::toDomain)
                .collect(Collectors.toMap(
                        AccountBalance::accountId,
                        Function.identity(),
                        (a, b) -> a,
                        HashMap::new
                ));
    }
}
