package com.pauluno.finledger.application.dto;

import java.util.List;
import java.util.UUID;

public record IngestSettlementReportResult(
        UUID reportBatchId,
        int matchedCount,
        int breakCount,
        List<UUID> breakIds
) {
}
