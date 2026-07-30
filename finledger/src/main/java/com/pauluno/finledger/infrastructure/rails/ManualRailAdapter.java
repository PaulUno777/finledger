package com.pauluno.finledger.infrastructure.rails;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.RailAdapter;
import com.pauluno.finledger.application.rail.RailTransactionRequest;
import com.pauluno.finledger.application.rail.RailTransactionResult;
import com.pauluno.finledger.domain.rail.RailSettlementStatus;

/**
 * In-box manual clearing adapter (plan §2.3 / §7). No external PSP —
 * references are local; settlement is confirmed via API/webhook.
 */
@Component
public class ManualRailAdapter implements RailAdapter {

    private final Map<String, RailSettlementStatus> statuses = new ConcurrentHashMap<>();

    @Override
    public RailTransactionResult initiate(RailTransactionRequest request) {
        String reference = "manual-" + UUID.randomUUID();
        statuses.put(reference, RailSettlementStatus.INITIATED);
        return new RailTransactionResult(reference, RailSettlementStatus.INITIATED);
    }

    @Override
    public RailSettlementStatus checkStatus(String railReference) {
        return statuses.getOrDefault(railReference, RailSettlementStatus.FAILED);
    }
}
