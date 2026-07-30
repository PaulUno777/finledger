package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.ConfigureTenantFxCommand;
import com.pauluno.finledger.application.dto.TenantFxConfigResult;

public interface ConfigureTenantFxUseCase {

    TenantFxConfigResult execute(ConfigureTenantFxCommand command);
}
