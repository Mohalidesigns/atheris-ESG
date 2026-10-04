package com.esg.controller;

import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.TenancyDtos.OrganisationRequest;
import com.esg.model.dto.TenancyDtos.OrganisationResponse;
import com.esg.service.OrganisationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Organisation hierarchy (WP-14). Returned flat with parentOrgId; the UI builds the tree. */
@RestController
@RequestMapping("/v1/organisations")
public class OrganisationController {

    private final OrganisationService service;

    public OrganisationController(OrganisationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('org_structure:read')")
    public ResponseEntity<ApiResponse<List<OrganisationResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('org_structure:read')")
    public ResponseEntity<ApiResponse<OrganisationResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('org_structure:create')")
    public ResponseEntity<ApiResponse<OrganisationResponse>> create(@Valid @RequestBody OrganisationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request), "Organisation created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('org_structure:update')")
    public ResponseEntity<ApiResponse<OrganisationResponse>> update(@PathVariable UUID id,
                                                                    @Valid @RequestBody OrganisationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, request), "Organisation updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('org_structure:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Organisation removed"));
    }
}
