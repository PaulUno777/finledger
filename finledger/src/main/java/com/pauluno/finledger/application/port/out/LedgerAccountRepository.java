package com.pauluno.finledger.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.LedgerAccount;

public interface LedgerAccountRepository {

    LedgerAccount save(LedgerAccount account);

    Optional<LedgerAccount> findById(UUID id);

    Optional<LedgerAccount> findByIdForTenant(UUID id, UUID tenantId);
}
