package com.esg.social;

import com.esg.model.dto.ApiResponse;
import com.esg.social.SocialDtos.ActionRequest;
import com.esg.social.SocialDtos.EvidenceRequest;
import com.esg.social.SocialDtos.ExpenditureRequest;
import com.esg.social.SocialDtos.Grievance;
import com.esg.social.SocialDtos.GrievanceRequest;
import com.esg.social.SocialDtos.GrievanceSummary;
import com.esg.social.SocialDtos.Incident;
import com.esg.social.SocialDtos.IncidentRequest;
import com.esg.social.SocialDtos.Overview;
import com.esg.social.SocialDtos.Person;
import com.esg.social.SocialDtos.Project;
import com.esg.social.SocialDtos.ProjectRequest;
import com.esg.social.SocialDtos.SafetyStatistics;
import com.esg.social.SocialDtos.WorkforceSnapshot;
import com.esg.social.SocialDtos.WorkforceSnapshotRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * M7 Social Impact (WP-33; TRD §6.4.2 Social): workforce diversity, health and safety, community projects, grievances.
 * Workflow steps go through POST …/actions; approval-type steps (approve, close, reopen) also need social:approve,
 * which the service checks and the audit log records when refused.
 */
@RestController
@RequestMapping("/v1/social")
public class SocialController {

    private final WorkforceService workforce;
    private final SafetyService safety;
    private final CommunityService community;
    private final GrievanceService grievances;
    private final SocialMetricsService metrics;
    private final SocialSupport support;

    public SocialController(WorkforceService workforce, SafetyService safety, CommunityService community, GrievanceService grievances,
                            SocialMetricsService metrics, SocialSupport support) {
        this.workforce = workforce;
        this.safety = safety;
        this.community = community;
        this.grievances = grievances;
        this.metrics = metrics;
        this.support = support;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<Overview>> overview(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(metrics.overview(year == null ? currentYear() : checkYear(year), true)));
    }

    @GetMapping("/people")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<List<Person>>> people() {
        return ResponseEntity.ok(ApiResponse.ok(support.holders("update")));
    }

    // ---------------------------------------------------------------- workforce

