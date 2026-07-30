package com.pauluno.finledger.application.port.in;

import java.util.UUID;

import com.pauluno.finledger.application.dto.AccountBalanceResult;

public interface GetAccountBalanceUseCase {

    AccountBalanceResult execute(UUID tenantId, UUID accountId);
}
