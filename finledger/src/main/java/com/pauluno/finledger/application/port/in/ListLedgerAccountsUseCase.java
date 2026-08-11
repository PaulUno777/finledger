package com.pauluno.finledger.application.port.in;

import java.util.List;
import java.util.UUID;

import com.pauluno.finledger.application.dto.LedgerAccountResult;

public interface ListLedgerAccountsUseCase {

    List<LedgerAccountResult> execute(UUID tenantId);
}
