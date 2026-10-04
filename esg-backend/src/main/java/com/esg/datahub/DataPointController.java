package com.esg.datahub;

import com.esg.controller.SortableFields;
import com.esg.datahub.DataHubDtos.DataPointResponse;
import com.esg.datahub.DataHubDtos.HistoryEntry;
import com.esg.datahub.DataHubDtos.TransitionRequest;
import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.DataPointDto;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

/**
 * M1 data points (WP-20): CRUD, workflow, history and the validation rules the form mirrors. Workflow steps follow the
 * TRD §6.4.2 catalogue entry {@code PUT /api/v1/data/points/{id}/validate}: every step is a PUT on the point.
 */
@RestController
@RequestMapping("/v1/data")
public class DataPointController {

    static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "reportingPeriodStart", "metricKey", "category",
            "validationStatus", "numericValue", "qualityScore");

    private final DataPointService service;
    private final DataValidationService validation;
    private final DataPointBulkService bulk;

    public DataPointController(DataPointService service, DataValidationService validation, DataPointBulkService bulk) {
        this.service = service;
        this.validation = validation;
        this.bulk = bulk;
    }

    @GetMapping("/points")
    @PreAuthorize("hasAuthority('data_points:read')")
    public ResponseEntity<ApiResponse<Page<DataPointResponse>>> list(
            @RequestParam(required = false) String category, @RequestParam(required = false) String status,
            @RequestParam(required = false) String metricKey, @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID importJobId,
            @PageableDefault(size = 20) Pageable pageable) {
        DataPointService.Filters filters = new DataPointService.Filters(blank(category), blank(status), blank(metricKey),
                orgId, sourceId, from, to, importJobId);
        return ResponseEntity.ok(ApiResponse.ok(service.list(filters, SortableFields.check(pageable, SORTABLE))));
    }

    @GetMapping("/points/{id}")
    @PreAuthorize("hasAuthority('data_points:read')")
    public ResponseEntity<ApiResponse<DataPointResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @GetMapping("/points/{id}/history")
    @PreAuthorize("hasAuthority('data_points:read')")
    public ResponseEntity<ApiResponse<List<HistoryEntry>>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.history(id)));
    }

    @PostMapping("/points")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<DataPointResponse>> create(@Valid @RequestBody DataPointDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(dto), "Draft saved"));
    }

    @PutMapping("/points/{id}")
    @PreAuthorize("hasAuthority('data_points:update')")
    public ResponseEntity<ApiResponse<DataPointResponse>> update(@PathVariable UUID id, @Valid @RequestBody DataPointDto dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, dto), "Data point updated"));
    }

    @DeleteMapping("/points/{id}")
    @PreAuthorize("hasAnyAuthority('data_points:create', 'data_points:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam Long version) {
        service.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/points/{id}/submit")
    @PreAuthorize("hasAnyAuthority('data_points:create', 'data_points:update')")
    public ResponseEntity<ApiResponse<DataPointResponse>> submit(@PathVariable UUID id, @Valid @RequestBody TransitionRequest body) {
        return ResponseEntity.ok(ApiResponse.ok(service.submit(id, body.version()), "Submitted for validation"));
    }

    @PutMapping("/points/{id}/validate")
    @PreAuthorize("hasAuthority('data_points:validate')")
    public ResponseEntity<ApiResponse<DataPointResponse>> validate(@PathVariable UUID id, @Valid @RequestBody TransitionRequest body) {
        return ResponseEntity.ok(ApiResponse.ok(service.validate(id, body.version()), "Validated"));
    }

    @PutMapping("/points/{id}/approve")
    @PreAuthorize("hasAuthority('data_points:approve')")
    public ResponseEntity<ApiResponse<DataPointResponse>> approve(@PathVariable UUID id, @Valid @RequestBody TransitionRequest body) {
        return ResponseEntity.ok(ApiResponse.ok(service.approve(id, body.version()), "Approved"));
    }

    @PutMapping("/points/{id}/reject")
    @PreAuthorize("hasAnyAuthority('data_points:validate', 'data_points:approve')")
    public ResponseEntity<ApiResponse<DataPointResponse>> reject(@PathVariable UUID id, @Valid @RequestBody TransitionRequest body) {
        return ResponseEntity.ok(ApiResponse.ok(service.reject(id, body.version(), body.reason()), "Rejected"));
    }

    /**
     * Phase 3 (2): one workflow step on many points. 200 with a result per point (each checked like the single call);
     * the step's own permission is checked by the service.
     */
    @PostMapping("/points/bulk")
    @PreAuthorize("hasAnyAuthority('data_points:create', 'data_points:update', 'data_points:validate', 'data_points:approve')")
    public ResponseEntity<ApiResponse<DataHubDtos.BulkActionResponse>> bulk(@Valid @RequestBody DataHubDtos.BulkActionRequest body) {
        DataHubDtos.BulkActionResponse result = bulk.run(body);
        return ResponseEntity.ok(ApiResponse.ok(result, result.succeeded() + " of " + result.requested() + " done"));
    }

    @PutMapping("/points/{id}/reopen")
    @PreAuthorize("hasAuthority('data_points:approve')")
    public ResponseEntity<ApiResponse<DataPointResponse>> reopen(@PathVariable UUID id, @Valid @RequestBody TransitionRequest body) {
        return ResponseEntity.ok(ApiResponse.ok(service.reopen(id, body.version(), body.reason()), "Reopened"));
    }

    /** The configurable validation rules, so the form can apply the same range errors before saving. */
    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('data_points:read')")
    public ResponseEntity<ApiResponse<List<DataValidationService.Rule>>> rules(@RequestParam(required = false) String metricKey) {
        return ResponseEntity.ok(ApiResponse.ok(validation.rules(blank(metricKey))));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() || "all".equalsIgnoreCase(v) ? null : v;
    }
}
