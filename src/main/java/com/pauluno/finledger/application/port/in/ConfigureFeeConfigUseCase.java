package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.ConfigureFeeConfigCommand;
import com.pauluno.finledger.application.dto.FeeConfigResult;

public interface ConfigureFeeConfigUseCase {

    FeeConfigResult execute(ConfigureFeeConfigCommand command);
}
