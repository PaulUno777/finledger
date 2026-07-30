package com.pauluno.finledger.application.port.in;

import java.util.UUID;

import com.pauluno.finledger.application.dto.TenantFraudConfigResult;

public interface GetTenantFraudConfigUseCase {
    TenantFraudConfigResult execute(UUID tenantId);
}
