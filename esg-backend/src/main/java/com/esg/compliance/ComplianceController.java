package com.esg.compliance;

import com.esg.compliance.ComplianceDtos.ActiveFrameworksRequest;
import com.esg.compliance.ComplianceDtos.AssessRequest;
import com.esg.compliance.ComplianceDtos.AttestationRequest;
import com.esg.compliance.ComplianceDtos.ChangeRequest;
import com.esg.compliance.ComplianceDtos.ChangeResponse;
import com.esg.compliance.ComplianceDtos.DeadlineRequest;
import com.esg.compliance.ComplianceDtos.DeadlineResponse;
import com.esg.compliance.ComplianceDtos.FrameworkStatus;
import com.esg.compliance.ComplianceDtos.GapReport;
import com.esg.compliance.ComplianceDtos.MapRequest;
import com.esg.compliance.ComplianceDtos.MapResult;
import com.esg.compliance.ComplianceDtos.Owner;
import com.esg.compliance.ComplianceDtos.ReminderRun;
import com.esg.compliance.ComplianceDtos.SubmitRequest;
import com.esg.compliance.ComplianceDtos.VersionRequest;
import com.esg.compliance.ComplianceDtos.WaiveRequest;
import com.esg.model.dto.ApiResponse;
import com.esg.reference.FrameworkCatalogService;
import com.esg.reference.ReferenceDtos;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Regulatory Compliance Manager (TRD §6.4.2 catalogue: frameworks, status, gaps, deadlines, map; WP-22, D23). */
@RestController
@RequestMapping("/v1/compliance")
public class ComplianceController {

    private final FrameworkCatalogService catalog;
    private final ComplianceService compliance;
    private final DeadlineService deadlines;
    private final DeadlineReminderService reminders;
    private final RegulatoryChangeService changes;

    public ComplianceController(FrameworkCatalogService catalog, ComplianceService compliance, DeadlineService deadlines,
                                DeadlineReminderService reminders, RegulatoryChangeService changes) {
        this.catalog = catalog;
        this.compliance = compliance;
        this.deadlines = deadlines;
        this.reminders = reminders;
        this.changes = changes;
    }

    // ---------------------------------------------------------------- frameworks

