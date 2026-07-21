package com.pauluno.finledger.domain.audit;

import java.time.Instant;

public record AuditLog(

        String actor,

        AuditAction action,

        String resource,

        Instant timestamp

) {
}
