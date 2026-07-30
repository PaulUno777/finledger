package com.pauluno.finledger.application.dto;

import java.util.List;
import java.util.UUID;

public record IngestSettlementReportCommand(
        UUID tenantId,
        UUID reportBatchId,
        List<ReportLine> lines
) {
    public record ReportLine(
            String railReference,
            String amount,
            String currencyCode
    ) {
    }
}
