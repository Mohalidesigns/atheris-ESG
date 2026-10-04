package com.esg.audit;

import com.esg.model.dto.ApiResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit log viewer and data lineage (TRD §6.4.2 GET /audit/logs; AUDITOR, TENANT_ADMIN). */
@RestController
@RequestMapping("/v1/audit")
public class AuditController {

    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('audit_logs:read')")
    public ResponseEntity<ApiResponse<AuditDtos.AuditPage>> logs(
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.search(resource, action, userId, resourceId, from, to, page, size)));
    }

    /** The audited tables, with friendly names, for the Audit log screen's Table filter (S2). */
    @GetMapping("/resources")
    @PreAuthorize("hasAuthority('audit_logs:read')")
    public ResponseEntity<ApiResponse<java.util.List<AuditDtos.AuditResource>>> resources() {
        return ResponseEntity.ok(ApiResponse.ok(service.resources()));
    }

    /** Phase 3 (6): the filtered trail as a CSV download (same filters as /logs). */
    @GetMapping(value = "/logs/export", produces = "text/csv")
    @PreAuthorize("hasAuthority('audit_logs:read')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        byte[] csv = service.exportCsv(resource, action, userId, resourceId, from, to).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv);
    }

    /** People who appear in the trail, for the User filter (audit readers may not have users:read). */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('audit_logs:read')")
    public ResponseEntity<ApiResponse<java.util.List<AuditDtos.AuditUser>>> users() {
        return ResponseEntity.ok(ApiResponse.ok(service.users()));
    }

    @GetMapping("/lineage")
    @PreAuthorize("hasAuthority('audit_logs:read')")
    public ResponseEntity<ApiResponse<AuditDtos.Lineage>> lineage(@RequestParam String type, @RequestParam UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.lineage(type, id)));
    }
}