    @GetMapping("/workforce")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<List<WorkforceSnapshot>>> snapshots(@RequestParam(required = false) Integer year,
                                                                          @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(workforce.list(year, status)));
    }

    @GetMapping("/workforce/{id}")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<WorkforceSnapshot>> snapshot(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(workforce.get(id)));
    }

    @PostMapping("/workforce")
    @PreAuthorize("hasAuthority('social:create')")
    public ResponseEntity<ApiResponse<WorkforceSnapshot>> addSnapshot(@Valid @RequestBody WorkforceSnapshotRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(workforce.create(r), "Workforce figures saved"));
    }

    @PutMapping("/workforce/{id}")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<ApiResponse<WorkforceSnapshot>> updateSnapshot(@PathVariable UUID id, @Valid @RequestBody WorkforceSnapshotRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(workforce.update(id, r), "Workforce figures updated"));
    }

    @DeleteMapping("/workforce/{id}")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<Void> deleteSnapshot(@PathVariable UUID id, @RequestParam long version) {
        workforce.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workforce/{id}/actions")
    @PreAuthorize("hasAuthority('social:update') or hasAuthority('social:approve')")
    public ResponseEntity<ApiResponse<WorkforceSnapshot>> snapshotAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest a) {
        return ResponseEntity.ok(ApiResponse.ok(workforce.act(id, a)));
    }

    // ---------------------------------------------------------------- safety

    @GetMapping("/incidents")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<List<Incident>>> incidents(@RequestParam(required = false) Integer year,
                                                                 @RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) UUID orgId) {
        return ResponseEntity.ok(ApiResponse.ok(safety.list(year, status, orgId)));
    }

    @GetMapping("/incidents/{id}")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<Incident>> incident(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(safety.get(id)));
    }

    @PostMapping("/incidents")
    @PreAuthorize("hasAuthority('social:create')")
    public ResponseEntity<ApiResponse<Incident>> addIncident(@Valid @RequestBody IncidentRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(safety.create(r), "Incident recorded"));
    }

    @PutMapping("/incidents/{id}")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<ApiResponse<Incident>> updateIncident(@PathVariable UUID id, @Valid @RequestBody IncidentRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(safety.update(id, r), "Incident updated"));
    }

    @DeleteMapping("/incidents/{id}")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<Void> deleteIncident(@PathVariable UUID id, @RequestParam long version) {
        safety.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/incidents/{id}/actions")
    @PreAuthorize("hasAuthority('social:update') or hasAuthority('social:approve')")
    public ResponseEntity<ApiResponse<Incident>> incidentAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest a) {
        return ResponseEntity.ok(ApiResponse.ok(safety.act(id, a)));
    }

    @GetMapping("/safety/statistics")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<SafetyStatistics>> safetyStatistics(@RequestParam(required = false) Integer year,
                                                                          @RequestParam(required = false) UUID orgId) {
        return ResponseEntity.ok(ApiResponse.ok(safety.statistics(year == null ? currentYear() : checkYear(year), orgId, true)));
    }

    // ---------------------------------------------------------------- community

    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<List<Project>>> projects(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(community.list(status)));
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<Project>> project(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(community.get(id)));
    }

    @PostMapping("/projects")
    @PreAuthorize("hasAuthority('social:create')")
    public ResponseEntity<ApiResponse<Project>> addProject(@Valid @RequestBody ProjectRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(community.create(r), "Project added"));
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<ApiResponse<Project>> updateProject(@PathVariable UUID id, @Valid @RequestBody ProjectRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(community.update(id, r), "Project updated"));
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id, @RequestParam long version) {
        community.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{id}/expenditures")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<ApiResponse<Project>> addExpenditure(@PathVariable UUID id, @Valid @RequestBody ExpenditureRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(community.addExpenditure(id, r), "Expenditure recorded"));
    }

    @DeleteMapping("/projects/{id}/expenditures/{expenditureId}")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<ApiResponse<Project>> deleteExpenditure(@PathVariable UUID id, @PathVariable UUID expenditureId,
                                                                  @RequestParam long version) {
        return ResponseEntity.ok(ApiResponse.ok(community.deleteExpenditure(id, expenditureId, version), "Expenditure removed"));
    }

    @PostMapping("/projects/{id}/evidence")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<ApiResponse<Project>> addEvidence(@PathVariable UUID id, @Valid @RequestBody EvidenceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(community.addEvidence(id, r), "Evidence added"));
    }

    @DeleteMapping("/projects/{id}/evidence/{evidenceId}")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<ApiResponse<Project>> deleteEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        return ResponseEntity.ok(ApiResponse.ok(community.deleteEvidence(id, evidenceId), "Evidence removed"));
    }

    // ---------------------------------------------------------------- grievances

    @GetMapping("/grievances")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<List<GrievanceSummary>>> grievanceList(@RequestParam(required = false) String status,
                                                                             @RequestParam(required = false) Boolean overdue,
                                                                             @RequestParam(required = false) UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(grievances.list(status, overdue, projectId)));
    }

    @GetMapping("/grievances/{id}")
    @PreAuthorize("hasAuthority('social:read')")
    public ResponseEntity<ApiResponse<Grievance>> grievance(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(grievances.get(id)));
    }

    @PostMapping("/grievances")
    @PreAuthorize("hasAuthority('social:create')")
    public ResponseEntity<ApiResponse<Grievance>> addGrievance(@Valid @RequestBody GrievanceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(grievances.create(r), "Grievance recorded"));
    }

    @PutMapping("/grievances/{id}")
    @PreAuthorize("hasAuthority('social:update')")
    public ResponseEntity<ApiResponse<Grievance>> updateGrievance(@PathVariable UUID id, @Valid @RequestBody GrievanceRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(grievances.update(id, r), "Grievance updated"));
    }

    @PostMapping("/grievances/escalate-overdue")
    @PreAuthorize("hasAuthority('social:approve')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Integer>>> escalateOverdue() {
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("escalated", grievances.escalateNow())));
    }

    @PostMapping("/grievances/{id}/actions")
    @PreAuthorize("hasAuthority('social:update') or hasAuthority('social:approve')")
    public ResponseEntity<ApiResponse<Grievance>> grievanceAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest a) {
        return ResponseEntity.ok(ApiResponse.ok(grievances.act(id, a)));
    }

    private int currentYear() {
        return support.today().getYear();
    }

    private static int checkYear(int year) {
        if (year < 2000 || year > LocalDate.now(ZoneId.of("Africa/Lagos")).getYear() + 1) {
            throw com.esg.exception.FieldValidationException.of("year", "must be between 2000 and next year");
        }
        return year;
    }
}
