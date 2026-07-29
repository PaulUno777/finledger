package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.LedgerAccountEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.mapper.LedgerAccountMapper;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataLedgerAccountRepository;

@Component
public class LedgerAccountJpaAdapter implements LedgerAccountRepository {

    private final SpringDataLedgerAccountRepository springData;
    private final AccountBalanceRepository accountBalanceRepository;

    public LedgerAccountJpaAdapter(
            SpringDataLedgerAccountRepository springData,
            AccountBalanceRepository accountBalanceRepository
    ) {
        this.springData = springData;
        this.accountBalanceRepository = accountBalanceRepository;
    }

    @Override
    @Transactional
    public LedgerAccount save(LedgerAccount account) {
        Optional<LedgerAccountEntity> existing = springData.findById(account.id());
        LedgerAccountEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            LedgerAccountMapper.copyToExisting(account, entity);
        } else {
            entity = LedgerAccountMapper.toEntity(account);
            springData.save(entity);
            accountBalanceRepository.save(
                    AccountBalance.zero(account.id(), account.currency()),
                    account.tenantId()
            );
            return LedgerAccountMapper.toDomain(entity);
        }
        return LedgerAccountMapper.toDomain(springData.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LedgerAccount> findById(UUID id) {
        return springData.findById(id).map(LedgerAccountMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LedgerAccount> findByIdForTenant(UUID id, UUID tenantId) {
        return springData.findByIdAndTenantId(id, tenantId).map(LedgerAccountMapper::toDomain);
    }
}
