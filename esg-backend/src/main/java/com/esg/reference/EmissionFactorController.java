package com.esg.reference;

import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Emission factors: platform (read-only, sourced) and the tenant's custom ones (TRD §8.2; WP-17). */
@RestController
@RequestMapping("/v1/carbon/factors")
public class EmissionFactorController {

    private final EmissionFactorService service;

    private final EmissionActivityService activities;

    public EmissionFactorController(EmissionFactorService service, EmissionActivityService activities) {
        this.activities = activities;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('carbon_data:read') or hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.EmissionFactor>>> list(@RequestParam(required = false) String region,
                                                                               @RequestParam(required = false) String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(region, category)));
    }

    /** Activities (factor categories) with their GHG scope and unit (WP-52b F5/F11). */
    @GetMapping("/activities")
    @PreAuthorize("hasAuthority('carbon_data:read') or hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.EmissionActivity>>> activities() {
        return ResponseEntity.ok(ApiResponse.ok(activities.list()));
    }

    /** ISO countries and factor regions for the custom factor picker (F11). */
    @GetMapping("/regions")
    @PreAuthorize("hasAuthority('carbon_data:read') or hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.FactorRegion>>> regions() {
        return ResponseEntity.ok(ApiResponse.ok(activities.regions()));
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasAuthority('carbon_data:read')")
    public ResponseEntity<ApiResponse<ReferenceDtos.ResolvedFactor>> resolve(
            @RequestParam String category, @RequestParam(required = false) String country,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BigDecimal activityData) {
        return ResponseEntity.ok(ApiResponse.ok(service.resolve(category, country, date, activityData)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('carbon_data:read') or hasAuthority('reference_data:read')")
    public ResponseEntity<ApiResponse<ReferenceDtos.EmissionFactor>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('emission_factors:manage')")
    public ResponseEntity<ApiResponse<ReferenceDtos.EmissionFactor>> create(@Valid @RequestBody ReferenceDtos.CustomFactorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createCustom(request), "Custom factor added"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('emission_factors:manage')")
    public ResponseEntity<ApiResponse<ReferenceDtos.EmissionFactor>> update(@PathVariable UUID id,
                                                                           @Valid @RequestBody ReferenceDtos.CustomFactorRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateCustom(id, request), "Custom factor updated"));
    }
}
