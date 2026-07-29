package com.pauluno.finledger.domain.events;

import java.time.Instant;

public interface DomainEvent {

    Instant occurredAt();

}
