package com.esg.service;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.model.dto.DataPointDto;
import com.esg.model.entity.DataPoint;
import com.esg.repository.DataPointRepository;
import com.esg.security.OrgScopeService;
import com.esg.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Service
public class DataHubService {

    /** Outcomes a reviewer may record (WP-13 interim; the full workflow states arrive with WP-20). */
    static final Set<String> REVIEW_STATUSES = Set.of("validated", "rejected", "flagged");

    public DataHubService(DataPointRepository dataPointRepository, OrgScopeService orgScope, ReportingPeriodService periods,
                          com.esg.reference.ReferenceService reference) {
        this.reference = reference;
        this.dataPointRepository = dataPointRepository;
        this.orgScope = orgScope;
        this.periods = periods;
    }

    private final DataPointRepository dataPointRepository;
    private final OrgScopeService orgScope;
    private final ReportingPeriodService periods;
    private final com.esg.reference.ReferenceService reference;

    /** Lists data points the caller may see: RLS limits to the tenant, org scope limits org-scoped roles. */
    public Page<DataPoint> getDataPoints(String category, String status, Pageable pageable) {
        OrgScopeService.QueryScope scope = orgScope.queryScope();
        return dataPointRepository.findScoped(TenantContext.requireTenantId(), blankToNull(category),
                blankToNull(status), scope.all(), scope.orgIds(), pageable);
    }

    @Transactional
    public DataPoint createDataPoint(DataPointDto dto, UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();
        orgScope.requireAccess(dto.getOrgId());
        com.esg.reference.ReferenceDtos.Metric metric = reference.metric(dto.getMetricKey()).orElse(null);
        java.util.Map<String, String> errors = DataPointRules.check(dto, metric);
        if (!errors.isEmpty()) {
            throw new com.esg.exception.FieldValidationException(errors);
        }
        dto.setCategory(metric.pillar());
        dto.setUnit(metric.unit());
        periods.requireOpen(dto.getReportingPeriodStart(), dto.getReportingPeriodEnd());

        // Calculate quality score
        BigDecimal qualityScore = calculateQualityScore(dto);

        DataPoint dp = new DataPoint();
        dp.setTenantId(tenantId);
        dp.setOrgId(dto.getOrgId());
        dp.setSourceId(dto.getSourceId());
        // Category and unit come from the metric catalogue (WP-17); the sub-category defaults to the metric's category.
        dp.setCategory(metric.pillar());
        dp.setSubCategory(dto.getSubCategory() == null || dto.getSubCategory().isBlank() ? metric.category() : dto.getSubCategory().trim());
        dp.setMetricKey(dto.getMetricKey());
        dp.setNumericValue(dto.getNumericValue());
        dp.setTextValue(dto.getTextValue());
        dp.setUnit(metric.unit());
        dp.setReportingPeriodStart(dto.getReportingPeriodStart());
        dp.setReportingPeriodEnd(dto.getReportingPeriodEnd());
        dp.setQualityScore(qualityScore);
        dp.setValidationStatus("pending");
        dp.setMetadata(dto.getMetadata());
        dp.setCreatedBy(userId);

        return dataPointRepository.save(dp);
    }

    /** Records a review outcome. Segregation of duties: nobody reviews their own data point. */
    @Transactional
    public DataPoint validateDataPoint(UUID id, String status, UUID userId) {
        if (status == null || !REVIEW_STATUSES.contains(status)) {
            throw FieldValidationException.of("status", "must be one of validated, rejected, flagged");
        }
        DataPoint dp = getDataPoint(id);
        periods.requireOpen(dp.getReportingPeriodStart(), dp.getReportingPeriodEnd());
        if (userId.equals(dp.getCreatedBy())) {
            throw ApiException.forbidden("You can't review a data point you created");
        }
        dp.setValidationStatus(status);
        dp.setValidatedBy(userId);
        dp.setValidatedAt(java.time.Instant.now());
        return dataPointRepository.save(dp);
    }

    /** Tenant-scoped (RLS + filter) and org-scoped lookup; out-of-scope rows look like they don't exist. */
    public DataPoint getDataPoint(UUID id) {
        DataPoint dp = dataPointRepository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> ApiException.notFound("Data point"));
        if (!orgScope.canAccess(dp.getOrgId())) {
            throw ApiException.notFound("Data point");
        }
        return dp;
    }

    /**
     * Quality Score = (0.3 * completeness) + (0.25 * consistency) + (0.25 * timeliness) + (0.2 * accuracy)
     * Per TRD Section 8.1
     */
    private BigDecimal calculateQualityScore(DataPointDto dto) {
        double completeness = 1.0;
        if (dto.getNumericValue() == null && dto.getTextValue() == null) completeness = 0.3;
        if (dto.getUnit() == null) completeness -= 0.2;

        double consistency = 0.8; // Default for new data points
        double timeliness = 1.0;  // Submitted within period
        double accuracy = 0.7;    // Default, increases with verification

        double score = (0.3 * completeness) + (0.25 * consistency) + (0.25 * timeliness) + (0.2 * accuracy);
        return BigDecimal.valueOf(Math.min(score, 1.0)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("all") ? null : value;
    }
}