    @GetMapping("/frameworks")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Framework>>> frameworks(@RequestParam(required = false) String jurisdiction) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.frameworks(jurisdiction)));
    }

    @GetMapping("/frameworks/{code}")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<ReferenceDtos.Framework>> framework(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.framework(code)));
    }

    @GetMapping("/frameworks/{code}/requirements")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Requirement>>> requirements(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.requirements(code)));
    }

    @GetMapping("/mappings")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<ReferenceDtos.Mapping>>> mappings(@RequestParam(required = false) String framework) {
        return ResponseEntity.ok(ApiResponse.ok(catalog.mappings(framework)));
    }

    /** The frameworks this tenant reports against (tenant settings default frameworks). */
    @PutMapping("/frameworks/active")
    @PreAuthorize("hasAuthority('compliance:configure')")
    public ResponseEntity<ApiResponse<List<String>>> setActive(@Valid @RequestBody ActiveFrameworksRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(compliance.setActive(request.codes()), "Frameworks updated"));
    }

    // ---------------------------------------------------------------- status, gaps, attestation, mapping

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<FrameworkStatus>>> status(@RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.ok(compliance.status(year)));
    }

    @GetMapping("/gaps")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<GapReport>> gaps(@RequestParam String framework, @RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.ok(compliance.gaps(framework, year)));
    }

    @PutMapping("/attestations")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<ApiResponse<GapReport>> attest(@Valid @RequestBody AttestationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(compliance.attest(request), "Status recorded"));
    }

    @PostMapping("/map")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<MapResult>> map(@Valid @RequestBody MapRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(compliance.map(request)));
    }

    @GetMapping("/owners")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<Owner>>> owners() {
        return ResponseEntity.ok(ApiResponse.ok(compliance.owners()));
    }

    // ---------------------------------------------------------------- filing deadlines

    @GetMapping("/deadlines")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<DeadlineResponse>>> deadlines(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(deadlines.list(from, to, status)));
    }

    @GetMapping("/deadlines/{id}")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<DeadlineResponse>> deadline(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(deadlines.get(id)));
    }

    @PostMapping("/deadlines")
    @PreAuthorize("hasAuthority('compliance:create')")
    public ResponseEntity<ApiResponse<DeadlineResponse>> createDeadline(@Valid @RequestBody DeadlineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(deadlines.create(request), "Deadline added"));
    }

    @PutMapping("/deadlines/{id}")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<ApiResponse<DeadlineResponse>> updateDeadline(@PathVariable UUID id, @Valid @RequestBody DeadlineRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deadlines.update(id, request), "Deadline updated"));
    }

    @PutMapping("/deadlines/{id}/submit")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<ApiResponse<DeadlineResponse>> submit(@PathVariable UUID id, @Valid @RequestBody SubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deadlines.submit(id, request), "Filing marked submitted"));
    }

    @PutMapping("/deadlines/{id}/waive")
    @PreAuthorize("hasAuthority('compliance:approve')")
    public ResponseEntity<ApiResponse<DeadlineResponse>> waive(@PathVariable UUID id, @Valid @RequestBody WaiveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deadlines.waive(id, request), "Filing waived"));
    }

    @PutMapping("/deadlines/{id}/reopen")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<ApiResponse<DeadlineResponse>> reopenDeadline(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deadlines.reopen(id, request.version()), "Filing reopened"));
    }

    @DeleteMapping("/deadlines/{id}")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<Void> deleteDeadline(@PathVariable UUID id, @RequestParam long version) {
        deadlines.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    /** Sends the reminders due today for this tenant now (the daily job does the same for every tenant). */
    @PostMapping("/deadlines/reminders/run")
    @PreAuthorize("hasAuthority('compliance:configure')")
    public ResponseEntity<ApiResponse<ReminderRun>> runReminders() {
        return ResponseEntity.ok(ApiResponse.ok(reminders.runCurrentTenant()));
    }

    // ---------------------------------------------------------------- regulatory changes

    @GetMapping("/changes")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<List<ChangeResponse>>> changes(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(changes.list(status)));
    }

    @GetMapping("/changes/{id}")
    @PreAuthorize("hasAuthority('compliance:read')")
    public ResponseEntity<ApiResponse<ChangeResponse>> change(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(changes.get(id)));
    }

    @PostMapping("/changes")
    @PreAuthorize("hasAuthority('compliance:create')")
    public ResponseEntity<ApiResponse<ChangeResponse>> createChange(@Valid @RequestBody ChangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(changes.create(request), "Change recorded"));
    }

    @PutMapping("/changes/{id}")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<ApiResponse<ChangeResponse>> updateChange(@PathVariable UUID id, @Valid @RequestBody ChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(changes.update(id, request), "Change updated"));
    }

    @PutMapping("/changes/{id}/start")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<ApiResponse<ChangeResponse>> startAssessment(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(changes.startAssessment(id, request.version()), "Assessment started"));
    }

    @PutMapping("/changes/{id}/assess")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<ApiResponse<ChangeResponse>> assess(@PathVariable UUID id, @Valid @RequestBody AssessRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(changes.assess(id, request), "Impact assessed"));
    }

    @PutMapping("/changes/{id}/close")
    @PreAuthorize("hasAuthority('compliance:approve')")
    public ResponseEntity<ApiResponse<ChangeResponse>> close(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(changes.close(id, request.version()), "Change closed"));
    }

    @PutMapping("/changes/{id}/reopen")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<ApiResponse<ChangeResponse>> reopenChange(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(changes.reopen(id, request.version()), "Change reopened"));
    }

    @DeleteMapping("/changes/{id}")
    @PreAuthorize("hasAuthority('compliance:update')")
    public ResponseEntity<Void> deleteChange(@PathVariable UUID id, @RequestParam long version) {
        changes.delete(id, version);
        return ResponseEntity.noContent().build();
    }
}
