package com.pauluno.finledger.presentation.rest.fraud;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.ConfigureTenantFraudCommand;
import com.pauluno.finledger.application.dto.RiskDecisionResult;
import com.pauluno.finledger.application.dto.TenantFraudConfigResult;
import com.pauluno.finledger.application.port.in.ConfigureTenantFraudUseCase;
import com.pauluno.finledger.application.port.in.GetTenantFraudConfigUseCase;
import com.pauluno.finledger.application.port.in.ListRiskDecisionsUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/fraud")
public class FraudController {

    private final ConfigureTenantFraudUseCase configureTenantFraudUseCase;
    private final GetTenantFraudConfigUseCase getTenantFraudConfigUseCase;
    private final ListRiskDecisionsUseCase listRiskDecisionsUseCase;

    public FraudController(
            ConfigureTenantFraudUseCase configureTenantFraudUseCase,
            GetTenantFraudConfigUseCase getTenantFraudConfigUseCase,
            ListRiskDecisionsUseCase listRiskDecisionsUseCase
    ) {
        this.configureTenantFraudUseCase = configureTenantFraudUseCase;
        this.getTenantFraudConfigUseCase = getTenantFraudConfigUseCase;
        this.listRiskDecisionsUseCase = listRiskDecisionsUseCase;
    }

    @GetMapping("/config")
    public ResponseEntity<TenantFraudConfigResult> getConfig(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(getTenantFraudConfigUseCase.execute(tenantId));
    }

    @PutMapping("/config")
    public ResponseEntity<TenantFraudConfigResult> putConfig(
            @PathVariable UUID tenantId,
            @Valid @RequestBody FraudConfigRequest request
    ) {
        return ResponseEntity.ok(configureTenantFraudUseCase.execute(new ConfigureTenantFraudCommand(
                tenantId,
                Boolean.TRUE.equals(request.enabled()),
                request.failMode(),
                request.maxAmount(),
                request.velocityMax() == null ? 0 : request.velocityMax(),
                request.velocityWindowSeconds() == null ? 3600 : request.velocityWindowSeconds(),
                request.holdAccountId(),
                request.denylistOwnerRefs() == null ? List.of() : request.denylistOwnerRefs()
        )));
    }

    @GetMapping("/decisions")
    public ResponseEntity<List<RiskDecisionResult>> listDecisions(
            @PathVariable UUID tenantId,
            @RequestParam String transactionReference
    ) {
        return ResponseEntity.ok(listRiskDecisionsUseCase.execute(tenantId, transactionReference));
    }

    public record FraudConfigRequest(
            @NotNull Boolean enabled,
            @NotBlank String failMode,
            BigDecimal maxAmount,
            Integer velocityMax,
            @Positive Integer velocityWindowSeconds,
            UUID holdAccountId,
            List<String> denylistOwnerRefs
    ) {
    }
}
