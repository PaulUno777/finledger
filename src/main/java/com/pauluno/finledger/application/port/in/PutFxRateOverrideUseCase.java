package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.PutFxRateOverrideCommand;

public interface PutFxRateOverrideUseCase {

    void execute(PutFxRateOverrideCommand command);
}
