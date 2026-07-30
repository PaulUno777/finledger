package com.pauluno.finledger.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.FeeReversalPolicyType;

public interface TenantFeeConfigRepository {

    FeeReversalPolicyType save(UUID tenantId, FeeReversalPolicyType policy);

    Optional<FeeReversalPolicyType> findByTenantId(UUID tenantId);
}
