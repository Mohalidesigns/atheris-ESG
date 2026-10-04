package com.esg.dashboard;

import com.esg.model.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Executive dashboard (WP-24, TRD §7.4 Dashboard): live figures, each section gated by the module it comes from. */
@RestController
@RequestMapping("/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/executive")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ResponseEntity<ApiResponse<DashboardDtos.Executive>> executive(
            @RequestParam(defaultValue = "year") String granularity) {
        if (!granularity.equals("year") && !granularity.equals("quarter")) {
            throw com.esg.exception.FieldValidationException.of("granularity", "must be year or quarter");
        }
        return ResponseEntity.ok(ApiResponse.ok(service.executive("quarter".equals(granularity))));
    }
}
