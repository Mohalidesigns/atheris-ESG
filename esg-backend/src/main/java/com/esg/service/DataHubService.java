package com.esg.service;

import com.esg.model.dto.DataPointDto;
import com.esg.model.entity.DataPoint;
import com.esg.repository.DataPointRepository;
import com.esg.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class DataHubService {

    public DataHubService(DataPointRepository dataPointRepository) {
        this.dataPointRepository = dataPointRepository;
    }

    private final DataPointRepository dataPointRepository;

    public Page<DataPoint> getDataPoints(String category, String status, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        if (category != null && !category.equalsIgnoreCase("all")) {
            return dataPointRepository.findByTenantIdAndCategory(tenantId, category, pageable);
        }
        if (status != null && !status.equalsIgnoreCase("all")) {
            return dataPointRepository.findByTenantIdAndValidationStatus(tenantId, status, pageable);
        }
        return dataPointRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional
    public DataPoint createDataPoint(DataPointDto dto, UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();

        // Calculate quality score
        BigDecimal qualityScore = calculateQualityScore(dto);

        DataPoint dp = new DataPoint();
        dp.setTenantId(tenantId);
        dp.setOrgId(dto.getOrgId());
        dp.setSourceId(dto.getSourceId());
        dp.setCategory(dto.getCategory());
        dp.setSubCategory(dto.getSubCategory());
        dp.setMetricKey(dto.getMetricKey());
        dp.setNumericValue(dto.getNumericValue());
        dp.setTextValue(dto.getTextValue());
        dp.setUnit(dto.getUnit());
        dp.setReportingPeriodStart(dto.getReportingPeriodStart());
        dp.setReportingPeriodEnd(dto.getReportingPeriodEnd());
        dp.setQualityScore(qualityScore);
        dp.setValidationStatus("pending");
        dp.setMetadata(dto.getMetadata());
        dp.setCreatedBy(userId);

        return dataPointRepository.save(dp);
    }

    @Transactional
    public DataPoint validateDataPoint(UUID id, String status, UUID userId) {
        DataPoint dp = getDataPoint(id);
        dp.setValidationStatus(status);
        dp.setValidatedBy(userId);
        dp.setValidatedAt(java.time.Instant.now());
        return dataPointRepository.save(dp);
    }

    /** Tenant-scoped lookup (AS-IS S7); RLS also hides other tenants' rows. */
    public DataPoint getDataPoint(UUID id) {
        return dataPointRepository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new RuntimeException("Data point not found: " + id));
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
}
