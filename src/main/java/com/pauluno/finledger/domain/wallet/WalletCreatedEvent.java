package com.pauluno.finledger.domain.wallet;

import java.time.Instant;
import java.util.UUID;

import com.pauluno.finledger.domain.events.DomainEvent;

public record WalletCreatedEvent(

        UUID walletId,

        Instant occurredAt

) implements DomainEvent {
}
