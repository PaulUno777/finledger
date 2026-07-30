package com.pauluno.finledger.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.ConfigureFeeConfigCommand;
import com.pauluno.finledger.application.dto.FeeConfigResult;
import com.pauluno.finledger.application.port.in.ConfigureFeeConfigUseCase;
import com.pauluno.finledger.application.port.out.TenantFeeConfigRepository;
import com.pauluno.finledger.domain.model.FeeReversalPolicyType;

@Service
public class ConfigureFeeConfigService implements ConfigureFeeConfigUseCase {

    private final TenantFeeConfigRepository tenantFeeConfigRepository;

    public ConfigureFeeConfigService(TenantFeeConfigRepository tenantFeeConfigRepository) {
        this.tenantFeeConfigRepository = tenantFeeConfigRepository;
    }

    @Override
    @Transactional
    @Auditable(action = "CONFIGURE_FEE", resourceType = "TENANT_FEE_CONFIG")
    public FeeConfigResult execute(ConfigureFeeConfigCommand command) {
        FeeReversalPolicyType policy = FeeReversalPolicyType.valueOf(command.feeReversalPolicy());
        FeeReversalPolicyType saved = tenantFeeConfigRepository.save(command.tenantId(), policy);
        return new FeeConfigResult(command.tenantId(), saved.name());
    }
}
