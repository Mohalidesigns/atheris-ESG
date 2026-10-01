package com.esg.controller;

import com.esg.model.dto.ApiResponse;
import com.esg.reference.FrameworkCatalogService;
import com.esg.reference.ReferenceDtos;
import com.esg.service.ComplianceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/compliance")
public class ComplianceController {

    public ComplianceController(ComplianceService complianceService, FrameworkCatalogService catalog) {
        this.complianceService = complianceService;
        this.catalog = catalog;
    }

    private final ComplianceService complianceService;
    private final FrameworkCatalogService catalog;

    @GetMapping("/frameworks")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Framework>>> getFrameworks(
            @RequestParam(required = false) String jurisdiction) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.frameworks(jurisdiction)));
    }

    @GetMapping("/frameworks/{code}")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<ReferenceDtos.Framework>> getFramework(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.framework(code)));
    }

    @GetMapping("/frameworks/{code}/requirements")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Requirement>>> getRequirements(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.requirements(code)));
    }

    @GetMapping("/mappings")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Mapping>>> getMappings(@RequestParam(required = false) String framework) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.mappings(framework)));
    }

    @GetMapping("/gaps")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGapAnalysis(
            @RequestParam String framework,
            @RequestParam(defaultValue = "2026") int year) {
        return ResponseEntity.ok(ApiResponse.ok(complianceService.runGapAnalysis(framework, year)));
    }
}
