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
 * File service (TRD §6.6.4): upload with type/size checks and virus scan, immutable versions, downloads
 * (streamed or pre-signed), soft delete, and an access log of every upload/download. Metadata is tenant data
 * under RLS; bytes go to {@link FileStorage} under a tenant-prefixed key.
 *
 * <p>Virus scanning (S6): every upload is scanned before it is accepted. Clean files are stored {@code clean};
 * infected files are stored under a {@code quarantine/} key only, recorded in the audit trail and refused (422
 * ESG-422-MALWARE); if the scanner can't be reached the file is stored {@code pending_scan} and {@link VirusScanService}
 * retries. Only clean files (and files the platform generated itself) can be downloaded or read by imports:
 * pending ones answer 409 ESG-409-SCAN-PENDING, quarantined ones 409 ESG-409-QUARANTINED.
 */
@Service
public class FileService {

    private static final String RESOURCE_TYPE_REGEX = "^[a-z_]{1,100}$";

    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final VirusScanner scanner;
    private final QuarantineLog quarantine;
    private final long maxBytes;
    private final Duration presignTtl;

    public FileService(JdbcTemplate jdbc, FileStorage storage, VirusScanner scanner, QuarantineLog quarantine,
                       @Value("${esg.files.max-bytes}") long maxBytes,
                       @Value("${esg.files.presign-ttl}") Duration presignTtl) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.scanner = scanner;
        this.quarantine = quarantine;
        this.maxBytes = maxBytes;
        this.presignTtl = presignTtl;
    }

    public StorageInfo info() {
        return new StorageInfo(storage.description(), maxBytes, FileTypePolicy.ALLOWED_EXTENSIONS, scanner.description(),
                scanner.sandbox());
    }

    @Transactional
    public FileInfo upload(MultipartFile file, String resourceType, UUID resourceId) {
        if (resourceType != null && !resourceType.isBlank() && !resourceType.matches(RESOURCE_TYPE_REGEX)) {
            throw FieldValidationException.of("resourceType", "lower-case letters and underscores only");
        }
        UUID fileId = UUID.randomUUID();
        Stored stored = store(file, fileId, 1, false);
        jdbc.update("insert into files.file_metadata (id, original_name, content_type, size_bytes, sha256, resource_type, resource_id) "
                        + "values (?, ?, ?, ?, ?, ?, ?)", fileId, stored.name(), stored.contentType(), stored.size(), stored.sha256(),
                blankToNull(resourceType), resourceId);
        insertVersion(fileId, 1, stored);
        logAccess(fileId, 1, "upload");
        return get(fileId);
    }

    /** Stores a file the platform produced (e.g. a report export) through the same checks, scan and storage as uploads. */
    @Transactional
    public FileInfo storeGenerated(byte[] content, String fileName, String contentType, String resourceType, UUID resourceId) {
        UUID fileId = UUID.randomUUID();
        Stored stored = store(new GeneratedFile(content, fileName, contentType), fileId, 1, true);
        jdbc.update("insert into files.file_metadata (id, original_name, content_type, size_bytes, sha256, resource_type, resource_id) "
                        + "values (?, ?, ?, ?, ?, ?, ?)", fileId, stored.name(), stored.contentType(), stored.size(), stored.sha256(),
                blankToNull(resourceType), resourceId);
        insertVersion(fileId, 1, stored);
        logAccess(fileId, 1, "upload");
        return get(fileId);
    }

    /** Minimal in-memory MultipartFile for generated content. */
    private record GeneratedFile(byte[] content, String fileName, String type) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return fileName; }
        @Override public String getContentType() { return type; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content.clone(); }
        @Override public InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException { Files.write(dest.toPath(), content); }
    }

    @Transactional
    public FileInfo addVersion(UUID fileId, MultipartFile file) {
        FileInfo current = get(fileId);
        int next = current.currentVersion() + 1;
        Stored stored = store(file, fileId, next, false);
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
                (rs, i) -> {
                    List<FileVersion> versions = versions(id);
                    int current = rs.getInt("current_version");
                    String scanStatus = versions.stream().filter(v -> v.version() == current).map(FileVersion::scanStatus)
                            .findFirst().orElse("pending_scan");
                    return new FileInfo(rs.getObject("id", UUID.class), rs.getString("original_name"), rs.getString("content_type"),
                            rs.getLong("size_bytes"), rs.getString("sha256"), current, rs.getString("resource_type"),
                            rs.getObject("resource_id", UUID.class), rs.getTimestamp("created_at").toInstant(), versions,
                            scanStatus);
                }, id);
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
        requireReadable(fv);
        logAccess(id, v, "download");
        return new Download(storage.open(storageKey(id, v)), info.originalName(), fv.contentType(), fv.sizeBytes());
    }

    @Transactional
    public DownloadUrl downloadUrl(UUID id, Integer version) {
        FileInfo info = get(id);
        int v = version == null ? info.currentVersion() : version;
        FileVersion fv = info.versions().stream().filter(x -> x.version() == v).findFirst()
                .orElseThrow(() -> ApiException.notFound("File version"));
        requireReadable(fv);
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

    private record Stored(String name, String contentType, long size, String sha256, String scanStatus, String scanner,
                          String scanDetail) {}

    /** Only scanned-clean or platform-generated versions may leave storage (S6). */
    private static void requireReadable(FileVersion fv) {
        switch (fv.scanStatus()) {
            case "clean", "generated" -> { }
            case "infected" -> throw ApiException.conflict("ESG-409-QUARANTINED",
                    "This file failed the virus scan and is quarantined; it can't be downloaded");
            default -> throw ApiException.conflict("ESG-409-SCAN-PENDING",
                    "This file is waiting for its virus scan; try again in a few minutes");
        }
    }

    /**
     * Checks, scans and stores one version. {@code generated}: content the platform produced itself (report exports),
     * which isn't scanned. Infected uploads go to a quarantine key, are audited, and are refused.
     */
    private Stored store(MultipartFile file, UUID fileId, int version, boolean generated) {
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
            String sha256 = HexFormat.of().formatHex(digest.digest());
            if (generated) {
                storage.put(storageKey(fileId, version), temp, size, contentType);
                return new Stored(name, contentType, size, sha256, "generated", "platform", null);
            }
            VirusScanner.Result scan = scanner.scan(temp);
            if (scan.verdict() == VirusScanner.Verdict.INFECTED) {
                storage.put("quarantine/" + storageKey(fileId, version), temp, size, contentType);
                quarantine.record(fileId, version, name, sha256, size, scanner.name(), scan.signature(), "upload");
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ESG-422-MALWARE",
                        "The file failed the virus scan (" + scan.signature() + ") and was quarantined, not stored");
            }
            storage.put(storageKey(fileId, version), temp, size, contentType);
            boolean clean = scan.verdict() == VirusScanner.Verdict.CLEAN;
            return new Stored(name, contentType, size, sha256, clean ? "clean" : "pending_scan", scanner.name(),
                    clean ? null : truncate(scan.signature()));
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
        jdbc.update("insert into files.file_versions (file_id, version_no, storage_key, content_type, size_bytes, sha256, scan_status, "
                + "scanned_at, scanner, scan_detail) values (?, ?, ?, ?, ?, ?, ?, case when ? = 'pending_scan' then null else now() end, ?, ?)",
                fileId, version, storageKey(fileId, version), s.contentType(), s.size(), s.sha256(), s.scanStatus(), s.scanStatus(),
                s.scanner(), s.scanDetail());
    }

    private List<FileVersion> versions(UUID fileId) {
        return jdbc.query("select version_no, content_type, size_bytes, sha256, scan_status, created_at, created_by, scanned_at, scanner, "
                        + "scan_detail from files.file_versions where file_id = ? order by version_no desc",
                (rs, i) -> new FileVersion(rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toInstant(), rs.getObject(7, UUID.class),
                        rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), rs.getString(9), rs.getString(10)), fileId);
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

    private static String truncate(String value) {
        return value == null ? null : value.length() > 255 ? value.substring(0, 255) : value;
    }
}
