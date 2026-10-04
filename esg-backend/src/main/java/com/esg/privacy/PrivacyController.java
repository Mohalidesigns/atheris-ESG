package com.esg.privacy;

import com.esg.model.dto.ApiResponse;
import com.esg.privacy.PrivacyDtos.AcknowledgeRequest;
import com.esg.privacy.PrivacyDtos.MyNotice;
import com.esg.privacy.PrivacyDtos.Notice;
import com.esg.privacy.PrivacyDtos.NoticeRequest;
import com.esg.privacy.PrivacyDtos.RequestAction;
import com.esg.privacy.PrivacyDtos.RequestCreate;
import com.esg.privacy.PrivacyDtos.RetentionSettings;
import com.esg.privacy.PrivacyDtos.SubjectRequest;
import com.esg.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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

/**
 * NDPA 2023 (WP-52, D40). Everyone signed in: the privacy notice in force, acknowledging it, and downloading their own
 * data. The privacy team (privacy:read / privacy:manage): notices, the data subject request register, exports and
 * erasure, retention settings.
 */
@RestController
@RequestMapping("/v1/privacy")
public class PrivacyController {

    private final PrivacyService privacy;

    public PrivacyController(PrivacyService privacy) {
        this.privacy = privacy;
    }

    @GetMapping("/notice")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MyNotice>> myNotice() {
        return ResponseEntity.ok(ApiResponse.ok(privacy.mine()));
    }

    @PostMapping("/notice/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MyNotice>> acknowledge(@Valid @RequestBody AcknowledgeRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(privacy.acknowledge(r.version())));
    }

    /** NDPA s. 34 / s. 38: a copy of one's own data, in a portable form. */
    @GetMapping("/my-data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> myData() {
        return download(privacy.export(CurrentUser.access().userId()), "my-data");
    }

    // ---------------------------------------------------------------- privacy team

    @GetMapping("/notices")
    @PreAuthorize("hasAuthority('privacy:read')")
    public ResponseEntity<ApiResponse<List<Notice>>> notices() {
        return ResponseEntity.ok(ApiResponse.ok(privacy.notices()));
    }

    @PostMapping("/notices")
    @PreAuthorize("hasAuthority('privacy:manage')")
    public ResponseEntity<ApiResponse<Notice>> publish(@Valid @RequestBody NoticeRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(privacy.publish(r), "Privacy notice published"));
    }

    @GetMapping("/people")
    @PreAuthorize("hasAuthority('privacy:manage')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> people() {
        return ResponseEntity.ok(ApiResponse.ok(privacy.people()));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('privacy:read')")
    public ResponseEntity<ApiResponse<List<SubjectRequest>>> requests(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(privacy.requests(status)));
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("hasAuthority('privacy:read')")
    public ResponseEntity<ApiResponse<SubjectRequest>> request(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(privacy.request(id)));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAuthority('privacy:manage')")
    public ResponseEntity<ApiResponse<SubjectRequest>> create(@Valid @RequestBody RequestCreate r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(privacy.createRequest(r)));
    }

    @PostMapping("/requests/{id}/actions")
    @PreAuthorize("hasAuthority('privacy:manage')")
    public ResponseEntity<ApiResponse<SubjectRequest>> act(@PathVariable UUID id, @Valid @RequestBody RequestAction r) {
        return ResponseEntity.ok(ApiResponse.ok(privacy.act(id, r)));
    }

    @GetMapping("/requests/{id}/export")
    @PreAuthorize("hasAuthority('privacy:manage')")
    public ResponseEntity<Map<String, Object>> export(@PathVariable UUID id) {
        return download(privacy.exportFor(id), "subject-data-" + privacy.request(id).reference());
    }

    @PostMapping("/requests/{id}/erase")
    @PreAuthorize("hasAuthority('privacy:manage')")
    public ResponseEntity<ApiResponse<SubjectRequest>> erase(@PathVariable UUID id, @RequestParam Long version) {
        return ResponseEntity.ok(ApiResponse.ok(privacy.erase(id, version), "Personal data erased"));
    }

    @GetMapping("/retention")
    @PreAuthorize("hasAuthority('privacy:read')")
    public ResponseEntity<ApiResponse<RetentionSettings>> retention() {
        return ResponseEntity.ok(ApiResponse.ok(privacy.retention()));
    }

    @PutMapping("/retention")
    @PreAuthorize("hasAuthority('privacy:manage')")
    public ResponseEntity<ApiResponse<RetentionSettings>> updateRetention(@Valid @RequestBody RetentionSettings r) {
        return ResponseEntity.ok(ApiResponse.ok(privacy.updateRetention(r), "Retention settings saved"));
    }

    private static ResponseEntity<Map<String, Object>> download(Map<String, Object> body, String name) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
