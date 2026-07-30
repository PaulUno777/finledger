package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record InitiateRailPaymentResult(
        UUID instructionId,
        String railReference,
        String status,
        UUID initiateJournalEntryId,
        boolean replayed
) {
}
