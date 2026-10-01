package com.esg.datahub;

import com.esg.datahub.ImportDtos.ImportJobResponse;
import com.esg.datahub.ImportDtos.ImportStartRequest;
import com.esg.model.dto.ApiResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Bulk import of data points from CSV or Excel (WP-20, journey 4). Imported rows become drafts. */
@RestController
@RequestMapping("/v1/data/imports")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<ImportJobResponse>> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.upload(file), "File read; map the columns"));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<List<ImportJobResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<ImportJobResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<ApiResponse<ImportJobResponse>> start(@PathVariable UUID id, @Valid @RequestBody ImportStartRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(service.start(id, request), "Import started"));
    }

    @GetMapping(value = "/template", produces = "text/csv")
    @PreAuthorize("hasAuthority('data_points:create')")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"esg-data-import-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(service.template().getBytes(StandardCharsets.UTF_8));
    }
}
