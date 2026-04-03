package com.esg.service;

import com.esg.model.entity.RegulatoryFramework;
import com.esg.repository.RegulatoryFrameworkRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ComplianceService {

    public ComplianceService(RegulatoryFrameworkRepository frameworkRepository) {
        this.frameworkRepository = frameworkRepository;
    }

    private final RegulatoryFrameworkRepository frameworkRepository;

    public List<RegulatoryFramework> getFrameworks() {
        return frameworkRepository.findByIsActiveTrue();
    }

    public RegulatoryFramework getFrameworkByCode(String code) {
        return frameworkRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Framework not found: " + code));
    }

    public List<RegulatoryFramework> getFrameworksByJurisdiction(String jurisdiction) {
        return frameworkRepository.findByJurisdiction(jurisdiction);
    }

    /**
     * Gap analysis engine per TRD Section 8.3
     * Returns compliance status for a given framework
     */
    public Map<String, Object> runGapAnalysis(String frameworkCode, int reportingYear) {
        RegulatoryFramework framework = getFrameworkByCode(frameworkCode);

        // In production this would cross-reference framework_requirements
        // with tenant_compliance_status and data_points
        return Map.of(
                "framework", framework.getCode(),
                "frameworkName", framework.getName(),
                "reportingYear", reportingYear,
                "totalRequirements", 85,
                "completed", 52,
                "inProgress", 18,
                "gaps", 15,
                "overallCompleteness", 61.2,
                "criticalGaps", List.of(
                        Map.of("req", "S1.26", "title", "Scope 3 GHG emissions", "priority", "HIGH"),
                        Map.of("req", "S2.29", "title", "Climate scenario analysis", "priority", "HIGH"),
                        Map.of("req", "S1.38", "title", "Transition plan disclosures", "priority", "MEDIUM")
                )
        );
    }
}
