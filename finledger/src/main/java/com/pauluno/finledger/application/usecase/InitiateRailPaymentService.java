package com.pauluno.finledger.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.InitiateRailPaymentCommand;
import com.pauluno.finledger.application.dto.InitiateRailPaymentResult;
import com.pauluno.finledger.application.dto.PostTransactionCommand;
import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.in.InitiateRailPaymentUseCase;
import com.pauluno.finledger.application.port.in.PostTransactionUseCase;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.RailAdapter;
import com.pauluno.finledger.application.port.out.RailInstructionRepository;
import com.pauluno.finledger.application.rail.RailInstruction;
import com.pauluno.finledger.application.rail.RailTransactionRequest;
import com.pauluno.finledger.application.rail.RailTransactionResult;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.rail.RailSettlementStatus;

@Service
public class InitiateRailPaymentService implements InitiateRailPaymentUseCase {

    private final RailAdapter railAdapter;
    private final RailInstructionRepository railInstructionRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PostTransactionUseCase postTransactionUseCase;

    public InitiateRailPaymentService(
            RailAdapter railAdapter,
            RailInstructionRepository railInstructionRepository,
            LedgerAccountRepository ledgerAccountRepository,
            PostTransactionUseCase postTransactionUseCase
    ) {
        this.railAdapter = railAdapter;
        this.railInstructionRepository = railInstructionRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.postTransactionUseCase = postTransactionUseCase;
    }

    @Override
    @Transactional
    @Auditable(action = "INITIATE_RAIL_PAYMENT", resourceType = "RAIL_INSTRUCTION")
    public InitiateRailPaymentResult execute(InitiateRailPaymentCommand command) {
        return railInstructionRepository.findByTenantAndIdempotencyKey(
                        command.tenantId(), command.idempotencyKey())
                .map(existing -> new InitiateRailPaymentResult(
                        existing.id(),
                        existing.railReference(),
                        existing.status().name(),
                        existing.initiateJournalEntryId(),
                        true))
                .orElseGet(() -> initiateNew(command));
    }

    private InitiateRailPaymentResult initiateNew(InitiateRailPaymentCommand command) {
        BigDecimal amount = new BigDecimal(command.amount());
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_AMOUNT", "Rail payment amount must be positive");
        }
        Currency currency = Currency.getInstance(command.currencyCode());

        LedgerAccount clearing = ledgerAccountRepository
                .findByIdForTenant(command.clearingAccountId(), command.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Clearing account not found: " + command.clearingAccountId()));
        if (clearing.type() != AccountType.RAIL_CLEARING) {
            throw new BusinessRuleException(
                    "INVALID_CLEARING_ACCOUNT",
                    "clearingAccountId must be of type RAIL_CLEARING");
        }
        ledgerAccountRepository
                .findByIdForTenant(command.counterpartyAccountId(), command.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Counterparty account not found: " + command.counterpartyAccountId()));

        RailTransactionResult railResult = railAdapter.initiate(new RailTransactionRequest(
                command.tenantId(),
                command.railCode(),
                amount,
                currency,
                command.clearingAccountId(),
                command.counterpartyAccountId(),
                command.clientReference()
        ));

        String amountStr = amount.toPlainString();
        PostTransactionResult journal = postTransactionUseCase.execute(new PostTransactionCommand(
                command.tenantId(),
                "rail-init-" + command.idempotencyKey(),
                "rail-init-" + railResult.railReference(),
                List.of(
                        new PostTransactionCommand.PostingLine(
                                command.clearingAccountId(),
                                "-" + amountStr,
                                command.currencyCode(),
                                "PENDING"),
                        new PostTransactionCommand.PostingLine(
                                command.counterpartyAccountId(),
                                amountStr,
                                command.currencyCode(),
                                "PENDING")
                )
        ));

        Instant now = Instant.now();
        UUID instructionId = UUID.randomUUID();
        RailInstruction saved = railInstructionRepository.save(new RailInstruction(
                instructionId,
                command.tenantId(),
                command.railCode(),
                railResult.railReference(),
                amount,
                currency,
                RailSettlementStatus.INITIATED,
                command.clearingAccountId(),
                command.counterpartyAccountId(),
                journal.journalEntryId(),
                null,
                command.idempotencyKey(),
                now,
                now
        ));

        return new InitiateRailPaymentResult(
                saved.id(),
                saved.railReference(),
                saved.status().name(),
                saved.initiateJournalEntryId(),
                false
        );
    }
}
