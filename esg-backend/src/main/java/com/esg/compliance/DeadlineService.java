package com.esg.compliance;

import com.esg.compliance.ComplianceDtos.DeadlineRequest;
import com.esg.compliance.ComplianceDtos.DeadlineResponse;
import com.esg.compliance.ComplianceDtos.ReminderSent;
import com.esg.compliance.ComplianceDtos.SubmitRequest;
import com.esg.compliance.ComplianceDtos.WaiveRequest;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.reference.FrameworkCatalogService;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filing deadlines and the filing tracker (TRD §4.5 filing_deadlines, GET /compliance/deadlines; BRD-5.2.3-05; D23 (4)).
 * upcoming → submitted (with the submission reference and evidence) or waived (with a reason); the daily job marks a
 * missed one overdue. Reopen returns a submitted, waived or overdue deadline to upcoming.
 */
@Service
@Transactional(readOnly = true)
public class DeadlineService {

    private static final String SELECT = "select d.*, f.code as framework_code, f.name as framework_name, "
            + "u.first_name || ' ' || u.last_name as owner_name from compliance.filing_deadlines d "
            + "left join compliance.regulatory_frameworks f on f.id = d.framework_id left join auth.users u on u.id = d.owner_user_id ";

    private final JdbcTemplate jdbc;
    private final FrameworkCatalogService catalog;
    private final ComplianceService compliance;

