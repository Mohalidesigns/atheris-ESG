package com.esg.files;

import com.esg.model.dto.ApiResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** File service API (WP-15, TRD §6.6.4). */
@RestController
@RequestMapping("/v1/files")
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    @GetMapping("/storage-info")
    @PreAuthorize("hasAuthority('files:read')")
    public ResponseEntity<ApiResponse<FileDtos.StorageInfo>> storageInfo() {
        return ResponseEntity.ok(ApiResponse.ok(service.info()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('files:create')")
    public ResponseEntity<ApiResponse<FileDtos.FileInfo>> upload(@RequestPart("file") MultipartFile file,
                                                                 @RequestParam(required = false) String resourceType,
                                                                 @RequestParam(required = false) UUID resourceId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.upload(file, resourceType, resourceId), "File uploaded"));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('files:create')")
    public ResponseEntity<ApiResponse<FileDtos.FileInfo>> addVersion(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.addVersion(id, file), "New version uploaded"));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('files:read')")
    public ResponseEntity<ApiResponse<List<FileDtos.FileInfo>>> list(@RequestParam(required = false) String resourceType,
                                                                     @RequestParam(required = false) UUID resourceId) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(resourceType, resourceId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('files:read')")
    public ResponseEntity<ApiResponse<FileDtos.FileInfo>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("hasAuthority('files:read')")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID id, @RequestParam(required = false) Integer version)
            throws IOException {
        FileService.Download download = service.open(id, version);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, download.contentDisposition())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .body(new InputStreamResource(download.stream()));
    }

    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAuthority('files:read')")
    public ResponseEntity<ApiResponse<FileDtos.DownloadUrl>> downloadUrl(@PathVariable UUID id,
                                                                         @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(ApiResponse.ok(service.downloadUrl(id, version)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('files:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "File deleted"));
    }
}
