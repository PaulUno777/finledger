package com.pauluno.finledger.application.usecase;

import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.ConfigureTenantFxCommand;
import com.pauluno.finledger.application.dto.TenantFxConfigResult;
import com.pauluno.finledger.application.port.in.ConfigureTenantFxUseCase;
import com.pauluno.finledger.application.port.out.TenantFxConfigRepository;
import com.pauluno.finledger.domain.model.TenantFxConfig;

@Service
public class ConfigureTenantFxService implements ConfigureTenantFxUseCase {

    private final TenantFxConfigRepository tenantFxConfigRepository;

    public ConfigureTenantFxService(TenantFxConfigRepository tenantFxConfigRepository) {
        this.tenantFxConfigRepository = tenantFxConfigRepository;
    }

    @Override
    @Transactional
    @Auditable(action = "CONFIGURE_FX", resourceType = "TENANT_FX_CONFIG")
    public TenantFxConfigResult execute(ConfigureTenantFxCommand command) {
        Currency pivot = Currency.getInstance(command.pivotCurrencyCode());
        Set<Currency> supported = new LinkedHashSet<>();
        for (String code : command.supportedCurrencyCodes()) {
            supported.add(Currency.getInstance(code));
        }
        TenantFxConfig saved = tenantFxConfigRepository.save(
                new TenantFxConfig(command.tenantId(), pivot, command.spreadBps(), supported));
        return toResult(saved);
    }

    static TenantFxConfigResult toResult(TenantFxConfig config) {
        List<String> codes = config.supportedCurrencies().stream()
                .map(Currency::getCurrencyCode)
                .sorted()
                .collect(Collectors.toList());
        return new TenantFxConfigResult(
                config.tenantId(),
                config.pivotCurrency().getCurrencyCode(),
                config.spreadBps(),
                codes
        );
    }
}
