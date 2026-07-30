package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.CreateLedgerAccountCommand;
import com.pauluno.finledger.application.dto.CreateLedgerAccountResult;

public interface CreateLedgerAccountUseCase {

    CreateLedgerAccountResult execute(CreateLedgerAccountCommand command);
}
