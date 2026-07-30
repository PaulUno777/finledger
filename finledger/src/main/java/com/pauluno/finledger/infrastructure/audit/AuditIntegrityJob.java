package com.pauluno.finledger.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.dto.AuditIntegrityResult;
import com.pauluno.finledger.application.port.in.VerifyAuditChainUseCase;
import com.pauluno.finledger.application.port.out.AuditLogRepository;
import com.pauluno.finledger.application.tenant.TenantContext;

@Component
public class AuditIntegrityJob {

    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityJob.class);

    private final AuditLogRepository auditLogRepository;
    private final VerifyAuditChainUseCase verifyAuditChainUseCase;

    public AuditIntegrityJob(
            AuditLogRepository auditLogRepository,
            VerifyAuditChainUseCase verifyAuditChainUseCase
    ) {
        this.auditLogRepository = auditLogRepository;
        this.verifyAuditChainUseCase = verifyAuditChainUseCase;
    }

    @Scheduled(fixedDelayString = "${finledger.audit.integrity-interval-ms:3600000}")
    public void run() {
        TenantContext.enableBypass();
        try {
            for (var tenantId : auditLogRepository.findTenantIdsWithAuditActivity()) {
                TenantContext.set(tenantId);
                try {
                    AuditIntegrityResult result = verifyAuditChainUseCase.execute(tenantId);
                    if (!result.valid()) {
                        log.error(
                                "Audit chain integrity failure tenantId={} breakAt={} checkedCount={}",
                                tenantId, result.breakAt(), result.checkedCount());
                    }
                } finally {
                    TenantContext.clear();
                    TenantContext.enableBypass();
                }
            }
        } finally {
            TenantContext.clear();
        }
    }
}
