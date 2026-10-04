package com.esg.reference;

import com.esg.model.dto.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only reference catalogues (WP-17). */
@RestController
@RequestMapping("/v1/reference")
public class ReferenceController {

    private final ReferenceService service;

    public ReferenceController(ReferenceService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Metric>>> metrics(@RequestParam(required = false) String pillar,
                                                                          @RequestParam(required = false) String framework) {
        return ResponseEntity.ok(ApiResponse.ok(service.metrics(pillar, framework)));
    }

    @GetMapping("/currencies")
    @PreAuthorize("hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Currency>>> currencies() {
        return ResponseEntity.ok(ApiResponse.ok(service.currencies()));
    }

    @GetMapping("/countries")
    @PreAuthorize("hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Country>>> countries() {
        return ResponseEntity.ok(ApiResponse.ok(service.countries()));
    }

    @GetMapping("/sdgs")
    @PreAuthorize("hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Sdg>>> sdgs() {
        return ResponseEntity.ok(ApiResponse.ok(service.sdgs()));
    }

    @GetMapping("/scope3-categories")
    @PreAuthorize("hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Scope3Category>>> scope3Categories() {
        return ResponseEntity.ok(ApiResponse.ok(service.scope3Categories()));
    }

    /** IANA time zones for Settings › Reporting time zone (F10); the server accepts only these. */
    @GetMapping("/timezones")
    @PreAuthorize("hasAuthority('reference_data:read') or hasAuthority('tenant_settings:read')")
    public ResponseEntity<ApiResponse<List<String>>> timeZones() {
        return ResponseEntity.ok(ApiResponse.ok(com.esg.service.TenantSettingsService.timeZones()));
    }

    @GetMapping("/sectors")
    @PreAuthorize("hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Sector>>> sectors() {
        return ResponseEntity.ok(ApiResponse.ok(service.sectors()));
    }
}
