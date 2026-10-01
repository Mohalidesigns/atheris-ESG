package com.esg.controller;

import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.EmissionRecordDto;
import com.esg.model.entity.EmissionRecord;
import com.esg.service.CarbonAccountingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/carbon")
public class CarbonController {

    public CarbonController(CarbonAccountingService carbonService) {
        this.carbonService = carbonService;
    }

    private final CarbonAccountingService carbonService;

    @GetMapping("/emissions")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<Page<EmissionRecord>>> getEmissions(
            @RequestParam(required = false) String scope,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(carbonService.getEmissions(scope, pageable)));
    }

    @PostMapping("/emissions")
    @PreAuthorize("hasAuthority('carbon_data:create')")
    public ResponseEntity<ApiResponse<EmissionRecord>> createEmission(
            @Valid @RequestBody EmissionRecordDto dto) {
        EmissionRecord record = carbonService.calculateAndSave(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(record, "Emission recorded"));
    }

    @GetMapping("/emissions/summary")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEmissionsSummary() {
        return ResponseEntity.ok(ApiResponse.ok(carbonService.getEmissionsSummary()));
    }

    // Emission factors moved to EmissionFactorController (/v1/carbon/factors, WP-17).
}
