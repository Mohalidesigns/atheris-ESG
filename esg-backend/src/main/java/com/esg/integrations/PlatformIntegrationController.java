package com.esg.integrations;

import com.esg.integrations.IntegrationDtos.PlatformRequest;
import com.esg.integrations.IntegrationDtos.StatusChange;
import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WP-46 (D44): the platform team's queue of connector requests from every tenant (SUPER_ADMIN: tenants:read / tenants:update). */
@RestController
@RequestMapping("/v1/platform/integration-requests")
public class PlatformIntegrationController {

    private final ConnectorService connectors;

    public PlatformIntegrationController(ConnectorService connectors) {
        this.connectors = connectors;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenants:read')")
    public ResponseEntity<ApiResponse<List<PlatformRequest>>> queue() {
        return ResponseEntity.ok(ApiResponse.ok(connectors.platformQueue()));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('tenants:update')")
    public ResponseEntity<ApiResponse<PlatformRequest>> status(@PathVariable UUID id, @Valid @RequestBody StatusChange change) {
        return ResponseEntity.ok(ApiResponse.ok(connectors.setStatus(id, change), "Request " + ConnectorService.label(change.status())));
    }
}
