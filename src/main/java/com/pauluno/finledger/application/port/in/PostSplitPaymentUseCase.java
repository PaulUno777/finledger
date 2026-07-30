package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.PostSplitPaymentCommand;
import com.pauluno.finledger.application.dto.PostTransactionResult;

public interface PostSplitPaymentUseCase {

    PostTransactionResult execute(PostSplitPaymentCommand command);
}
