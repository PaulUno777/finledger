package com.pauluno.finledger.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.application.fraud.TenantFraudConfig;

public interface TenantFraudConfigRepository {

    TenantFraudConfig save(TenantFraudConfig config);

    Optional<TenantFraudConfig> findByTenantId(UUID tenantId);
}
