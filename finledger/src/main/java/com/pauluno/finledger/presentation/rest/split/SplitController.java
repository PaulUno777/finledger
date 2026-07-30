package com.pauluno.finledger.presentation.rest.split;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.ConfigureFeeConfigCommand;
import com.pauluno.finledger.application.dto.ConfigureSplitRulesCommand;
import com.pauluno.finledger.application.dto.FeeConfigResult;
import com.pauluno.finledger.application.dto.PostSplitPaymentCommand;
import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.dto.RefundTransactionCommand;
import com.pauluno.finledger.application.dto.SplitRuleSetResult;
import com.pauluno.finledger.application.port.in.ConfigureFeeConfigUseCase;
import com.pauluno.finledger.application.port.in.ConfigureSplitRulesUseCase;
import com.pauluno.finledger.application.port.in.PostSplitPaymentUseCase;
import com.pauluno.finledger.application.port.in.RefundTransactionUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class SplitController {

    private final ConfigureSplitRulesUseCase configureSplitRulesUseCase;
    private final ConfigureFeeConfigUseCase configureFeeConfigUseCase;
    private final PostSplitPaymentUseCase postSplitPaymentUseCase;
    private final RefundTransactionUseCase refundTransactionUseCase;

    public SplitController(
            ConfigureSplitRulesUseCase configureSplitRulesUseCase,
            ConfigureFeeConfigUseCase configureFeeConfigUseCase,
            PostSplitPaymentUseCase postSplitPaymentUseCase,
            RefundTransactionUseCase refundTransactionUseCase
    ) {
        this.configureSplitRulesUseCase = configureSplitRulesUseCase;
        this.configureFeeConfigUseCase = configureFeeConfigUseCase;
        this.postSplitPaymentUseCase = postSplitPaymentUseCase;
        this.refundTransactionUseCase = refundTransactionUseCase;
    }

    @PutMapping("/split-rules/{ruleSetKey}")
    public ResponseEntity<SplitRuleSetResult> putSplitRules(
            @PathVariable UUID tenantId,
            @PathVariable String ruleSetKey,
            @Valid @RequestBody SplitRulesRequest request
    ) {
        List<ConfigureSplitRulesCommand.RuleLine> rules = request.rules().stream()
                .map(r -> new ConfigureSplitRulesCommand.RuleLine(
                        r.targetAccountType(), r.percentage()))
                .toList();
        SplitRuleSetResult result = configureSplitRulesUseCase.execute(
                new ConfigureSplitRulesCommand(
                        tenantId,
                        ruleSetKey,
                        rules,
                        request.remainderTarget()
                )
        );
        return ResponseEntity.ok(result);
    }

    @PutMapping("/fee-config")
    public ResponseEntity<FeeConfigResult> putFeeConfig(
            @PathVariable UUID tenantId,
            @Valid @RequestBody FeeConfigRequest request
    ) {
        FeeConfigResult result = configureFeeConfigUseCase.execute(
                new ConfigureFeeConfigCommand(tenantId, request.feeReversalPolicy())
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/splits")
    public ResponseEntity<PostTransactionResult> postSplit(
            @PathVariable UUID tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PostSplitRequest request
    ) {
        PostTransactionResult result = postSplitPaymentUseCase.execute(new PostSplitPaymentCommand(
                tenantId,
                idempotencyKey,
                request.transactionReference(),
                request.totalAmount(),
                request.currencyCode(),
                request.sourceAccountId(),
                request.accountsByType(),
                request.ruleSetKey()
        ));
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/refunds")
    public ResponseEntity<PostTransactionResult> postRefund(
            @PathVariable UUID tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PostRefundRequest request
    ) {
        PostTransactionResult result = refundTransactionUseCase.execute(new RefundTransactionCommand(
                tenantId,
                idempotencyKey,
                request.transactionReference(),
                request.originalJournalEntryId(),
                request.refundAmount(),
                request.currencyCode()
        ));
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    public record SplitRulesRequest(
            @NotEmpty List<@Valid RuleLineRequest> rules,
            @NotBlank String remainderTarget
    ) {
    }

    public record RuleLineRequest(
            @NotBlank String targetAccountType,
            @NotBlank String percentage
    ) {
    }

    public record FeeConfigRequest(
            @NotBlank String feeReversalPolicy
    ) {
    }

    public record PostSplitRequest(
            @NotBlank String transactionReference,
            @NotBlank String totalAmount,
            @NotBlank String currencyCode,
            @NotNull UUID sourceAccountId,
            @NotEmpty Map<String, UUID> accountsByType,
            @NotBlank String ruleSetKey
    ) {
    }

    public record PostRefundRequest(
            @NotBlank String transactionReference,
            @NotNull UUID originalJournalEntryId,
            @NotBlank String refundAmount,
            @NotBlank String currencyCode
    ) {
    }
}
