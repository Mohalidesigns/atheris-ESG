package com.esg.controller;

import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.TenancyDtos.PeriodRequest;
import com.esg.model.dto.TenancyDtos.PeriodResponse;
import com.esg.service.ReportingPeriodService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reporting periods and their open/locked state (WP-14). */
@RestController
@RequestMapping("/v1/reporting-periods")
public class ReportingPeriodController {

    private final ReportingPeriodService service;

    public ReportingPeriodController(ReportingPeriodService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('reporting_periods:read')")
    public ResponseEntity<ApiResponse<List<PeriodResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('reporting_periods:create')")
    public ResponseEntity<ApiResponse<PeriodResponse>> create(@Valid @RequestBody PeriodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request), "Period created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('reporting_periods:update')")
    public ResponseEntity<ApiResponse<PeriodResponse>> update(@PathVariable UUID id, @Valid @RequestBody PeriodRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, request), "Period updated"));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('reporting_periods:approve')")
    public ResponseEntity<ApiResponse<PeriodResponse>> lock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.lock(id), "Period locked"));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('reporting_periods:approve')")
    public ResponseEntity<ApiResponse<PeriodResponse>> unlock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.unlock(id), "Period unlocked"));
    }
}
