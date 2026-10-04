package com.esg.repository;

import com.esg.model.entity.DataPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface DataPointRepository extends JpaRepository<DataPoint, UUID> {
    Page<DataPoint> findByTenantId(UUID tenantId, Pageable pageable);
    Page<DataPoint> findByTenantIdAndCategory(UUID tenantId, String category, Pageable pageable);
    Page<DataPoint> findByTenantIdAndValidationStatus(UUID tenantId, String status, Pageable pageable);
    List<DataPoint> findByTenantIdAndOrgId(UUID tenantId, UUID orgId);
    Optional<DataPoint> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Filtered list; category/status are optional, and allOrgs=false restricts to orgIds (org-scoped roles). */
    @Query("select d from DataPoint d where d.tenantId = :tenantId"
            + " and (:category is null or d.category = :category)"
            + " and (:status is null or d.validationStatus = :status)"
            + " and (:allOrgs = true or d.orgId in :orgIds)")
    Page<DataPoint> findScoped(@Param("tenantId") UUID tenantId, @Param("category") String category,
                               @Param("status") String status, @Param("allOrgs") boolean allOrgs,
                               @Param("orgIds") java.util.Collection<UUID> orgIds, Pageable pageable);
    /** WP-20 list: every filter optional; soft-deleted points excluded; allOrgs=false restricts to orgIds. */
    @Query("select d from DataPoint d where d.tenantId = :tenantId and d.deletedAt is null"
            + " and (:category is null or d.category = :category)"
            + " and (:status is null or d.validationStatus = :status)"
            + " and (:metricKey is null or d.metricKey = :metricKey)"
            + " and (:orgId is null or d.orgId = :orgId)"
            + " and (:sourceId is null or d.sourceId = :sourceId)"
            + " and (cast(:fromDate as localdate) is null or d.reportingPeriodEnd >= :fromDate)"
            + " and (cast(:toDate as localdate) is null or d.reportingPeriodStart <= :toDate)"
            + " and (:importJobId is null or d.importJobId = :importJobId)"
            + " and (:allOrgs = true or d.orgId in :orgIds)")
    Page<DataPoint> findFiltered(@Param("tenantId") UUID tenantId, @Param("category") String category,
                                 @Param("status") String status, @Param("metricKey") String metricKey,
                                 @Param("orgId") UUID orgId, @Param("sourceId") UUID sourceId,
                                 @Param("fromDate") java.time.LocalDate from, @Param("toDate") java.time.LocalDate to,
                                 @Param("importJobId") UUID importJobId, @Param("allOrgs") boolean allOrgs,
                                 @Param("orgIds") java.util.Collection<UUID> orgIds, Pageable pageable);
    long countByTenantId(UUID tenantId);
    long countByTenantIdAndValidationStatus(UUID tenantId, String status);
}
