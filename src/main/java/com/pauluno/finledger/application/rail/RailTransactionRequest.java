package com.pauluno.finledger.application.rail;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/**
 * Request to initiate a payment on an external rail (plan §7).
 */
public record RailTransactionRequest(
        UUID tenantId,
        String railCode,
        BigDecimal amount,
        Currency currency,
        UUID clearingAccountId,
        UUID counterpartyAccountId,
        String clientReference
) {
}
