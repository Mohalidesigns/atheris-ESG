package com.esg.reporting;

import com.esg.model.dto.ApiResponse;
import com.esg.reporting.ReportingDtos.ExportJob;
import com.esg.reporting.ReportingDtos.GenerateRequest;
import com.esg.reporting.ReportingDtos.ReportDetail;
import com.esg.reporting.ReportingDtos.ReportDetailsRequest;
import com.esg.reporting.ReportingDtos.ReportRequest;
import com.esg.reporting.ReportingDtos.ReportSummary;
import com.esg.reporting.ReportingDtos.SectionsRequest;
import com.esg.reporting.ReportingDtos.StepActionRequest;
import com.esg.reporting.ReportingDtos.SubmitRequest;
import com.esg.reporting.ReportingDtos.Template;
import com.esg.reporting.ReportingDtos.VersionDetail;
import com.esg.reporting.ReportingDtos.VersionSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reporting & Disclosure Studio (TRD §6.4.2: POST /reports, GET /reports/{id}, PUT /reports/{id}/sections, POST …/generate, POST …/approve). */
@RestController
@RequestMapping("/v1/reports")
public class ReportController {

    private final ReportService reports;
    private final ExportService exports;

    public ReportController(ReportService reports, ExportService exports) {
        this.reports = reports;
        this.exports = exports;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('reports:read')")
    public ResponseEntity<ApiResponse<List<ReportSummary>>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(reports.list(status)));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('reports:read')")
    public ResponseEntity<ApiResponse<List<Template>>> templates() {
        return ResponseEntity.ok(ApiResponse.ok(reports.templates()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('reports:create')")
    public ResponseEntity<ApiResponse<ReportDetail>> create(@Valid @RequestBody ReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reports.create(request), "Report created"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('reports:read')")
    public ResponseEntity<ApiResponse<ReportDetail>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(reports.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('reports:update')")
    public ResponseEntity<ApiResponse<ReportDetail>> update(@PathVariable UUID id, @Valid @RequestBody ReportDetailsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reports.updateDetails(id, request), "Report updated"));
    }

    @PutMapping("/{id}/sections")
    @PreAuthorize("hasAuthority('reports:update')")
    public ResponseEntity<ApiResponse<ReportDetail>> sections(@PathVariable UUID id, @Valid @RequestBody SectionsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reports.saveSections(id, request), "Sections saved"));
    }

    @PostMapping("/{id}/refresh")
    @PreAuthorize("hasAuthority('reports:update')")
    public ResponseEntity<ApiResponse<ReportDetail>> refresh(@PathVariable UUID id, @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(ApiResponse.ok(reports.refresh(id, version(body)), "Data refreshed"));
    }

    /** TRD: "Submit report for approval workflow". */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('reports:update')")
    public ResponseEntity<ApiResponse<ReportDetail>> submit(@PathVariable UUID id, @Valid @RequestBody SubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reports.submit(id, request), "Submitted for approval"));
    }

    @PutMapping("/{id}/approval-steps/{stepId}")
    @PreAuthorize("hasAuthority('reports:approve')")
    public ResponseEntity<ApiResponse<ReportDetail>> act(@PathVariable UUID id, @PathVariable UUID stepId, @Valid @RequestBody StepActionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reports.act(id, stepId, request), "Decision recorded"));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('reports:update')")
    public ResponseEntity<ApiResponse<ReportDetail>> reopen(@PathVariable UUID id, @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(ApiResponse.ok(reports.reopen(id, version(body)), "Report reopened"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('reports:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam long version) {
        reports.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('reports:read')")
    public ResponseEntity<ApiResponse<List<VersionSummary>>> versions(@PathVariable UUID id) {
        reports.get(id);
        return ResponseEntity.ok(ApiResponse.ok(reports.versions(id)));
    }

    @GetMapping("/{id}/versions/{versionId}")
    @PreAuthorize("hasAuthority('reports:read')")
    public ResponseEntity<ApiResponse<VersionDetail>> version(@PathVariable UUID id, @PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.ok(reports.version(id, versionId)));
    }

    /** TRD: "Generate PDF/XBRL export" (PDF and Excel; XBRL is WP-30). */
    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority('reports:export')")
    public ResponseEntity<ApiResponse<ExportJob>> generate(@PathVariable UUID id, @Valid @RequestBody GenerateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(exports.generate(id, request.format()), "Export generated"));
    }

    private static long version(Map<String, Long> body) {
        Long v = body == null ? null : body.get("version");
        if (v == null) {
            throw com.esg.exception.FieldValidationException.of("version", "is required");
        }
        return v;
    }
}
