package com.pauluno.finledger.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.dto.TenantFraudConfigResult;
import com.pauluno.finledger.application.fraud.TenantFraudConfig;
import com.pauluno.finledger.application.port.in.GetTenantFraudConfigUseCase;
import com.pauluno.finledger.application.port.out.TenantFraudConfigRepository;

@Service
public class GetTenantFraudConfigService implements GetTenantFraudConfigUseCase {

    private final TenantFraudConfigRepository repository;

    public GetTenantFraudConfigService(TenantFraudConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public TenantFraudConfigResult execute(UUID tenantId) {
        TenantFraudConfig config = repository.findByTenantId(tenantId)
                .orElseGet(() -> TenantFraudConfig.defaults(tenantId));
        return ConfigureTenantFraudService.toResult(config);
    }
}
