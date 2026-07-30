package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record ConfirmRailSettlementResult(
        UUID instructionId,
        String railReference,
        String status,
        UUID settleJournalEntryId,
        boolean replayed
) {
}
