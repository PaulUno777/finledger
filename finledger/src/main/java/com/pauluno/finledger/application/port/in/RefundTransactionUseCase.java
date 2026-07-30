package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.dto.RefundTransactionCommand;

public interface RefundTransactionUseCase {

    PostTransactionResult execute(RefundTransactionCommand command);
}
