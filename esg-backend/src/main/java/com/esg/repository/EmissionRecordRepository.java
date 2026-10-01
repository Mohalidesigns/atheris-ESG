package com.esg.repository;

import com.esg.model.entity.EmissionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;

public interface EmissionRecordRepository extends JpaRepository<EmissionRecord, UUID> {
    Page<EmissionRecord> findByTenantId(UUID tenantId, Pageable pageable);
    Page<EmissionRecord> findByTenantIdAndScope(UUID tenantId, String scope, Pageable pageable);
    List<EmissionRecord> findByTenantIdAndOrgId(UUID tenantId, UUID orgId);

    @Query("SELECT COALESCE(SUM(e.co2eTonnes), 0) FROM EmissionRecord e WHERE e.tenantId = ?1 AND e.scope = ?2")
    BigDecimal sumCo2eByTenantAndScope(UUID tenantId, String scope);
}
