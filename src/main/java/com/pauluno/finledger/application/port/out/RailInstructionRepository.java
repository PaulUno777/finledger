package com.pauluno.finledger.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.application.rail.RailInstruction;

public interface RailInstructionRepository {

    RailInstruction save(RailInstruction instruction);

    Optional<RailInstruction> findByTenantAndReference(UUID tenantId, String railReference);

    Optional<RailInstruction> findByTenantAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<RailInstruction> findByTenantAndReferences(UUID tenantId, List<String> railReferences);
}
