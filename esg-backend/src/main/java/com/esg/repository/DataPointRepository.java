package com.esg.repository;

import com.esg.model.entity.DataPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface DataPointRepository extends JpaRepository<DataPoint, UUID> {
    Page<DataPoint> findByTenantId(UUID tenantId, Pageable pageable);
    Page<DataPoint> findByTenantIdAndCategory(UUID tenantId, String category, Pageable pageable);
    Page<DataPoint> findByTenantIdAndValidationStatus(UUID tenantId, String status, Pageable pageable);
    List<DataPoint> findByTenantIdAndOrgId(UUID tenantId, UUID orgId);
    Optional<DataPoint> findByIdAndTenantId(UUID id, UUID tenantId);
    long countByTenantId(UUID tenantId);
    long countByTenantIdAndValidationStatus(UUID tenantId, String status);
}
