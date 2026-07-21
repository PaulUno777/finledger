package com.pauluno.finledger.domain.audit;

public interface AuditRepository {
    void save(AuditLog log);
}
