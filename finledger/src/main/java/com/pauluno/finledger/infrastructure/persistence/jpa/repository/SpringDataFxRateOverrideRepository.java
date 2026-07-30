package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.FxRateOverrideEntity;

public interface SpringDataFxRateOverrideRepository extends JpaRepository<FxRateOverrideEntity, UUID> {

    @Query("""
            select o from FxRateOverrideEntity o
            where o.tenantId = :tenantId
              and o.baseCurrency = :base
              and o.quoteCurrency = :quote
              and o.validFrom <= :asOf
              and o.validTo > :asOf
            order by o.validFrom desc
            """)
    List<FxRateOverrideEntity> findActive(
            @Param("tenantId") UUID tenantId,
            @Param("base") String base,
            @Param("quote") String quote,
            @Param("asOf") Instant asOf
    );
}
