package com.pauluno.finledger.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.Tenant;

public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(UUID id);

    /**
     * Replaces ancestry rows for {@code tenantId}: always includes (self, self);
     * for sub-merchants also copies the parent's ancestor set onto this descendant.
     */
    void replaceAncestry(UUID tenantId, List<UUID> ancestorIdsIncludingSelf);

    List<UUID> findAncestorIds(UUID tenantId);

    List<UUID> findDescendantIds(UUID tenantId);
}
