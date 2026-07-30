package com.pauluno.finledger.application.usecase;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.dto.ExchangeRateResult;
import com.pauluno.finledger.application.port.in.ResolveExchangeRateUseCase;
import com.pauluno.finledger.application.port.out.ExchangeRateProvider;
import com.pauluno.finledger.domain.model.CurrencyPair;
import com.pauluno.finledger.domain.model.ExchangeRate;

@Service
public class ResolveExchangeRateService implements ResolveExchangeRateUseCase {

    private final ExchangeRateProvider exchangeRateProvider;

    public ResolveExchangeRateService(ExchangeRateProvider exchangeRateProvider) {
        this.exchangeRateProvider = exchangeRateProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public ExchangeRateResult execute(UUID tenantId, String baseCurrency, String quoteCurrency, Instant asOf) {
        Instant when = asOf == null ? Instant.now() : asOf;
        ExchangeRate rate = exchangeRateProvider.getRate(
                tenantId,
                CurrencyPair.of(Currency.getInstance(baseCurrency), Currency.getInstance(quoteCurrency)),
                when
        );
        return new ExchangeRateResult(
                rate.pair().base().getCurrencyCode(),
                rate.pair().quote().getCurrencyCode(),
                rate.rate(),
                rate.source().name(),
                rate.asOf(),
                rate.stale()
        );
    }
}
