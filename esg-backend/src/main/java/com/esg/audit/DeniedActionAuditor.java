package com.esg.audit;

import com.esg.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records refused actions in the audit trail (action {@code denied}): missing permission, segregation of duties,
 * locked periods, privilege escalation, self-lockout and malware. The refused request's own transaction has rolled
 * back, so this writes in a new one. Database triggers only see writes that happened; this covers the ones that
 * didn't. Anonymous requests (no tenant) aren't recorded here: failed sign-ins are tracked on the user row.
 */
@Component
public class DeniedActionAuditor {

    /**
     * Codes recorded besides every 403: refusals that aren't about the request being malformed. Malware uploads
     * (ESG-422-MALWARE) have their own, richer {@code quarantined} row (files.QuarantineLog).
     */
    static final Set<String> DENIAL_CODES = Set.of("ESG-409-LOCKED", "ESG-409-SOD", "ESG-400-SELF", "ESG-409-SEATS",
            "ESG-409-FACTOR-IN-USE", "ESG-409-FACTOR-PENDING", "ESG-409-QUARANTINED", "ESG-409-SERIAL");
    private static final Pattern UUID_IN_PATH = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Logger log = LoggerFactory.getLogger(DeniedActionAuditor.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public DeniedActionAuditor(JdbcTemplate jdbc, PlatformTransactionManager txManager, ObjectMapper json) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = json;
    }

    public static boolean isDenial(HttpStatus status, String code) {
        return status == HttpStatus.FORBIDDEN || (code != null && DENIAL_CODES.contains(code));
    }

    /** Writes one {@code denied} row; never throws (the caller is already answering with an error). */
    public void record(HttpServletRequest request, HttpStatus status, String code, String detail) {
        if (!isDenial(status, code) || TenantContext.getTenantId() == null) {
            return;
        }
        try {
            String path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
            UUID target = null;
            Matcher m = UUID_IN_PATH.matcher(path);
            while (m.find()) {
                target = UUID.fromString(m.group());
            }
            String resource = (request.getMethod() + " " + UUID_IN_PATH.matcher(path).replaceAll("{id}"));
            write(resource, target, request.getMethod(), path, status, code, detail);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Could not record a denied action on {}", request.getRequestURI(), e);
        }
    }

    /**
     * Phase 3 (2): a refusal inside a request that otherwise succeeds — one row of a bulk action. {@code method} and
     * {@code path} describe the single-item call the row stands for, e.g. PUT /v1/data/points/{id}/approve.
     */
    public void recordAction(String method, String pathTemplate, UUID resourceId, HttpStatus status, String code, String detail) {
        if (!isDenial(status, code) || TenantContext.getTenantId() == null) {
            return;
        }
        try {
            write(method + " " + pathTemplate, resourceId, method,
                    resourceId == null ? pathTemplate : pathTemplate.replace("{id}", resourceId.toString()), status, code, detail);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Could not record a denied action on {}", pathTemplate, e);
        }
    }

    private void write(String resource, UUID resourceId, String method, String path, HttpStatus status, String code, String detail)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", method);
        details.put("path", path);
        details.put("status", status.value());
        details.put("errorCode", code);
        details.put("detail", detail);
        String body = json.writeValueAsString(details);
        tx.executeWithoutResult(s -> jdbc.update(
                "insert into audit.audit_logs (action, resource, resource_id, new_value, ip_address, user_agent) "
                        + "values ('denied', ?, ?, ?::jsonb, ?::inet, ?)",
                resource.length() > 100 ? resource.substring(0, 100) : resource, resourceId, body,
                blankToNull(TenantContext.getClientIp()), truncate(TenantContext.getUserAgent())));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value) {
        return value == null ? null : value.length() > 500 ? value.substring(0, 500) : value;
    }
}
