package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record CreateTenantCommand(
        String name,
        String type,
        UUID parentTenantId
) {
}
