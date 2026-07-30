package com.pauluno.finledger.application.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.pauluno.finledger.application.dto.IngestSettlementReportCommand;
import com.pauluno.finledger.application.rail.RailInstruction;
import com.pauluno.finledger.domain.rail.RailSettlementStatus;

/**
 * Pure matcher: settlement report lines vs rail instructions → breaks.
 */
public final class RailSettlementReconciler {

    private RailSettlementReconciler() {
    }

    public static List<ReconciliationBreak> match(
            UUID tenantId,
            UUID reportBatchId,
            List<IngestSettlementReportCommand.ReportLine> lines,
            List<RailInstruction> instructions,
            Instant detectedAt
    ) {
        Map<String, RailInstruction> byRef = instructions.stream()
                .collect(Collectors.toMap(RailInstruction::railReference, Function.identity(), (a, b) -> a));

        List<ReconciliationBreak> breaks = new ArrayList<>();
        for (IngestSettlementReportCommand.ReportLine line : lines) {
            RailInstruction instruction = byRef.get(line.railReference());
            BigDecimal reported = new BigDecimal(line.amount());
            Currency currency = Currency.getInstance(line.currencyCode());

            if (instruction == null) {
                breaks.add(newBreak(
                        tenantId, line.railReference(), null, reported, currency,
                        "MISSING_INSTRUCTION", detectedAt, reportBatchId));
                continue;
            }

            if (instruction.status() != RailSettlementStatus.SETTLED) {
                breaks.add(newBreak(
                        tenantId, line.railReference(), instruction.amount(), reported, currency,
                        "NOT_SETTLED", detectedAt, reportBatchId));
                continue;
            }

            if (instruction.amount().compareTo(reported) != 0
                    || !instruction.currency().getCurrencyCode().equals(line.currencyCode())) {
                breaks.add(newBreak(
                        tenantId, line.railReference(), instruction.amount(), reported, currency,
                        "AMOUNT_MISMATCH", detectedAt, reportBatchId));
            }
        }
        return breaks;
    }

    private static ReconciliationBreak newBreak(
            UUID tenantId,
            String railReference,
            BigDecimal expected,
            BigDecimal reported,
            Currency currency,
            String reason,
            Instant detectedAt,
            UUID reportBatchId
    ) {
        return new ReconciliationBreak(
                UUID.randomUUID(),
                tenantId,
                railReference,
                expected,
                reported,
                currency,
                reason,
                detectedAt,
                reportBatchId
        );
    }
}
