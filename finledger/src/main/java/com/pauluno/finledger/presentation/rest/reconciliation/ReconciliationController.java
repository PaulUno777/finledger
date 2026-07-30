package com.pauluno.finledger.presentation.rest.reconciliation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.IngestSettlementReportCommand;
import com.pauluno.finledger.application.dto.IngestSettlementReportResult;
import com.pauluno.finledger.application.port.in.IngestSettlementReportUseCase;
import com.pauluno.finledger.application.reconciliation.ReconciliationBreak;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/reconciliation")
public class ReconciliationController {

    private final IngestSettlementReportUseCase ingestSettlementReportUseCase;

    public ReconciliationController(IngestSettlementReportUseCase ingestSettlementReportUseCase) {
        this.ingestSettlementReportUseCase = ingestSettlementReportUseCase;
    }

    @PostMapping("/reports")
    public ResponseEntity<IngestSettlementReportResult> ingestReport(
            @PathVariable UUID tenantId,
            @Valid @RequestBody IngestReportRequest request
    ) {
        List<IngestSettlementReportCommand.ReportLine> lines = request.lines().stream()
                .map(l -> new IngestSettlementReportCommand.ReportLine(
                        l.railReference(), l.amount(), l.currencyCode()))
                .toList();
        IngestSettlementReportResult result = ingestSettlementReportUseCase.execute(
                new IngestSettlementReportCommand(tenantId, request.reportBatchId(), lines)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/breaks")
    public ResponseEntity<List<BreakView>> listBreaks(@PathVariable UUID tenantId) {
        List<BreakView> views = ingestSettlementReportUseCase.listBreaks(tenantId).stream()
                .map(BreakView::from)
                .toList();
        return ResponseEntity.ok(views);
    }

    public record IngestReportRequest(
            UUID reportBatchId,
            @NotEmpty List<ReportLineRequest> lines
    ) {
    }

    public record ReportLineRequest(
            @NotBlank String railReference,
            @NotBlank String amount,
            @NotBlank String currencyCode
    ) {
    }

    public record BreakView(
            UUID id,
            String railReference,
            String expectedAmount,
            String reportedAmount,
            String currencyCode,
            String reason,
            UUID reportBatchId
    ) {
        static BreakView from(ReconciliationBreak br) {
            return new BreakView(
                    br.id(),
                    br.railReference(),
                    br.expectedAmount() == null ? null : br.expectedAmount().toPlainString(),
                    br.reportedAmount() == null ? null : br.reportedAmount().toPlainString(),
                    br.currency().getCurrencyCode(),
                    br.reason(),
                    br.reportBatchId()
            );
        }
    }
}
