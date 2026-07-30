package com.pauluno.finledger.application.usecase;

import java.util.Currency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.PutFxRateOverrideCommand;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.port.in.PutFxRateOverrideUseCase;
import com.pauluno.finledger.application.port.out.FxRateOverrideRepository;
import com.pauluno.finledger.application.port.out.TenantFxConfigRepository;
import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.TenantFxConfig;

@Service
public class PutFxRateOverrideService implements PutFxRateOverrideUseCase {

    private final FxRateOverrideRepository fxRateOverrideRepository;
    private final TenantFxConfigRepository tenantFxConfigRepository;

    public PutFxRateOverrideService(
            FxRateOverrideRepository fxRateOverrideRepository,
            TenantFxConfigRepository tenantFxConfigRepository
    ) {
        this.fxRateOverrideRepository = fxRateOverrideRepository;
        this.tenantFxConfigRepository = tenantFxConfigRepository;
    }

    @Override
    @Transactional
    @Auditable(action = "PUT_FX_OVERRIDE", resourceType = "FX_RATE_OVERRIDE")
    public void execute(PutFxRateOverrideCommand command) {
        if (!command.validTo().isAfter(command.validFrom())) {
            throw new BusinessRuleException("INVALID_FX_OVERRIDE", "validTo must be after validFrom");
        }
        if (command.rate().signum() <= 0) {
            throw new BusinessRuleException("INVALID_FX_OVERRIDE", "rate must be positive");
        }
        TenantFxConfig config = tenantFxConfigRepository.findByTenantId(command.tenantId())
                .orElseThrow(() -> new BusinessRuleException(
                        "FX_CONFIG_REQUIRED",
                        "Configure tenant FX before saving rate overrides"));
        Currency base = Currency.getInstance(command.baseCurrencyCode());
        Currency quote = Currency.getInstance(command.quoteCurrencyCode());
        if (!config.supports(base) || !config.supports(quote)) {
            throw new BusinessRuleException(
                    "UNSUPPORTED_CURRENCY",
                    "Override currencies must be in tenant supported set");
        }
        fxRateOverrideRepository.saveOverride(
                command.tenantId(),
                CurrencyPair.of(base, quote),
                command.rate(),
                command.validFrom(),
                command.validTo()
        );
    }
}
