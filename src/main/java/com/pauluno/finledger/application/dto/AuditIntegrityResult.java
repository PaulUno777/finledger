package com.pauluno.finledger.application.dto;

public record AuditIntegrityResult(
        boolean valid,
        int checkedCount,
        Integer breakAt
) {
}
