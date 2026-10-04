package com.esg.controller;

import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.TenancyDtos.CreateTenantRequest;
import com.esg.model.dto.TenancyDtos.TenantResponse;
import com.esg.model.dto.TenancyDtos.UpdateTenantRequest;
import com.esg.service.PlatformTenantService;
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

/** Platform tenant management for SUPER_ADMIN (WP-14). */
@RestController
@RequestMapping("/v1/platform/tenants")
public class PlatformTenantController {

    private final PlatformTenantService service;

    public PlatformTenantController(PlatformTenantService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenants:read')")
    public ResponseEntity<ApiResponse<List<TenantResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenants:create')")
    public ResponseEntity<ApiResponse<TenantResponse>> create(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request), "Tenant created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('tenants:update')")
    public ResponseEntity<ApiResponse<TenantResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, request), "Tenant updated"));
    }
}
