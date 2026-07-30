package com.pauluno.finledger.application.port.out;

import com.pauluno.finledger.application.rail.RailTransactionRequest;
import com.pauluno.finledger.application.rail.RailTransactionResult;
import com.pauluno.finledger.domain.rail.RailSettlementStatus;

/**
 * Payment rail connector (plan §7). Vendor adapters implement this port.
 */
public interface RailAdapter {

    RailTransactionResult initiate(RailTransactionRequest request);

    RailSettlementStatus checkStatus(String railReference);
}
