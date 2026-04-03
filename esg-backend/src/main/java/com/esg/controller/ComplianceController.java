package com.esg.controller;

import com.esg.model.dto.ApiResponse;
import com.esg.model.entity.RegulatoryFramework;
import com.esg.service.ComplianceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/compliance")
public class ComplianceController {

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    private final ComplianceService complianceService;

    @GetMapping("/frameworks")
    public ResponseEntity<ApiResponse<List<RegulatoryFramework>>> getFrameworks(
            @RequestParam(required = false) String jurisdiction) {
        List<RegulatoryFramework> frameworks = jurisdiction != null
                ? complianceService.getFrameworksByJurisdiction(jurisdiction)
                : complianceService.getFrameworks();
        return ResponseEntity.ok(ApiResponse.ok(frameworks));
    }

    @GetMapping("/frameworks/{code}")
    public ResponseEntity<ApiResponse<RegulatoryFramework>> getFramework(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(complianceService.getFrameworkByCode(code)));
    }

    @GetMapping("/gaps")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGapAnalysis(
            @RequestParam String framework,
            @RequestParam(defaultValue = "2026") int year) {
        return ResponseEntity.ok(ApiResponse.ok(complianceService.runGapAnalysis(framework, year)));
    }
}
