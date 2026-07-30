package com.pauluno.finledger.application.port.out;

import java.util.List;
import java.util.UUID;

import com.pauluno.finledger.application.reconciliation.ReconciliationBreak;

public interface ReconciliationBreakRepository {

    ReconciliationBreak save(ReconciliationBreak reconciliationBreak);

    List<ReconciliationBreak> findByTenant(UUID tenantId);
}
