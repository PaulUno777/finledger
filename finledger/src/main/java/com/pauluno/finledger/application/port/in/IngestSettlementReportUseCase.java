package com.pauluno.finledger.application.port.in;

import java.util.List;
import java.util.UUID;

import com.pauluno.finledger.application.dto.IngestSettlementReportCommand;
import com.pauluno.finledger.application.dto.IngestSettlementReportResult;
import com.pauluno.finledger.application.reconciliation.ReconciliationBreak;

public interface IngestSettlementReportUseCase {

    IngestSettlementReportResult execute(IngestSettlementReportCommand command);

    List<ReconciliationBreak> listBreaks(UUID tenantId);
}
