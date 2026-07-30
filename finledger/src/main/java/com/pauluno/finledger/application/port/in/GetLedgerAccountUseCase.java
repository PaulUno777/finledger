package com.pauluno.finledger.application.port.in;

import java.util.UUID;

import com.pauluno.finledger.application.dto.LedgerAccountResult;

public interface GetLedgerAccountUseCase {

    LedgerAccountResult execute(UUID tenantId, UUID accountId);
}
