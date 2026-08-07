package com.pauluno.finledger.presentation.rest.rail;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pauluno.finledger.application.dto.ConfirmRailSettlementCommand;
import com.pauluno.finledger.application.dto.ConfirmRailSettlementResult;
import com.pauluno.finledger.application.dto.InitiateRailPaymentCommand;
import com.pauluno.finledger.application.dto.InitiateRailPaymentResult;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.port.in.ConfirmRailSettlementUseCase;
import com.pauluno.finledger.application.port.in.InitiateRailPaymentUseCase;
import com.pauluno.finledger.application.port.out.SecretsProvider;
import com.pauluno.finledger.application.rail.RailWebhookAntiReplay;
import com.pauluno.finledger.application.rail.RailWebhookHmac;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/rails")
public class RailController {

    private final InitiateRailPaymentUseCase initiateRailPaymentUseCase;
    private final ConfirmRailSettlementUseCase confirmRailSettlementUseCase;
    private final SecretsProvider secretsProvider;
    private final RailWebhookAntiReplay railWebhookAntiReplay;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RailController(
            InitiateRailPaymentUseCase initiateRailPaymentUseCase,
            ConfirmRailSettlementUseCase confirmRailSettlementUseCase,
            SecretsProvider secretsProvider,
            RailWebhookAntiReplay railWebhookAntiReplay
    ) {
        this.initiateRailPaymentUseCase = initiateRailPaymentUseCase;
        this.confirmRailSettlementUseCase = confirmRailSettlementUseCase;
        this.secretsProvider = secretsProvider;
        this.railWebhookAntiReplay = railWebhookAntiReplay;
    }

    @PostMapping("/payments")
    public ResponseEntity<InitiateRailPaymentResult> initiate(
            @PathVariable UUID tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InitiatePaymentRequest request
    ) {
        InitiateRailPaymentResult result = initiateRailPaymentUseCase.execute(
                new InitiateRailPaymentCommand(
                        tenantId,
                        idempotencyKey,
                        request.railCode(),
                        request.amount(),
                        request.currencyCode(),
                        request.clearingAccountId(),
                        request.counterpartyAccountId(),
                        request.clientReference()
                )
        );
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/payments/{railReference}/settle")
    public ResponseEntity<ConfirmRailSettlementResult> settle(
            @PathVariable UUID tenantId,
            @PathVariable String railReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        ConfirmRailSettlementResult result = confirmRailSettlementUseCase.execute(
                new ConfirmRailSettlementCommand(tenantId, railReference, idempotencyKey)
        );
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping(value = "/webhooks/settlement", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConfirmRailSettlementResult> settlementWebhook(
            @PathVariable UUID tenantId,
            @RequestHeader("X-Finledger-Timestamp") String timestamp,
            @RequestHeader("X-Finledger-Nonce") String nonce,
            @RequestHeader("X-Finledger-Signature") String signature,
            @RequestBody String rawBody
    ) throws Exception {
        String secret = secretsProvider.get(RailWebhookHmac.SECRET_KEY)
                .orElseThrow(() -> new BusinessRuleException(
                        "WEBHOOK_SECRET_MISSING",
                        "FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET is not configured"));
        if (!RailWebhookHmac.matches(secret, timestamp, nonce, rawBody, signature)) {
            throw new BusinessRuleException("WEBHOOK_SIGNATURE_INVALID", "Invalid webhook HMAC signature");
        }
        railWebhookAntiReplay.verify(timestamp, nonce);

        JsonNode json = objectMapper.readTree(rawBody);
        String railReference = json.path("railReference").asText(null);
        if (railReference == null || railReference.isBlank()) {
            throw new BusinessRuleException("INVALID_WEBHOOK", "railReference is required");
        }
        String idempotencyKey = json.path("idempotencyKey").asText("webhook-" + nonce);

        ConfirmRailSettlementResult result = confirmRailSettlementUseCase.execute(
                new ConfirmRailSettlementCommand(tenantId, railReference, idempotencyKey)
        );
        return ResponseEntity.ok(result);
    }

    public record InitiatePaymentRequest(
            @NotBlank String railCode,
            @NotBlank String amount,
            @NotBlank String currencyCode,
            @NotNull UUID clearingAccountId,
            @NotNull UUID counterpartyAccountId,
            String clientReference
    ) {
    }
}
