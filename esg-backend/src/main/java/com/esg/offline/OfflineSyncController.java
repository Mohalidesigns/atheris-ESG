package com.esg.offline;

import com.esg.model.dto.ApiResponse;
import com.esg.offline.OfflineDtos.Conflict;
import com.esg.offline.OfflineDtos.ResolveRequest;
import com.esg.offline.OfflineDtos.SyncRequest;
import com.esg.offline.OfflineDtos.SyncResponse;
import com.esg.offline.OfflineDtos.SyncResult;
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

/**
 * WP-43 offline capture (D43): a device sends what was saved without a connection; conflicts wait for the person who
 * captured the value. Each operation is checked against its own permission (data_points:create, social:create).
 */
@RestController
@RequestMapping("/v1/data/sync")
public class OfflineSyncController {

    private final OfflineSyncService service;

    public OfflineSyncController(OfflineSyncService service) {
        this.service = service;
    }

    /** Always 200: the outcome of every operation is in the body (applied, conflict or rejected). */
    @PostMapping
    @PreAuthorize("hasAuthority('data_points:create') or hasAuthority('social:create')")
    public ResponseEntity<ApiResponse<SyncResponse>> sync(@Valid @RequestBody SyncRequest request) {
        SyncResponse response = service.sync(request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.applied() + " of " + response.received() + " synced"));
    }

    @GetMapping("/conflicts")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<List<Conflict>>> conflicts() {
        return ResponseEntity.ok(ApiResponse.ok(service.conflicts()));
    }

    @PostMapping("/conflicts/{id}/resolve")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<SyncResult>> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
        SyncResult result = service.resolve(id, request);
        return ResponseEntity.ok(ApiResponse.ok(result, "keep_server".equals(request.resolution()) ? "Kept the server value" : "Your value was saved"));
    }
}
