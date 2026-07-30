package com.pauluno.finledger.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.ConfigureTenantFraudCommand;
import com.pauluno.finledger.application.dto.TenantFraudConfigResult;
import com.pauluno.finledger.application.fraud.FraudFailMode;
import com.pauluno.finledger.application.fraud.TenantFraudConfig;
import com.pauluno.finledger.application.port.in.ConfigureTenantFraudUseCase;
import com.pauluno.finledger.application.port.out.TenantFraudConfigRepository;

@Service
public class ConfigureTenantFraudService implements ConfigureTenantFraudUseCase {

    private final TenantFraudConfigRepository repository;

    public ConfigureTenantFraudService(TenantFraudConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    @Auditable(action = "CONFIGURE_FRAUD", resourceType = "TENANT_FRAUD_CONFIG")
    public TenantFraudConfigResult execute(ConfigureTenantFraudCommand command) {
        TenantFraudConfig saved = repository.save(new TenantFraudConfig(
                command.tenantId(),
                command.enabled(),
                FraudFailMode.valueOf(command.failMode()),
                command.maxAmount(),
                command.velocityMax(),
                command.velocityWindowSeconds(),
                command.holdAccountId(),
                command.denylistOwnerRefs() == null ? List.of() : command.denylistOwnerRefs()
        ));
        return toResult(saved);
    }

    static TenantFraudConfigResult toResult(TenantFraudConfig config) {
        return new TenantFraudConfigResult(
                config.tenantId(),
                config.enabled(),
                config.failMode().name(),
                config.maxAmount(),
                config.velocityMax(),
                config.velocityWindowSeconds(),
                config.holdAccountId(),
                config.denylistOwnerRefs()
        );
    }
}
