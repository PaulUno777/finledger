package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantAncestryEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.TenantAncestryId;

public interface SpringDataTenantAncestryRepository
        extends JpaRepository<TenantAncestryEntity, TenantAncestryId> {

    @Query("select a.ancestorId from TenantAncestryEntity a where a.descendantId = :descendantId")
    List<UUID> findAncestorIds(@Param("descendantId") UUID descendantId);

    @Query("select a.descendantId from TenantAncestryEntity a where a.ancestorId = :ancestorId")
    List<UUID> findDescendantIds(@Param("ancestorId") UUID ancestorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TenantAncestryEntity a where a.descendantId = :descendantId")
    void deleteByDescendantId(@Param("descendantId") UUID descendantId);
}
