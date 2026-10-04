package com.esg.offline;

import com.esg.audit.DeniedActionAuditor;
import com.esg.datahub.DataHubDtos.DataPointResponse;
import com.esg.datahub.DataPointService;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.model.dto.DataPointDto;
import com.esg.offline.OfflineDtos.Conflict;
import com.esg.offline.OfflineDtos.ResolveRequest;
import com.esg.offline.OfflineDtos.SyncOperation;
import com.esg.offline.OfflineDtos.SyncRequest;
import com.esg.offline.OfflineDtos.SyncResponse;
import com.esg.offline.OfflineDtos.SyncResult;
import com.esg.security.CurrentUser;
import com.esg.social.SafetyService;
import com.esg.social.SocialDtos.IncidentRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * WP-43 OfflineSyncResolver (TRD §8.1, D43). Applies what a device saved offline, one operation at a time, each in its
 * own transaction with the same rules as the forms (validation, period locks, org scope), so one refusal never blocks
 * the rest.
 * <ul>
 *   <li><b>Idempotent:</b> an operation already received (same {@code opId}) returns its first result.</li>
 *   <li><b>Own record:</b> a record this device already synced (same {@code clientId}) is updated if the server copy is
 *       still the version the device last saw and still editable; otherwise it is a conflict.</li>
 *   <li><b>Conflict key</b> (tenant, org, metric_key, period): a new offline value meets a value already on the server.
 *       Last write wins: if the server value is an editable draft saved before the offline value was captured, the
 *       offline value replaces it (the audit trail keeps the old one); otherwise the conflict waits for the person to
 *       choose — keep the server's value or use theirs (manual override).</li>
 * </ul>
 */
@Service
public class OfflineSyncService {

