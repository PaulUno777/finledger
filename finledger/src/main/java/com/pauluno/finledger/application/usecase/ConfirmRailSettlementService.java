package com.pauluno.finledger.application.usecase;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.ConfirmRailSettlementCommand;
import com.pauluno.finledger.application.dto.ConfirmRailSettlementResult;
import com.pauluno.finledger.application.dto.PostTransactionCommand;
import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.in.ConfirmRailSettlementUseCase;
import com.pauluno.finledger.application.port.in.PostTransactionUseCase;
import com.pauluno.finledger.application.port.out.RailInstructionRepository;
import com.pauluno.finledger.application.rail.RailInstruction;
import com.pauluno.finledger.domain.rail.RailSettlementStatus;

@Service
public class ConfirmRailSettlementService implements ConfirmRailSettlementUseCase {

    private final RailInstructionRepository railInstructionRepository;
    private final PostTransactionUseCase postTransactionUseCase;

    public ConfirmRailSettlementService(
            RailInstructionRepository railInstructionRepository,
            PostTransactionUseCase postTransactionUseCase
    ) {
        this.railInstructionRepository = railInstructionRepository;
        this.postTransactionUseCase = postTransactionUseCase;
    }

    @Override
    @Transactional
    @Auditable(action = "CONFIRM_RAIL_SETTLEMENT", resourceType = "RAIL_INSTRUCTION")
    public ConfirmRailSettlementResult execute(ConfirmRailSettlementCommand command) {
        RailInstruction instruction = railInstructionRepository
                .findByTenantAndReference(command.tenantId(), command.railReference())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rail instruction not found: " + command.railReference()));

        if (instruction.status() == RailSettlementStatus.SETTLED) {
            return new ConfirmRailSettlementResult(
                    instruction.id(),
                    instruction.railReference(),
                    instruction.status().name(),
                    instruction.settleJournalEntryId(),
                    true
            );
        }
        if (instruction.status() == RailSettlementStatus.FAILED) {
            throw new BusinessRuleException(
                    "RAIL_FAILED",
                    "Cannot settle a failed rail instruction");
        }

        String amountStr = instruction.amount().toPlainString();
        String currency = instruction.currency().getCurrencyCode();
        // Clear PENDING exposure, then apply SETTLED legs (append-only).
        PostTransactionResult journal = postTransactionUseCase.execute(new PostTransactionCommand(
                command.tenantId(),
                command.idempotencyKey(),
                "rail-settle-" + instruction.railReference(),
                List.of(
                        new PostTransactionCommand.PostingLine(
                                instruction.clearingAccountId(),
                                amountStr,
                                currency,
                                "PENDING"),
                        new PostTransactionCommand.PostingLine(
                                instruction.counterpartyAccountId(),
                                "-" + amountStr,
                                currency,
                                "PENDING"),
                        new PostTransactionCommand.PostingLine(
                                instruction.clearingAccountId(),
                                "-" + amountStr,
                                currency,
                                "SETTLED"),
                        new PostTransactionCommand.PostingLine(
                                instruction.counterpartyAccountId(),
                                amountStr,
                                currency,
                                "SETTLED")
                )
        ));

        Instant now = Instant.now();
        RailInstruction updated = railInstructionRepository.save(new RailInstruction(
                instruction.id(),
                instruction.tenantId(),
                instruction.railCode(),
                instruction.railReference(),
                instruction.amount(),
                instruction.currency(),
                RailSettlementStatus.SETTLED,
                instruction.clearingAccountId(),
                instruction.counterpartyAccountId(),
                instruction.initiateJournalEntryId(),
                journal.journalEntryId(),
                instruction.idempotencyKey(),
                instruction.createdAt(),
                now
        ));

        return new ConfirmRailSettlementResult(
                updated.id(),
                updated.railReference(),
                updated.status().name(),
                updated.settleJournalEntryId(),
                false
        );
    }
}
