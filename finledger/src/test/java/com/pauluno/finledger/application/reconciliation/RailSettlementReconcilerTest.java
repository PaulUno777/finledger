package com.pauluno.finledger.application.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.dto.IngestSettlementReportCommand;
import com.pauluno.finledger.application.rail.RailInstruction;
import com.pauluno.finledger.domain.rail.RailSettlementStatus;

@Tag("unit")
class RailSettlementReconcilerTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void should_flag_amount_mismatch() {
        UUID tenantId = UUID.randomUUID();
        UUID batch = UUID.randomUUID();
        RailInstruction instruction = instruction(tenantId, "ref-1", "10.00", RailSettlementStatus.SETTLED);

        List<ReconciliationBreak> breaks = RailSettlementReconciler.match(
                tenantId,
                batch,
                List.of(new IngestSettlementReportCommand.ReportLine("ref-1", "9.00", "USD")),
                List.of(instruction),
                Instant.parse("2026-07-30T00:00:00Z")
        );

        assertThat(breaks).hasSize(1);
        assertThat(breaks.getFirst().reason()).isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    void should_flag_missing_instruction() {
        UUID tenantId = UUID.randomUUID();
        List<ReconciliationBreak> breaks = RailSettlementReconciler.match(
                tenantId,
                UUID.randomUUID(),
                List.of(new IngestSettlementReportCommand.ReportLine("unknown", "1.00", "USD")),
                List.of(),
                Instant.now()
        );

        assertThat(breaks).singleElement().extracting(ReconciliationBreak::reason)
                .isEqualTo("MISSING_INSTRUCTION");
    }

    @Test
    void should_flag_not_settled() {
        UUID tenantId = UUID.randomUUID();
        RailInstruction instruction = instruction(tenantId, "ref-2", "5.00", RailSettlementStatus.INITIATED);

        List<ReconciliationBreak> breaks = RailSettlementReconciler.match(
                tenantId,
                UUID.randomUUID(),
                List.of(new IngestSettlementReportCommand.ReportLine("ref-2", "5.00", "USD")),
                List.of(instruction),
                Instant.now()
        );

        assertThat(breaks).singleElement().extracting(ReconciliationBreak::reason)
                .isEqualTo("NOT_SETTLED");
    }

    @Test
    void should_produce_no_breaks_when_matched() {
        UUID tenantId = UUID.randomUUID();
        RailInstruction instruction = instruction(tenantId, "ref-ok", "5.00", RailSettlementStatus.SETTLED);

        List<ReconciliationBreak> breaks = RailSettlementReconciler.match(
                tenantId,
                UUID.randomUUID(),
                List.of(new IngestSettlementReportCommand.ReportLine("ref-ok", "5.00", "USD")),
                List.of(instruction),
                Instant.now()
        );

        assertThat(breaks).isEmpty();
    }

    private static RailInstruction instruction(
            UUID tenantId,
            String ref,
            String amount,
            RailSettlementStatus status
    ) {
        Instant now = Instant.now();
        return new RailInstruction(
                UUID.randomUUID(),
                tenantId,
                "MANUAL",
                ref,
                new BigDecimal(amount),
                USD,
                status,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status == RailSettlementStatus.SETTLED ? UUID.randomUUID() : null,
                "idem-" + ref,
                now,
                now
        );
    }
}
