package com.esg.service;

import com.esg.model.dto.EmissionRecordDto;
import com.esg.model.entity.EmissionFactor;
import com.esg.model.entity.EmissionRecord;
import com.esg.repository.EmissionFactorRepository;
import com.esg.repository.EmissionRecordRepository;
import com.esg.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CarbonAccountingService {

    public CarbonAccountingService(EmissionRecordRepository emissionRecordRepository, EmissionFactorRepository emissionFactorRepository) {
        this.emissionRecordRepository = emissionRecordRepository;
        this.emissionFactorRepository = emissionFactorRepository;
    }

    private final EmissionRecordRepository emissionRecordRepository;
    private final EmissionFactorRepository emissionFactorRepository;

    public Page<EmissionRecord> getEmissions(String scope, Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        if (scope != null && !scope.equalsIgnoreCase("all")) {
            return emissionRecordRepository.findByTenantIdAndScope(tenantId, scope, pageable);
        }
        return emissionRecordRepository.findByTenantId(tenantId, pageable);
    }

    /**
     * CO2e = Activity Data x Emission Factor x GWP
     * Per TRD Section 8.2
     */
    @Transactional
    public EmissionRecord calculateAndSave(EmissionRecordDto dto) {
        String tenantId = TenantContext.getTenantId();

        BigDecimal co2eTonnes = dto.getCo2eTonnes();

        // Auto-calculate if emission factor is specified
        if (dto.getEmissionFactorId() != null && co2eTonnes == null) {
            EmissionFactor factor = emissionFactorRepository.findById(dto.getEmissionFactorId())
                    .orElseThrow(() -> new RuntimeException("Emission factor not found"));

            // CO2e (kg) = activity_data * factor_value
            BigDecimal co2eKg = dto.getActivityData().multiply(factor.getFactorValue());
            // Convert to tonnes
            co2eTonnes = co2eKg.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        }

        if (co2eTonnes == null) {
            throw new RuntimeException("Either co2eTonnes or emissionFactorId must be provided");
        }

        EmissionRecord record = new EmissionRecord();
        record.setTenantId(tenantId);
        record.setOrgId(dto.getOrgId());
        record.setScope(dto.getScope());
        record.setScope3Category(dto.getScope3Category());
        record.setSourceActivity(dto.getSourceActivity());
        record.setActivityData(dto.getActivityData());
        record.setActivityUnit(dto.getActivityUnit());
        record.setEmissionFactorId(dto.getEmissionFactorId());
        record.setCo2eTonnes(co2eTonnes);
        record.setReportingPeriodStart(dto.getReportingPeriodStart());
        record.setReportingPeriodEnd(dto.getReportingPeriodEnd());
        record.setCalculationMethod(dto.getCalculationMethod());

        return emissionRecordRepository.save(record);
    }

    public Map<String, Object> getEmissionsSummary() {
        String tenantId = TenantContext.getTenantId();
        Map<String, Object> summary = new HashMap<>();

        BigDecimal scope1 = emissionRecordRepository.sumCo2eByTenantAndScope(tenantId, "scope_1");
        BigDecimal scope2 = emissionRecordRepository.sumCo2eByTenantAndScope(tenantId, "scope_2");
        BigDecimal scope3 = emissionRecordRepository.sumCo2eByTenantAndScope(tenantId, "scope_3");
        BigDecimal total = scope1.add(scope2).add(scope3);

        summary.put("scope1Total", scope1);
        summary.put("scope2Total", scope2);
        summary.put("scope3Total", scope3);
        summary.put("totalCO2e", total);

        return summary;
    }

    public List<EmissionFactor> getEmissionFactors(String region) {
        if (region != null) {
            return emissionFactorRepository.findByRegion(region);
        }
        return emissionFactorRepository.findAll();
    }
}
