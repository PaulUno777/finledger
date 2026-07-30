package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.ConfigureTenantFraudCommand;
import com.pauluno.finledger.application.dto.TenantFraudConfigResult;

public interface ConfigureTenantFraudUseCase {
    TenantFraudConfigResult execute(ConfigureTenantFraudCommand command);
}
