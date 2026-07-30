package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.CreateTenantCommand;
import com.pauluno.finledger.application.dto.CreateTenantResult;

public interface CreateTenantUseCase {

    CreateTenantResult execute(CreateTenantCommand command);
}
