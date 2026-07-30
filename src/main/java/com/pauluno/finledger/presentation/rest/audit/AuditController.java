package com.pauluno.finledger.presentation.rest.audit;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.AuditIntegrityResult;
import com.pauluno.finledger.application.port.in.VerifyAuditChainUseCase;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/audit")
public class AuditController {

    private final VerifyAuditChainUseCase verifyAuditChainUseCase;

    public AuditController(VerifyAuditChainUseCase verifyAuditChainUseCase) {
        this.verifyAuditChainUseCase = verifyAuditChainUseCase;
    }

    @GetMapping("/integrity")
    public ResponseEntity<AuditIntegrityResult> integrity(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(verifyAuditChainUseCase.execute(tenantId));
    }
}
