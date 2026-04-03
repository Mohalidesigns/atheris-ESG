package com.esg.controller;

import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.EmissionRecordDto;
import com.esg.model.entity.EmissionFactor;
import com.esg.model.entity.EmissionRecord;
import com.esg.service.CarbonAccountingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<Page<EmissionRecord>>> getEmissions(
            @RequestParam(required = false) String scope,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(carbonService.getEmissions(scope, pageable)));
    }

    @PostMapping("/emissions")
    public ResponseEntity<ApiResponse<EmissionRecord>> createEmission(
            @Valid @RequestBody EmissionRecordDto dto) {
        EmissionRecord record = carbonService.calculateAndSave(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(record, "Emission recorded"));
    }

    @GetMapping("/emissions/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEmissionsSummary() {
        return ResponseEntity.ok(ApiResponse.ok(carbonService.getEmissionsSummary()));
    }

    @GetMapping("/factors")
    public ResponseEntity<ApiResponse<List<EmissionFactor>>> getEmissionFactors(
            @RequestParam(required = false) String region) {
        return ResponseEntity.ok(ApiResponse.ok(carbonService.getEmissionFactors(region)));
    }
}
