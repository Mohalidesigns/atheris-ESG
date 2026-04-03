package com.esg.repository;

import com.esg.model.entity.EmissionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;

public interface EmissionRecordRepository extends JpaRepository<EmissionRecord, String> {
    Page<EmissionRecord> findByTenantId(String tenantId, Pageable pageable);
    Page<EmissionRecord> findByTenantIdAndScope(String tenantId, String scope, Pageable pageable);
    List<EmissionRecord> findByTenantIdAndOrgId(String tenantId, String orgId);

    @Query("SELECT COALESCE(SUM(e.co2eTonnes), 0) FROM EmissionRecord e WHERE e.tenantId = ?1 AND e.scope = ?2")
    BigDecimal sumCo2eByTenantAndScope(String tenantId, String scope);
}
