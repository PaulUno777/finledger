package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.PostTransactionCommand;
import com.pauluno.finledger.application.dto.PostTransactionResult;

public interface PostTransactionUseCase {

    PostTransactionResult execute(PostTransactionCommand command);
}
