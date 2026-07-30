package com.pauluno.finledger.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.AccountBalance;

public interface AccountBalanceRepository {

    AccountBalance save(AccountBalance balance, UUID tenantId);

    Optional<AccountBalance> findByAccountId(UUID accountId);

    Map<UUID, AccountBalance> findByAccountIds(Collection<UUID> accountIds);
}
