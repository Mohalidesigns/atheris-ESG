package com.esg.repository;

import com.esg.model.entity.EmissionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface EmissionRecordRepository extends JpaRepository<EmissionRecord, UUID> {
    Page<EmissionRecord> findByTenantId(UUID tenantId, Pageable pageable);
    Page<EmissionRecord> findByTenantIdAndScope(UUID tenantId, String scope, Pageable pageable);
    List<EmissionRecord> findByTenantIdAndOrgId(UUID tenantId, UUID orgId);

    @Query("select e from EmissionRecord e where e.tenantId = :tenantId and (:scope is null or e.scope = :scope)"
            + " and (:allOrgs = true or e.orgId in :orgIds)")
    Page<EmissionRecord> findScoped(@Param("tenantId") UUID tenantId, @Param("scope") String scope,
                                    @Param("allOrgs") boolean allOrgs, @Param("orgIds") java.util.Collection<UUID> orgIds,
                                    Pageable pageable);

    @Query("select coalesce(sum(e.co2eTonnes), 0) from EmissionRecord e where e.tenantId = :tenantId and e.scope = :scope"
            + " and (:allOrgs = true or e.orgId in :orgIds)")
    BigDecimal sumCo2eScoped(@Param("tenantId") UUID tenantId, @Param("scope") String scope,
                             @Param("allOrgs") boolean allOrgs, @Param("orgIds") java.util.Collection<UUID> orgIds);
}
