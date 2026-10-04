package com.esg.controller;

import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.TenancyDtos.SettingsRequest;
import com.esg.model.dto.TenancyDtos.SettingsResponse;
import com.esg.service.TenantSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant settings (WP-14). */
@RestController
@RequestMapping("/v1/settings")
public class SettingsController {

    private final TenantSettingsService service;

    public SettingsController(TenantSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant_settings:read')")
    public ResponseEntity<ApiResponse<SettingsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(service.get()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('tenant_settings:configure')")
    public ResponseEntity<ApiResponse<SettingsResponse>> update(@Valid @RequestBody SettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(request), "Settings saved"));
    }
}
