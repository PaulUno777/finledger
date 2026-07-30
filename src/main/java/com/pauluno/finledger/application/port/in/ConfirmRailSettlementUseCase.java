package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.ConfirmRailSettlementCommand;
import com.pauluno.finledger.application.dto.ConfirmRailSettlementResult;

public interface ConfirmRailSettlementUseCase {

    ConfirmRailSettlementResult execute(ConfirmRailSettlementCommand command);
}
