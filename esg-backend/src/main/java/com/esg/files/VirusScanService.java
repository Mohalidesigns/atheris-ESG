package com.esg.files;

import com.esg.security.TenantContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rescans file versions left {@code pending_scan} (scanner unreachable at upload, or files stored before V023; S6).
 * Clean ones become downloadable; infected ones are marked {@code infected} (quarantined: never served again) and
 * audited. Runs per tenant under its own RLS context; scheduled by {@link FileScanScheduling}.
 */
@Service
public class VirusScanService {

    private static final Logger log = LoggerFactory.getLogger(VirusScanService.class);
    private static final int BATCH = 200;

    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final VirusScanner scanner;
    private final QuarantineLog quarantine;
    private final TransactionTemplate tx;

    public VirusScanService(JdbcTemplate jdbc, FileStorage storage, VirusScanner scanner, QuarantineLog quarantine,
                            PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.scanner = scanner;
        this.quarantine = quarantine;
        this.tx = new TransactionTemplate(txManager);
    }

    public record ScanRun(int tenants, int clean, int infected, int stillPending) {}

    /** Every tenant with pending files. */
    public ScanRun runOnce() {
        String previousTenant = TenantContext.getTenantId();
        String previousUser = TenantContext.getUserId();
        List<UUID> tenants = jdbc.queryForList("select tenant_id from files.tenants_with_pending_scans()", UUID.class);
        int[] totals = new int[3];
        try {
            for (UUID tenant : tenants) {
                TenantContext.setTenantId(tenant.toString());
                TenantContext.setUserId(null);
                try {
                    int[] r = runTenant();
                    for (int i = 0; i < 3; i++) {
                        totals[i] += r[i];
                    }
                } catch (RuntimeException e) {
                    log.warn("Virus rescan for tenant {} failed: {}", tenant, e.getMessage());
                }
            }
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
        }
        return new ScanRun(tenants.size(), totals[0], totals[1], totals[2]);
    }

    private int[] runTenant() {
        int[] counts = new int[3];
        List<Map<String, Object>> pending = jdbc.queryForList("select v.id, v.file_id, v.version_no, v.storage_key, v.sha256, "
                + "v.size_bytes, m.original_name from files.file_versions v join files.file_metadata m on m.id = v.file_id "
                + "where v.scan_status = 'pending_scan' order by v.created_at limit " + BATCH);
        for (Map<String, Object> row : pending) {
            UUID versionId = (UUID) row.get("id");
            VirusScanner.Result result = scan((String) row.get("storage_key"));
            switch (result.verdict()) {
                case CLEAN -> {
                    mark(versionId, "clean", null);
                    counts[0]++;
                }
                case INFECTED -> {
                    mark(versionId, "infected", result.signature());
                    quarantine.record((UUID) row.get("file_id"), (Integer) row.get("version_no"), (String) row.get("original_name"),
                            (String) row.get("sha256"), ((Number) row.get("size_bytes")).longValue(), scanner.name(),
                            result.signature(), "rescan");
                    counts[1]++;
                }
                case UNAVAILABLE -> {
                    tx.executeWithoutResult(s -> jdbc.update("update files.file_versions set scan_detail = ?, scanner = ? where id = ?",
                            truncate(result.signature()), scanner.name(), versionId));
                    counts[2]++;
                }
            }
        }
        return counts;
    }

    private VirusScanner.Result scan(String key) {
        Path temp = null;
        try {
            temp = Files.createTempFile("esg-rescan-", ".bin");
            try (InputStream in = storage.open(key)) {
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return scanner.scan(temp);
        } catch (IOException e) {
            return VirusScanner.Result.unavailable("Could not read stored file: " + e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private void mark(UUID versionId, String status, String detail) {
        tx.executeWithoutResult(s -> jdbc.update("update files.file_versions set scan_status = ?, scanned_at = now(), scanner = ?, "
                + "scan_detail = ? where id = ? and scan_status = 'pending_scan'", status, scanner.name(), truncate(detail), versionId));
    }

    private static String truncate(String value) {
        return value == null ? null : value.length() > 255 ? value.substring(0, 255) : value;
    }
}
