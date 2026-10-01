package com.esg.files;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.files.FileDtos.DownloadUrl;
import com.esg.files.FileDtos.FileInfo;
import com.esg.files.FileDtos.FileVersion;
import com.esg.files.FileDtos.StorageInfo;
import com.esg.security.TenantContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * File service (TRD §6.6.4): upload with type/size checks and virus-scan hook, immutable versions, downloads
 * (streamed or pre-signed), soft delete, and an access log of every upload/download. Metadata is tenant data
 * under RLS; bytes go to {@link FileStorage} under a tenant-prefixed key.
 */
@Service
public class FileService {

    private static final String RESOURCE_TYPE_REGEX = "^[a-z_]{1,100}$";

    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final VirusScanner scanner;
    private final long maxBytes;
    private final Duration presignTtl;

    public FileService(JdbcTemplate jdbc, FileStorage storage, VirusScanner scanner,
                       @Value("${esg.files.max-bytes}") long maxBytes,
                       @Value("${esg.files.presign-ttl}") Duration presignTtl) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.scanner = scanner;
        this.maxBytes = maxBytes;
        this.presignTtl = presignTtl;
    }

    public StorageInfo info() {
        return new StorageInfo(storage.description(), maxBytes, FileTypePolicy.ALLOWED_EXTENSIONS,
                scanner instanceof VirusScanner.NotConfigured ? "Not configured: files are stored unscanned" : "Enabled");
    }

    @Transactional
    public FileInfo upload(MultipartFile file, String resourceType, UUID resourceId) {
        if (resourceType != null && !resourceType.isBlank() && !resourceType.matches(RESOURCE_TYPE_REGEX)) {
            throw FieldValidationException.of("resourceType", "lower-case letters and underscores only");
        }
        UUID fileId = UUID.randomUUID();
        Stored stored = store(file, fileId, 1);
        jdbc.update("insert into files.file_metadata (id, original_name, content_type, size_bytes, sha256, resource_type, resource_id) "
                        + "values (?, ?, ?, ?, ?, ?, ?)", fileId, stored.name(), stored.contentType(), stored.size(), stored.sha256(),
                blankToNull(resourceType), resourceId);
        insertVersion(fileId, 1, stored);
        logAccess(fileId, 1, "upload");
        return get(fileId);
    }

    @Transactional
    public FileInfo addVersion(UUID fileId, MultipartFile file) {
        FileInfo current = get(fileId);
        int next = current.currentVersion() + 1;
        Stored stored = store(file, fileId, next);
        jdbc.update("update files.file_metadata set original_name = ?, content_type = ?, size_bytes = ?, sha256 = ?, "
                + "current_version = ?, version = version + 1 where id = ?", stored.name(), stored.contentType(), stored.size(),
                stored.sha256(), next, fileId);
        insertVersion(fileId, next, stored);
        logAccess(fileId, next, "upload");
        return get(fileId);
    }

    @Transactional(readOnly = true)
    public FileInfo get(UUID id) {
        List<FileInfo> rows = jdbc.query("select * from files.file_metadata where id = ? and deleted_at is null",
                (rs, i) -> new FileInfo(rs.getObject("id", UUID.class), rs.getString("original_name"), rs.getString("content_type"),
                        rs.getLong("size_bytes"), rs.getString("sha256"), rs.getInt("current_version"), rs.getString("resource_type"),
                        rs.getObject("resource_id", UUID.class), rs.getTimestamp("created_at").toInstant(), versions(id)), id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("File");
        }
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<FileInfo> list(String resourceType, UUID resourceId) {
        List<UUID> ids = jdbc.queryForList("select id from files.file_metadata where deleted_at is null "
                + "and (?::text is null or resource_type = ?) and (?::uuid is null or resource_id = ?) "
                + "order by created_at desc limit 500", UUID.class, resourceType, resourceType, resourceId, resourceId);
        return ids.stream().map(this::get).toList();
    }

    /** Opens a version for streaming and logs the download. */
    @Transactional
    public Download open(UUID id, Integer version) throws IOException {
        FileInfo info = get(id);
        int v = version == null ? info.currentVersion() : version;
        FileVersion fv = info.versions().stream().filter(x -> x.version() == v).findFirst()
                .orElseThrow(() -> ApiException.notFound("File version"));
        logAccess(id, v, "download");
        return new Download(storage.open(storageKey(id, v)), info.originalName(), fv.contentType(), fv.sizeBytes());
    }

    @Transactional
    public DownloadUrl downloadUrl(UUID id, Integer version) {
        FileInfo info = get(id);
        int v = version == null ? info.currentVersion() : version;
        FileVersion fv = info.versions().stream().filter(x -> x.version() == v).findFirst()
                .orElseThrow(() -> ApiException.notFound("File version"));
        logAccess(id, v, "download_url");
        Optional<URI> direct = storage.presignedDownload(storageKey(id, v), info.originalName(), fv.contentType(), presignTtl);
        return direct.map(uri -> new DownloadUrl(uri.toString(), Instant.now().plus(presignTtl), true))
                .orElseGet(() -> new DownloadUrl("/api/v1/files/" + id + "/content?version=" + v, null, false));
    }

    /** Soft delete: the record disappears from lists; bytes and history stay for the audit trail. */
    @Transactional
    public void delete(UUID id) {
        get(id);
        jdbc.update("update files.file_metadata set deleted_at = now() where id = ?", id);
        logAccess(id, null, "delete");
    }

    public record Download(InputStream stream, String fileName, String contentType, long size) {
        public String contentDisposition() {
            return ContentDisposition.attachment(fileName);
        }
    }

    // ------------------------------------------------------------------ internals

    private record Stored(String name, String contentType, long size, String sha256, String scanStatus) {}

    private Stored store(MultipartFile file, UUID fileId, int version) {
        if (file == null || file.isEmpty()) {
            throw FieldValidationException.of("file", "Choose a file to upload");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "ESG-413", "Files can be at most " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String name = sanitizeName(file.getOriginalFilename());
        Path temp = null;
        try {
            temp = Files.createTempFile("esg-upload-", ".bin");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest); OutputStream out = Files.newOutputStream(temp)) {
                size = in.transferTo(out);
            }
            byte[] head = new byte[4096];
            int read;
            try (InputStream in = Files.newInputStream(temp)) {
                read = in.readNBytes(head, 0, head.length);
            }
            String contentType = FileTypePolicy.check(name, java.util.Arrays.copyOf(head, read));
            VirusScanner.Result scan = scanner.scan(temp);
            if (scan == VirusScanner.Result.INFECTED) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ESG-422-MALWARE", "The file failed the virus scan and was not stored");
            }
            storage.put(storageKey(fileId, version), temp, size, contentType);
            return new Stored(name, contentType, size, HexFormat.of().formatHex(digest.digest()),
                    scan == VirusScanner.Result.CLEAN ? "clean" : "not_scanned");
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the file", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // temp file clean-up is best effort
                }
            }
        }
    }

    private void insertVersion(UUID fileId, int version, Stored s) {
        jdbc.update("insert into files.file_versions (file_id, version_no, storage_key, content_type, size_bytes, sha256, scan_status) "
                + "values (?, ?, ?, ?, ?, ?, ?)", fileId, version, storageKey(fileId, version), s.contentType(), s.size(), s.sha256(),
                s.scanStatus());
    }

    private List<FileVersion> versions(UUID fileId) {
        return jdbc.query("select version_no, content_type, size_bytes, sha256, scan_status, created_at, created_by "
                        + "from files.file_versions where file_id = ? order by version_no desc",
                (rs, i) -> new FileVersion(rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toInstant(), rs.getObject(7, UUID.class)), fileId);
    }

    private void logAccess(UUID fileId, Integer version, String action) {
        String ip = TenantContext.getClientIp();
        jdbc.update("insert into files.file_access_log (file_id, version_no, action, ip_address) values (?, ?, ?, ?::inet)",
                fileId, version, action, ip == null || ip.isBlank() ? null : ip);
    }

    private static String storageKey(UUID fileId, int version) {
        return TenantContext.requireTenantId() + "/" + fileId + "/v" + version;
    }

    private static String sanitizeName(String original) {
        String name = original == null ? "file" : Path.of(original.replace('\\', '/')).getFileName().toString();
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
