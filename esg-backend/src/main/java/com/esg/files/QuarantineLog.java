package com.esg.files;

import com.esg.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes the audit row for a quarantined file (S6): action {@code quarantined} on files.file_metadata, with the
 * name, hash, size, scanner and threat. Its own transaction, because a refused upload rolls back everything else.
 */
@Component
public class QuarantineLog {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public QuarantineLog(JdbcTemplate jdbc, PlatformTransactionManager txManager, ObjectMapper json) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = json;
    }

    /** @param when "upload" (refused at upload) or "rescan" (found later by the rescan job) */
    public void record(UUID fileId, int version, String name, String sha256, long size, String scanner, String signature,
                       String when) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fileName", name);
        details.put("version", version);
        details.put("sha256", sha256);
        details.put("sizeBytes", size);
        details.put("scanner", scanner);
        details.put("threat", signature);
        details.put("detectedAt", when);
        String body;
        try {
            body = json.writeValueAsString(details);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        String ip = TenantContext.getClientIp();
        tx.executeWithoutResult(s -> jdbc.update(
                "insert into audit.audit_logs (action, resource, resource_id, new_value, ip_address, user_agent) "
                        + "values ('quarantined', 'files.file_metadata', ?, ?::jsonb, ?::inet, ?)",
                fileId, body, ip == null || ip.isBlank() ? null : ip, TenantContext.getUserAgent()));
    }
}
