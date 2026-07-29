package com.pauluno.finledger.presentation.rest.journal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.PostTransactionCommand;
import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.port.in.GetJournalEntryUseCase;
import com.pauluno.finledger.application.port.in.PostTransactionUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/journal-entries")
public class JournalEntryController {

    private final PostTransactionUseCase postTransactionUseCase;
    private final GetJournalEntryUseCase getJournalEntryUseCase;

    public JournalEntryController(
            PostTransactionUseCase postTransactionUseCase,
            GetJournalEntryUseCase getJournalEntryUseCase
    ) {
        this.postTransactionUseCase = postTransactionUseCase;
        this.getJournalEntryUseCase = getJournalEntryUseCase;
    }

    @PostMapping
    public ResponseEntity<PostTransactionResult> post(
            @PathVariable UUID tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PostJournalEntryRequest request
    ) {
        List<PostTransactionCommand.PostingLine> lines = request.postings().stream()
                .map(p -> new PostTransactionCommand.PostingLine(
                        p.accountId(),
                        p.amount(),
                        p.currencyCode(),
                        p.settlementStatus()
                ))
                .toList();

        PostTransactionResult result = postTransactionUseCase.execute(
                new PostTransactionCommand(
                        tenantId,
                        idempotencyKey,
                        request.transactionReference(),
                        lines
                )
        );

        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/{journalEntryId}")
    public ResponseEntity<PostTransactionResult> get(
            @PathVariable UUID tenantId,
            @PathVariable UUID journalEntryId
    ) {
        return ResponseEntity.ok(getJournalEntryUseCase.execute(tenantId, journalEntryId));
    }

    public record PostJournalEntryRequest(
            @NotBlank String transactionReference,
            @NotEmpty List<@Valid PostingLineRequest> postings
    ) {
    }

    public record PostingLineRequest(
            @NotNull UUID accountId,
            @NotBlank String amount,
            @NotBlank String currencyCode,
            @NotBlank String settlementStatus
    ) {
    }
}
