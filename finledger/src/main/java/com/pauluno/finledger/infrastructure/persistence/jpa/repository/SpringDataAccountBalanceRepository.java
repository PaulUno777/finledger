package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.AccountBalanceEntity;

public interface SpringDataAccountBalanceRepository extends JpaRepository<AccountBalanceEntity, UUID> {

    List<AccountBalanceEntity> findByAccountIdIn(Collection<UUID> accountIds);
}
