package com.pauluno.finledger.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.TenantFxConfig;

public interface TenantFxConfigRepository {

    TenantFxConfig save(TenantFxConfig config);

    Optional<TenantFxConfig> findByTenantId(UUID tenantId);
}
