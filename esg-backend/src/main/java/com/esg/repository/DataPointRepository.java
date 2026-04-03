package com.esg.repository;

import com.esg.model.entity.DataPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DataPointRepository extends JpaRepository<DataPoint, String> {
    Page<DataPoint> findByTenantId(String tenantId, Pageable pageable);
    Page<DataPoint> findByTenantIdAndCategory(String tenantId, String category, Pageable pageable);
    Page<DataPoint> findByTenantIdAndValidationStatus(String tenantId, String status, Pageable pageable);
    List<DataPoint> findByTenantIdAndOrgId(String tenantId, String orgId);
    long countByTenantId(String tenantId);
    long countByTenantIdAndValidationStatus(String tenantId, String status);
}
