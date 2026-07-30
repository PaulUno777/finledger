package com.pauluno.finledger.application.port.out;

import com.pauluno.finledger.application.audit.AuditRecord;

public interface AuditLogWriter {

    void append(AuditRecord record);
}