    public DeadlineService(JdbcTemplate jdbc, FrameworkCatalogService catalog, ComplianceService compliance) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.compliance = compliance;
    }

    public List<DeadlineResponse> list(LocalDate from, LocalDate to, String status) {
        StringBuilder where = new StringBuilder("where d.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (from != null) {
            where.append(" and d.deadline_date >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" and d.deadline_date <= ?");
            args.add(Date.valueOf(to));
        }
        if (status != null) {
            if (!List.of("upcoming", "submitted", "overdue", "waived").contains(status)) {
                throw FieldValidationException.of("status", "must be upcoming, submitted, overdue or waived");
            }
            where.append(" and d.status = ?");
            args.add(status);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT + where + " order by d.deadline_date, d.description", args.toArray());
        Map<UUID, List<ReminderSent>> reminders = reminders();
        LocalDate today = compliance.today();
        return rows.stream().map(r -> response(r, reminders, today)).toList();
    }

    public DeadlineResponse get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT + "where d.id = ? and d.deleted_at is null", id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Filing deadline");
        }
        return response(rows.get(0), reminders(), compliance.today());
    }

    @Transactional
    public DeadlineResponse create(DeadlineRequest r) {
        UUID frameworkId = check(r);
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into compliance.filing_deadlines (id, framework_id, description, deadline_date, reporting_year, "
                    + "owner_user_id, reminder_days, notes) values (?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setObject(1, id);
            ps.setObject(2, frameworkId);
            ps.setString(3, r.description().trim());
            ps.setDate(4, Date.valueOf(r.deadlineDate()));
            ps.setObject(5, r.reportingYear());
            ps.setObject(6, r.ownerUserId());
            ps.setArray(7, con.createArrayOf("integer", days(r).toArray()));
            ps.setString(8, blank(r.notes()));
            return ps;
        });
        return get(id);
    }

    @Transactional
    public DeadlineResponse update(UUID id, DeadlineRequest r) {
        DeadlineResponse current = get(id);
        UUID frameworkId = check(r);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        // Moving an overdue deadline to a later date makes it upcoming again.
        String status = "overdue".equals(current.status()) && !r.deadlineDate().isBefore(compliance.today()) ? "upcoming" : current.status();
        int n = jdbc.update(con -> {
            var ps = con.prepareStatement("update compliance.filing_deadlines set framework_id = ?, description = ?, deadline_date = ?, "
                    + "reporting_year = ?, owner_user_id = ?, reminder_days = ?, notes = ?, status = ?, version = version + 1 "
                    + "where id = ? and version = ? and deleted_at is null");
            ps.setObject(1, frameworkId);
            ps.setString(2, r.description().trim());
            ps.setDate(3, Date.valueOf(r.deadlineDate()));
            ps.setObject(4, r.reportingYear());
            ps.setObject(5, r.ownerUserId());
            ps.setArray(6, con.createArrayOf("integer", days(r).toArray()));
            ps.setString(7, blank(r.notes()));
            ps.setString(8, status);
            ps.setObject(9, id);
            ps.setLong(10, r.version());
            return ps;
        });
        conflictIfZero(n);
        return get(id);
    }

    @Transactional
    public DeadlineResponse submit(UUID id, SubmitRequest r) {
        DeadlineResponse d = get(id);
        if (!List.of("upcoming", "overdue").contains(d.status())) {
            throw ApiException.conflict("ESG-409-STATE", "Only an upcoming or overdue filing can be marked submitted");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.submittedOn().isAfter(compliance.today())) {
            errors.put("submittedOn", "can't be in the future");
        }
        List<UUID> evidence = r.evidenceFileIds() == null ? List.of() : r.evidenceFileIds().stream().distinct().toList();
        compliance.checkFiles(evidence, "evidenceFileIds", errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        int n = jdbc.update(con -> {
            var ps = con.prepareStatement("update compliance.filing_deadlines set status = 'submitted', submitted_at = ?, submission_ref = ?, "
                    + "evidence_file_ids = ?, version = version + 1 where id = ? and version = ? and deleted_at is null");
            ps.setObject(1, r.submittedOn().atStartOfDay().atOffset(ZoneOffset.UTC));
            ps.setString(2, r.submissionRef().trim());
            ps.setArray(3, evidence.isEmpty() ? null : con.createArrayOf("uuid", evidence.toArray()));
            ps.setObject(4, id);
            ps.setLong(5, r.version());
            return ps;
        });
        conflictIfZero(n);
        return get(id);
    }

    @Transactional
    public DeadlineResponse waive(UUID id, WaiveRequest r) {
        DeadlineResponse d = get(id);
        if (!List.of("upcoming", "overdue").contains(d.status())) {
            throw ApiException.conflict("ESG-409-STATE", "Only an upcoming or overdue filing can be waived");
        }
        conflictIfZero(jdbc.update("update compliance.filing_deadlines set status = 'waived', waived_reason = ?, version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", r.reason().trim(), id, r.version()));
        return get(id);
    }

    @Transactional
    public DeadlineResponse reopen(UUID id, long version) {
        DeadlineResponse d = get(id);
        if ("upcoming".equals(d.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This filing is already open");
        }
        String status = d.deadlineDate().isBefore(compliance.today()) ? "overdue" : "upcoming";
        conflictIfZero(jdbc.update("update compliance.filing_deadlines set status = ?, submitted_at = null, submission_ref = null, "
                + "evidence_file_ids = null, waived_reason = null, version = version + 1 where id = ? and version = ? and deleted_at is null",
                status, id, version));
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        get(id);
        conflictIfZero(jdbc.update("update compliance.filing_deadlines set deleted_at = now(), version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", id, version));
    }

    private UUID check(DeadlineRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        UUID frameworkId = null;
        if (r.framework() != null && !r.framework().isBlank()) {
            try {
                frameworkId = catalog.framework(r.framework().trim()).id();
            } catch (ApiException e) {
                errors.put("framework", "is not a known framework");
            }
        }
        if (compliance.owners().stream().noneMatch(o -> o.id().equals(r.ownerUserId()))) {
            errors.put("ownerUserId", "must be an active user who can update compliance");
        }
        List<Integer> days = r.reminderDays();
        if (days.stream().distinct().count() != days.size()) {
            errors.put("reminderDays", "contains the same day twice");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return frameworkId;
    }

    private static List<Integer> days(DeadlineRequest r) {
        return r.reminderDays().stream().sorted((a, b) -> b - a).toList();
    }

    private Map<UUID, List<ReminderSent>> reminders() {
        Map<UUID, List<ReminderSent>> out = new HashMap<>();
        jdbc.query("select r.deadline_id, r.kind, r.days_before, r.sent_on, u.first_name || ' ' || u.last_name "
                        + "from compliance.deadline_reminders r join auth.users u on u.id = r.recipient_id order by r.sent_on",
                rs -> {
                    out.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(new ReminderSent(rs.getString(2),
                            rs.getInt(3), rs.getDate(4).toLocalDate(), rs.getString(5)));
                });
        return out;
    }

    private static DeadlineResponse response(Map<String, Object> r, Map<UUID, List<ReminderSent>> reminders, LocalDate today) {
        UUID id = (UUID) r.get("id");
        LocalDate date = ((Date) r.get("deadline_date")).toLocalDate();
        java.sql.Timestamp submitted = (java.sql.Timestamp) r.get("submitted_at");
        return new DeadlineResponse(id, (String) r.get("framework_code"), (String) r.get("framework_name"), (String) r.get("description"),
                date, (Integer) r.get("reporting_year"), (UUID) r.get("owner_user_id"), (String) r.get("owner_name"),
                ints(r.get("reminder_days")), (String) r.get("status"), ChronoUnit.DAYS.between(today, date),
                submitted == null ? null : submitted.toInstant().atOffset(ZoneOffset.UTC).toLocalDate(), (String) r.get("submission_ref"),
                uuidList(r.get("evidence_file_ids")), (String) r.get("waived_reason"), (String) r.get("notes"),
                reminders.getOrDefault(id, List.of()), ((Number) r.get("version")).longValue());
    }

    private static List<Integer> ints(Object array) {
        try {
            return array == null ? List.of() : Arrays.asList((Integer[]) ((java.sql.Array) array).getArray());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<UUID> uuidList(Object array) {
        try {
            return ComplianceService.uuids((java.sql.Array) array);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void conflictIfZero(int n) {
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This filing deadline was changed by someone else. Reload and try again.");
        }
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
