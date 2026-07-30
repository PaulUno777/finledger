package com.pauluno.finledger.application.port.in;

import java.util.UUID;

import com.pauluno.finledger.application.dto.AuditIntegrityResult;

public interface VerifyAuditChainUseCase {

    AuditIntegrityResult execute(UUID tenantId);
}
