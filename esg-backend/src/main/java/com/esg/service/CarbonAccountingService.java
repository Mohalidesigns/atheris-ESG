package com.esg.service;

import com.esg.model.dto.EmissionRecordDto;
import com.esg.model.entity.EmissionFactor;
import com.esg.model.entity.EmissionRecord;
import com.esg.repository.EmissionFactorRepository;
import com.esg.repository.EmissionRecordRepository;
import com.esg.exception.FieldValidationException;
import com.esg.security.OrgScopeService;
import com.esg.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CarbonAccountingService {

    public CarbonAccountingService(EmissionRecordRepository emissionRecordRepository, EmissionFactorRepository emissionFactorRepository,
                                   OrgScopeService orgScope, ReportingPeriodService periods,
                                   com.esg.audit.LineageService lineage) {
        this.orgScope = orgScope;
        this.periods = periods;
        this.lineage = lineage;
        this.emissionRecordRepository = emissionRecordRepository;
        this.emissionFactorRepository = emissionFactorRepository;
    }

    private final EmissionRecordRepository emissionRecordRepository;
    private final EmissionFactorRepository emissionFactorRepository;
    private final OrgScopeService orgScope;
    private final ReportingPeriodService periods;
    private final com.esg.audit.LineageService lineage;

    public Page<EmissionRecord> getEmissions(String scope, Pageable pageable) {
        OrgScopeService.QueryScope orgs = orgScope.queryScope();
        String scopeFilter = scope == null || scope.isBlank() || scope.equalsIgnoreCase("all") ? null : scope;
        return emissionRecordRepository.findScoped(TenantContext.requireTenantId(), scopeFilter, orgs.all(),
                orgs.orgIds(), pageable);
    }

    /**
     * CO2e = Activity Data x Emission Factor x GWP
     * Per TRD Section 8.2
     */
    @Transactional
    public EmissionRecord calculateAndSave(EmissionRecordDto dto) {
        UUID tenantId = TenantContext.requireTenantId();
        orgScope.requireAccess(dto.getOrgId());
        periods.requireOpen(dto.getReportingPeriodStart(), dto.getReportingPeriodEnd());

        BigDecimal co2eTonnes = dto.getCo2eTonnes();

        // Auto-calculate if emission factor is specified
        if (dto.getEmissionFactorId() != null && co2eTonnes == null) {
            // RLS limits this to global factors and the tenant's own custom factors (AS-IS S8)
            EmissionFactor factor = emissionFactorRepository.findById(dto.getEmissionFactorId())
                    .orElseThrow(() -> FieldValidationException.of("emissionFactorId", "Unknown emission factor"));

            co2eTonnes = com.esg.reference.EmissionCalculator.co2eTonnes(dto.getActivityData(), factor.getFactorValue());
        }

        if (co2eTonnes == null) {
            throw FieldValidationException.of("emissionFactorId", "Either co2eTonnes or emissionFactorId must be provided");
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

        EmissionRecord saved = emissionRecordRepository.save(record);
        if (saved.getEmissionFactorId() != null) {
            // TRD §6.6.2: emission calculations link back to the factor they used.
            lineage.record("emission_factor", saved.getEmissionFactorId(), "emission_record", saved.getId(), "calculated_from",
                    java.util.Map.of("activityData", saved.getActivityData(), "activityUnit", saved.getActivityUnit(),
                            "co2eTonnes", saved.getCo2eTonnes()));
        }
        return saved;
    }

    public Map<String, Object> getEmissionsSummary() {
        UUID tenantId = TenantContext.requireTenantId();
        OrgScopeService.QueryScope orgs = orgScope.queryScope();
        Map<String, Object> summary = new HashMap<>();

        BigDecimal scope1 = emissionRecordRepository.sumCo2eScoped(tenantId, "scope_1", orgs.all(), orgs.orgIds());
        BigDecimal scope2 = emissionRecordRepository.sumCo2eScoped(tenantId, "scope_2", orgs.all(), orgs.orgIds());
        BigDecimal scope3 = emissionRecordRepository.sumCo2eScoped(tenantId, "scope_3", orgs.all(), orgs.orgIds());
        BigDecimal total = scope1.add(scope2).add(scope3);

        summary.put("scope1Total", scope1);
        summary.put("scope2Total", scope2);
        summary.put("scope3Total", scope3);
        summary.put("totalCO2e", total);

        return summary;
    }
}
