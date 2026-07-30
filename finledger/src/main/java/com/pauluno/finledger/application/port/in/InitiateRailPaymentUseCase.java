package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.InitiateRailPaymentCommand;
import com.pauluno.finledger.application.dto.InitiateRailPaymentResult;

public interface InitiateRailPaymentUseCase {

    InitiateRailPaymentResult execute(InitiateRailPaymentCommand command);
}
