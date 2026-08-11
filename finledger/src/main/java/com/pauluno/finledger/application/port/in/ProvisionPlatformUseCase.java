package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.ProvisionPlatformCommand;
import com.pauluno.finledger.application.dto.ProvisionPlatformResult;

public interface ProvisionPlatformUseCase {

    ProvisionPlatformResult execute(ProvisionPlatformCommand command);
}