    private static final Logger log = LoggerFactory.getLogger(OfflineSyncService.class);
    static final String APPLIED = "applied";
    static final String CONFLICT = "conflict";
    static final String REJECTED = "rejected";
    static final String DISCARDED = "discarded";
    private static final Set<String> EDITABLE = Set.of(DataPointService.DRAFT, DataPointService.REJECTED);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.of("Africa/Lagos"));

    private final DataPointService points;
    private final SafetyService safety;
    private final DeniedActionAuditor denied;
    private final Validator validator;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate perOp;

    public OfflineSyncService(DataPointService points, SafetyService safety, DeniedActionAuditor denied, Validator validator,
                              JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager txManager) {
        this.points = points;
        this.safety = safety;
        this.denied = denied;
        this.validator = validator;
        this.jdbc = jdbc;
        this.json = json;
        this.perOp = new TransactionTemplate(txManager);
        this.perOp.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ================================================================== sync

    public SyncResponse sync(SyncRequest request) {
        List<SyncResult> results = new ArrayList<>(request.operations().size());
        for (SyncOperation op : request.operations()) {
            results.add(one(request.deviceId().trim(), op));
        }
        int applied = (int) results.stream().filter(r -> APPLIED.equals(r.outcome())).count();
        int conflicts = (int) results.stream().filter(r -> CONFLICT.equals(r.outcome())).count();
        return new SyncResponse(results.size(), applied, conflicts, results.size() - applied - conflicts, results);
    }

    private SyncResult one(String deviceId, SyncOperation op) {
        SyncResult earlier = replay(op.opId());
        if (earlier != null) {
            return earlier;
        }
        try {
            return perOp.execute(s -> "data_point".equals(op.kind()) ? dataPoint(deviceId, op) : incident(deviceId, op));
        } catch (DuplicateKeyException e) {
            // The same operation arrived twice at the same moment (two tabs, a retried request): the first one won.
            SyncResult first = replay(op.opId());
            if (first != null) {
                return first;
            }
            throw e;
        } catch (FieldValidationException e) {
            return refused(deviceId, op, "Some values need correcting", e.getErrors(), null);
        } catch (ApiException e) {
            return refused(deviceId, op, e.getMessage(), Map.of(), e);
        } catch (DataAccessException e) {
            String state = sqlState(e);
            if ("P0L01".equals(state) || "P0L02".equals(state)) {
                return refused(deviceId, op, "The reporting period is locked", Map.of(), null);
            }
            log.warn("Offline sync failed for operation {}", op.opId(), e);
            return refused(deviceId, op, "Couldn't save this record — try again later", Map.of(), null);
        }
    }

    private SyncResult dataPoint(String deviceId, SyncOperation op) {
        DataPointDto dto = op.dataPoint();
        if (dto == null) {
            throw FieldValidationException.of("dataPoint", "is required");
        }
        requireValid(dto);
        if (!CurrentUser.hasPermission("data_points:create")) {
            throw ApiException.forbidden("You can't enter data points");
        }
        Instant captured = capturedAt(op);

        // An offline edit of a record this device already synced (it may not have received the result).
        UUID target = op.recordId();
        Long base = op.baseVersion();
        if (target == null) {
            Map<String, Object> mine = lastApplied(op.clientId());
            if (mine != null) {
                target = (UUID) mine.get("record_id");
                base = ((Number) mine.get("record_version")).longValue();
            }
        }
        if (target != null) {
            ServerPoint server = serverPoint(target);
            if (server == null) {
                return record(deviceId, op, REJECTED, null, null, "This record was deleted on the server", null, null, null);
            }
            if (!EDITABLE.contains(server.status())) {
                return conflict(deviceId, op, server, "It is " + server.status() + " on the server, so it can no longer be changed from this device");
            }
            if (base == null || base != server.version()) {
                return conflict(deviceId, op, server, "It was changed on the server after this device last saw it");
            }
            requireEdit();
            return update(deviceId, op, dto, server, null);
        }

        ServerPoint existing = byConflictKey(dto);
        if (existing == null) {
            DataPointResponse created = points.create(dto);
            return record(deviceId, op, APPLIED, created.id(), created.version(), "Saved", null, null, null);
        }
        if (!EDITABLE.contains(existing.status())) {
            return conflict(deviceId, op, existing, "A value for this metric and period is already " + existing.status() + " on the server");
        }
        if (!existing.updatedAt().isBefore(captured)) {
            return conflict(deviceId, op, existing, "A value for this metric and period was saved on the server on "
                    + WHEN.format(existing.updatedAt()) + ", after you captured yours");
        }
        if (!CurrentUser.hasPermission("data_points:update")) {
            return conflict(deviceId, op, existing, "A value for this metric and period is already on the server and you can't edit it");
        }
        // Last write wins: the offline value is newer than the server's draft.
        return update(deviceId, op, dto, existing, "Replaced the value saved on the server on " + WHEN.format(existing.updatedAt()));
    }

    private SyncResult update(String deviceId, SyncOperation op, DataPointDto dto, ServerPoint server, String message) {
        dto.setVersion(server.version());
        DataPointResponse saved = points.update(server.id(), dto);
        return record(deviceId, op, APPLIED, saved.id(), saved.version(), message == null ? "Saved" : message, null, null, null);
    }

    private SyncResult incident(String deviceId, SyncOperation op) {
        IncidentRequest r = op.incident();
        if (r == null) {
            throw FieldValidationException.of("incident", "is required");
        }
        requireValid(r);
        if (!CurrentUser.hasPermission("social:create")) {
            throw ApiException.forbidden("You can't report incidents");
        }
        capturedAt(op);
        // An incident is reported once; a resend of the same record (new operation, same clientId) is the same report.
        Map<String, Object> mine = lastApplied(op.clientId());
        if (mine != null) {
            return record(deviceId, op, APPLIED, (UUID) mine.get("record_id"), ((Number) mine.get("record_version")).longValue(),
                    "Already reported", null, null, null);
        }
        var created = safety.create(r);
        return record(deviceId, op, APPLIED, created.id(), created.version(), "Reported", null, null, null);
    }

    private SyncResult conflict(String deviceId, SyncOperation op, ServerPoint server, String reason) {
        return record(deviceId, op, CONFLICT, server.id(), null, "Needs your decision", null, reason, server.version());
    }

    /** A refused operation is recorded too (its transaction rolled back), so resending it gives the same answer. */
    private SyncResult refused(String deviceId, SyncOperation op, String message, Map<String, String> errors, ApiException e) {
        if (e != null) {
            denied.recordAction("POST", "/v1/data/sync", op.clientId(), e.getStatus(), e.getCode(), e.getMessage());
        }
        try {
            return perOp.execute(s -> record(deviceId, op, REJECTED, null, null, message, errors.isEmpty() ? null : errors, null, null));
        } catch (DuplicateKeyException race) {
            SyncResult first = replay(op.opId());
            if (first != null) {
                return first;
            }
            throw race;
        }
    }

    private SyncResult record(String deviceId, SyncOperation op, String status, UUID recordId, Long recordVersion, String message,
                              Map<String, String> errors, String conflictReason, Long serverVersion) {
        Object body = "data_point".equals(op.kind()) ? op.dataPoint() : op.incident();
        UUID id = UUID.randomUUID();
        jdbc.update("insert into data_hub.offline_sync_queue (id, op_id, client_id, kind, device_id, captured_at, base_version, payload, "
                        + "status, record_id, record_version, message, errors, conflict_reason, server_version) "
                        + "values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb), ?, ?)",
                id, op.opId(), op.clientId(), op.kind(), deviceId, Timestamp.from(op.capturedAt()), op.baseVersion(),
                write(body == null ? Map.of() : body), status, recordId, recordVersion, message, errors == null ? null : write(errors),
                conflictReason, serverVersion);
        String shown = CONFLICT.equals(status) ? conflictReason : message;
        return new SyncResult(op.opId(), op.clientId(), op.kind(), status, recordId, recordVersion, shown,
                errors == null ? Map.of() : errors, false, id);
    }

    private SyncResult replay(UUID opId) {
        List<SyncResult> found = jdbc.query("select id, op_id, client_id, kind, status, record_id, record_version, message, errors, "
                + "conflict_reason, resolution from data_hub.offline_sync_queue where op_id = ? and user_id = auth.current_user_id()", (rs, i) -> {
                    String status = rs.getString("status");
                    String shown = CONFLICT.equals(status) ? rs.getString("conflict_reason") : rs.getString("message");
                    Long version = rs.getObject("record_version") == null ? null : rs.getLong("record_version");
                    // A resolved conflict reports how it ended (use_mine → applied; keep_server → discarded).
                    return new SyncResult(rs.getObject("op_id", UUID.class), rs.getObject("client_id", UUID.class), rs.getString("kind"),
                            status, rs.getObject("record_id", UUID.class), version, shown, readErrors(rs.getString("errors")), true,
                            rs.getObject("id", UUID.class));
                }, opId);
        return found.isEmpty() ? null : found.get(0);
    }

    // ================================================================== conflicts

    @Transactional(readOnly = true)
    public List<Conflict> conflicts() {
        List<Conflict> result = new ArrayList<>();
        jdbc.query("select id, op_id, client_id, device_id, captured_at, created_at, conflict_reason, payload::text, record_id "
                + "from data_hub.offline_sync_queue where user_id = auth.current_user_id() and status = 'conflict' and kind = 'data_point' "
                + "order by created_at, id", rs -> {
                    UUID recordId = rs.getObject("record_id", UUID.class);
                    DataPointResponse server = null;
                    if (recordId != null && serverPoint(recordId) != null) {
                        try {
                            server = points.get(recordId);
                        } catch (ApiException e) {
                            server = null; // outside the person's org scope now
                        }
                    }
                    result.add(new Conflict(rs.getObject("id", UUID.class), rs.getObject("op_id", UUID.class),
                            rs.getObject("client_id", UUID.class), rs.getString("device_id"), rs.getTimestamp("captured_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant(), rs.getString("conflict_reason"), readPoint(rs.getString(8)), server));
                });
        return result;
    }

    /**
     * The person decides: keep the server's value (their offline value is discarded) or use theirs (manual override:
     * the server value is replaced, provided it is still editable and still the version they looked at).
     */
    @Transactional
    public SyncResult resolve(UUID id, ResolveRequest r) {
        List<Map<String, Object>> rows = jdbc.queryForList("select op_id, client_id, kind, payload::text as payload, record_id, status "
                + "from data_hub.offline_sync_queue where id = ? and user_id = auth.current_user_id() for update", id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Sync conflict");
        }
        Map<String, Object> row = rows.get(0);
        if (!CONFLICT.equals(row.get("status"))) {
            throw ApiException.conflict("ESG-409-STATE", "This conflict has already been resolved");
        }
        UUID opId = (UUID) row.get("op_id");
        UUID clientId = (UUID) row.get("client_id");
        if ("keep_server".equals(r.resolution())) {
            jdbc.update("update data_hub.offline_sync_queue set status = 'discarded', resolution = 'keep_server', resolved_at = now(), "
                    + "resolved_by = auth.current_user_id(), message = 'Kept the server value' where id = ?", id);
            return new SyncResult(opId, clientId, (String) row.get("kind"), DISCARDED, (UUID) row.get("record_id"), null,
                    "Kept the server value", Map.of(), false, id);
        }
        requireEdit();
        DataPointDto mine = readPoint((String) row.get("payload"));
        UUID recordId = (UUID) row.get("record_id");
        ServerPoint server = recordId == null ? null : serverPoint(recordId);
        DataPointResponse saved;
        if (server == null) {
            // The server value has gone (deleted): theirs becomes the value.
            saved = points.create(mine);
        } else {
            if (r.version() == null) {
                throw FieldValidationException.of("version", "is required");
            }
            if (!EDITABLE.contains(server.status())) {
                throw ApiException.conflict("ESG-409-STATE", "The server value is " + server.status()
                        + ": it must be rejected or reopened before it can be replaced");
            }
            mine.setVersion(r.version());
            saved = points.update(server.id(), mine);
        }
        jdbc.update("update data_hub.offline_sync_queue set status = 'applied', resolution = 'use_mine', resolved_at = now(), "
                + "resolved_by = auth.current_user_id(), record_id = ?, record_version = ?, message = 'Replaced the server value' where id = ?",
                saved.id(), saved.version(), id);
        return new SyncResult(opId, clientId, (String) row.get("kind"), APPLIED, saved.id(), saved.version(), "Replaced the server value",
                Map.of(), false, id);
    }

    // ================================================================== helpers

    private record ServerPoint(UUID id, long version, String status, Instant updatedAt) {}

    private ServerPoint serverPoint(UUID id) {
        List<ServerPoint> found = jdbc.query("select id, version, validation_status, updated_at from data_hub.data_points "
                + "where id = ? and deleted_at is null", (rs, i) -> point(rs), id);
        return found.isEmpty() ? null : found.get(0);
    }

    private ServerPoint byConflictKey(DataPointDto dto) {
        List<ServerPoint> found = jdbc.query("select id, version, validation_status, updated_at from data_hub.data_points "
                        + "where org_id = ? and metric_key = ? and reporting_period_start = ? and reporting_period_end = ? and deleted_at is null "
                        + "order by updated_at desc limit 1", (rs, i) -> point(rs),
                dto.getOrgId(), dto.getMetricKey().trim(), java.sql.Date.valueOf(dto.getReportingPeriodStart()),
                java.sql.Date.valueOf(dto.getReportingPeriodEnd()));
        return found.isEmpty() ? null : found.get(0);
    }

    private static ServerPoint point(java.sql.ResultSet rs) throws SQLException {
        return new ServerPoint(rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3), rs.getTimestamp(4).toInstant());
    }

    /** The record this person's device last synced under {@code clientId}, with the version it got back. */
    private Map<String, Object> lastApplied(UUID clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select record_id, record_version from data_hub.offline_sync_queue "
                + "where client_id = ? and user_id = auth.current_user_id() and status = 'applied' and record_id is not null "
                + "order by created_at desc, id desc limit 1", clientId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Device clocks drift: a capture time in the future counts as now (it can't win a conflict it didn't earn). */
    private static Instant capturedAt(SyncOperation op) {
        Instant now = Instant.now();
        return op.capturedAt().isAfter(now) ? now : op.capturedAt();
    }

    private static void requireEdit() {
        if (!CurrentUser.hasPermission("data_points:update")) {
            throw ApiException.forbidden("You can't edit data points");
        }
    }

    private <T> void requireValid(T body) {
        Set<ConstraintViolation<T>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();
            violations.stream().sorted((a, b) -> a.getPropertyPath().toString().compareTo(b.getPropertyPath().toString()))
                    .forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
            throw new FieldValidationException(errors);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private DataPointDto readPoint(String payload) {
        try {
            return json.readValue(payload, DataPointDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> readErrors(String errors) {
        if (errors == null) {
            return Map.of();
        }
        try {
            return json.readValue(errors, json.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && s.getSQLState() != null) {
                return s.getSQLState();
            }
        }
        return "";
    }
}
