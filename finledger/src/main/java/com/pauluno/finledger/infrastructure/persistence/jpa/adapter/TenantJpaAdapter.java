package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.domain.model.Tenant;
import com.pauluno.finledger.domain.model.TenantType;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantAncestryEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataTenantAncestryRepository;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataTenantRepository;

@Component
public class TenantJpaAdapter implements TenantRepository {

    private final SpringDataTenantRepository tenants;
    private final SpringDataTenantAncestryRepository ancestry;
    private final Clock clock;

    public TenantJpaAdapter(
            SpringDataTenantRepository tenants,
            SpringDataTenantAncestryRepository ancestry
    ) {
        this.tenants = Objects.requireNonNull(tenants, "tenants");
        this.ancestry = Objects.requireNonNull(ancestry, "ancestry");
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional
    public Tenant save(Tenant tenant) {
        TenantEntity entity = new TenantEntity();
        entity.setId(tenant.id());
        entity.setTenantType(tenant.type().name());
        entity.setParentTenantId(tenant.parentTenantId());
        entity.setName(tenant.name());
        entity.setCreatedAt(clock.instant());
        return toDomain(tenants.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> findById(UUID id) {
        return tenants.findById(id).map(TenantJpaAdapter::toDomain);
    }

    @Override
    @Transactional
    public void replaceAncestry(UUID tenantId, List<UUID> ancestorIdsIncludingSelf) {
        ancestry.deleteByDescendantId(tenantId);
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(ancestorIdsIncludingSelf);
        unique.add(tenantId);
        for (UUID ancestorId : unique) {
            ancestry.save(new TenantAncestryEntity(ancestorId, tenantId));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findAncestorIds(UUID tenantId) {
        return ancestry.findAncestorIds(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDescendantIds(UUID tenantId) {
        return ancestry.findDescendantIds(tenantId);
    }

    private static Tenant toDomain(TenantEntity entity) {
        return new Tenant(
                entity.getId(),
                TenantType.valueOf(entity.getTenantType()),
                entity.getParentTenantId(),
                entity.getName()
        );
    }
}
