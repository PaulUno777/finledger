package com.pauluno.finledger.presentation.rest.fx;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.ConfigureTenantFxCommand;
import com.pauluno.finledger.application.dto.ExchangeRateResult;
import com.pauluno.finledger.application.dto.PutFxRateOverrideCommand;
import com.pauluno.finledger.application.dto.TenantFxConfigResult;
import com.pauluno.finledger.application.port.in.ConfigureTenantFxUseCase;
import com.pauluno.finledger.application.port.in.PutFxRateOverrideUseCase;
import com.pauluno.finledger.application.port.in.ResolveExchangeRateUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/fx")
public class FxController {

    private final ConfigureTenantFxUseCase configureTenantFxUseCase;
    private final PutFxRateOverrideUseCase putFxRateOverrideUseCase;
    private final ResolveExchangeRateUseCase resolveExchangeRateUseCase;

    public FxController(
            ConfigureTenantFxUseCase configureTenantFxUseCase,
            PutFxRateOverrideUseCase putFxRateOverrideUseCase,
            ResolveExchangeRateUseCase resolveExchangeRateUseCase
    ) {
        this.configureTenantFxUseCase = configureTenantFxUseCase;
        this.putFxRateOverrideUseCase = putFxRateOverrideUseCase;
        this.resolveExchangeRateUseCase = resolveExchangeRateUseCase;
    }

    @PutMapping("/config")
    public ResponseEntity<TenantFxConfigResult> putConfig(
            @PathVariable UUID tenantId,
            @Valid @RequestBody FxConfigRequest request
    ) {
        TenantFxConfigResult result = configureTenantFxUseCase.execute(
                new ConfigureTenantFxCommand(
                        tenantId,
                        request.pivotCurrencyCode(),
                        request.spreadBps(),
                        request.supportedCurrencyCodes()
                )
        );
        return ResponseEntity.ok(result);
    }

    @PutMapping("/overrides")
    public ResponseEntity<Void> putOverride(
            @PathVariable UUID tenantId,
            @Valid @RequestBody FxOverrideRequest request
    ) {
        putFxRateOverrideUseCase.execute(new PutFxRateOverrideCommand(
                tenantId,
                request.baseCurrencyCode(),
                request.quoteCurrencyCode(),
                request.rate(),
                request.validFrom(),
                request.validTo()
        ));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/rates")
    public ResponseEntity<ExchangeRateResult> getRate(
            @PathVariable UUID tenantId,
            @RequestParam String base,
            @RequestParam String quote,
            @RequestParam(required = false) Instant asOf
    ) {
        return ResponseEntity.ok(resolveExchangeRateUseCase.execute(tenantId, base, quote, asOf));
    }

    public record FxConfigRequest(
            @NotBlank String pivotCurrencyCode,
            int spreadBps,
            @NotEmpty List<@NotBlank String> supportedCurrencyCodes
    ) {
    }

    public record FxOverrideRequest(
            @NotBlank String baseCurrencyCode,
            @NotBlank String quoteCurrencyCode,
            @NotNull @Positive BigDecimal rate,
            @NotNull Instant validFrom,
            @NotNull Instant validTo
    ) {
    }
}
