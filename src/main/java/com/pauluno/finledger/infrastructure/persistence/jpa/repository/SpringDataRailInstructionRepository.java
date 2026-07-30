package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.RailInstructionEntity;

public interface SpringDataRailInstructionRepository extends JpaRepository<RailInstructionEntity, UUID> {

    Optional<RailInstructionEntity> findByTenantIdAndRailReference(UUID tenantId, String railReference);

    Optional<RailInstructionEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<RailInstructionEntity> findByTenantIdAndRailReferenceIn(UUID tenantId, Collection<String> railReferences);
}
