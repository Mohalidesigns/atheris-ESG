package com.esg.integrations;

import com.esg.model.dto.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings › Integrations: connection status of the external services (read-only). */
@RestController
@RequestMapping("/v1/integrations")
public class IntegrationController {

    private final IntegrationStatusService service;

    public IntegrationController(IntegrationStatusService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant_settings:read')")
    public ResponseEntity<ApiResponse<List<IntegrationStatusService.Integration>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }
}
