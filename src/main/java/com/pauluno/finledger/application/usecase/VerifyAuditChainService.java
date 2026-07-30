package com.pauluno.finledger.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.dto.AuditIntegrityResult;
import com.pauluno.finledger.application.port.in.VerifyAuditChainUseCase;
import com.pauluno.finledger.application.port.out.AuditLogRepository;
import com.pauluno.finledger.domain.audit.AuditHashChain;

@Service
public class VerifyAuditChainService implements VerifyAuditChainUseCase {

    private final AuditLogRepository auditLogRepository;

    public VerifyAuditChainService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AuditIntegrityResult execute(UUID tenantId) {
        List<AuditHashChain.AuditChainLink> links = auditLogRepository.findAllOrdered(tenantId).stream()
                .map(e -> new AuditHashChain.AuditChainLink(
                        e.payload(),
                        e.payloadHash(),
                        e.prevHash(),
                        e.currentHash(),
                        e.occurredAt(),
                        e.actor()))
                .toList();
        AuditHashChain.AuditChainVerification verification = AuditHashChain.verify(links);
        return new AuditIntegrityResult(
                verification.valid(),
                verification.checkedCount(),
                verification.breakAt()
        );
    }
}
