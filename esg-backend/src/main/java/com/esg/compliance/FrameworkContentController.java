package com.esg.compliance;

import com.esg.model.dto.ApiResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Phase 3 (5): framework requirement content — platform import (dry run, then apply) and version history. */
@RestController
public class FrameworkContentController {

    private final FrameworkContentService service;

    public FrameworkContentController(FrameworkContentService service) {
        this.service = service;
    }

    @PostMapping(value = "/v1/platform/frameworks/{code}/content", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('framework_content:import')")
    public ResponseEntity<ApiResponse<FrameworkContentService.ImportResult>> importContent(
            @PathVariable String code, @RequestParam("file") MultipartFile file, @RequestParam String versionLabel,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveTo,
            @RequestParam(defaultValue = "false") boolean smeVerified, @RequestParam(required = false) String verifiedBy,
            @RequestParam String source, @RequestParam(defaultValue = "true") boolean dryRun) throws IOException {
        FrameworkContentService.ImportResult result = service.importContent(code, new FrameworkContentService.ImportRequest(versionLabel,
                effectiveFrom, effectiveTo, smeVerified, verifiedBy, source, dryRun), file.getBytes(), file.getOriginalFilename());
        String message = !result.errors().isEmpty() ? "The file has errors; nothing was imported"
                : result.applied() ? "Content version " + result.versionLabel() + " imported" : "Checked; nothing imported yet";
        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }

    @GetMapping("/v1/compliance/frameworks/{code}/content-versions")
    @PreAuthorize("hasAuthority('compliance:read') or hasAuthority('framework_content:import')")
    public ResponseEntity<ApiResponse<List<FrameworkContentService.ContentVersion>>> versions(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(service.versions(code)));
    }
}
