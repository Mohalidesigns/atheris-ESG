package com.esg.social;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.notifications.NotificationService;
import com.esg.security.CurrentUser;
import com.esg.service.ReportingPeriodService;
import com.esg.social.SocialDtos.ActionRequest;
import com.esg.social.SocialDtos.WorkforceLine;
import com.esg.social.SocialDtos.WorkforceLineRequest;
import com.esg.social.SocialDtos.WorkforceSnapshot;
import com.esg.social.SocialDtos.WorkforceSnapshotRequest;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workforce diversity (WP-33; TRD §8.7 DEIMetricsAggregator). A snapshot holds one site's aggregate headcount lines for
 * a period: draft → submitted → approved, the approver someone other than the submitter (ESG-409-SOD). Only approved
 * snapshots feed reports, compliance and the dashboard. Lines are frozen once submitted (V028 trigger as backstop).
 */
@Service
public class WorkforceService {

    // no-mock-gate: config — snapshot workflow: which actions each status allows (V028 workforce_snapshots status check)
    static final Map<String, Set<String>> ACTIONS = Map.of(
            "draft", Set.of("submit"),
            "submitted", Set.of("approve", "return"),
            "approved", Set.of("reopen"));

    private static final String SELECT = "select s.*, o.name as org_name, su.first_name || ' ' || su.last_name as submitted_by_name, "
            + "au.first_name || ' ' || au.last_name as approved_by_name from social.workforce_snapshots s "
            + "join auth.organisations o on o.id = s.org_id left join auth.users su on su.id = s.submitted_by "
            + "left join auth.users au on au.id = s.approved_by ";

    private final JdbcTemplate jdbc;
    private final SocialSupport support;
    private final ReportingPeriodService periods;
    private final NotificationService notifications;

    public WorkforceService(JdbcTemplate jdbc, SocialSupport support, ReportingPeriodService periods, NotificationService notifications) {
        this.jdbc = jdbc;
        this.support = support;
        this.periods = periods;
        this.notifications = notifications;
    }

    public List<WorkforceSnapshot> list(Integer year, String status) {
        StringBuilder where = new StringBuilder("where s.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (year != null) {
            where.append(" and extract(year from s.reporting_period_end) = ?");
            args.add(year);
        }
        if (status != null) {
            if (!ACTIONS.containsKey(status)) {
                throw FieldValidationException.of("status", "must be draft, submitted or approved");
            }
            where.append(" and s.status = ?");
            args.add(status);
        }
        support.scope(where, args, "s.org_id");
        return jdbc.query(SELECT + where + " order by s.reporting_period_end desc, o.name", (rs, i) -> snapshot(rs), args.toArray());
    }

    public WorkforceSnapshot get(UUID id) {
        WorkforceSnapshot s = jdbc.query(SELECT + "where s.id = ? and s.deleted_at is null", (rs, i) -> snapshot(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Workforce snapshot"));
        support.visible(s.orgId(), "Workforce snapshot");
        return s;
    }

    @Transactional
    public WorkforceSnapshot create(WorkforceSnapshotRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into social.workforce_snapshots (id, org_id, reporting_period_start, reporting_period_end, hours_worked, new_hires, "
                        + "leavers, pay_currency, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, r.orgId(), Date.valueOf(r.reportingPeriodStart()),
                Date.valueOf(r.reportingPeriodEnd()), r.hoursWorked(), r.newHires(), r.leavers(), r.payCurrency(), SocialSupport.blank(r.notes()));
        writeLines(id, r.lines());
        return get(id);
    }

    @Transactional
    public WorkforceSnapshot update(UUID id, WorkforceSnapshotRequest r) {
        WorkforceSnapshot current = get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        if (!"draft".equals(current.status())) {
            throw ApiException.conflict("ESG-409-STATE", "These figures are " + current.status() + "; return or reopen them before editing.");
        }
        periods.requireOpen(current.reportingPeriodStart(), current.reportingPeriodEnd());
        check(r);
        int n = jdbc.update("update social.workforce_snapshots set org_id = ?, reporting_period_start = ?, reporting_period_end = ?, hours_worked = ?, "
                        + "new_hires = ?, leavers = ?, pay_currency = ?, notes = ?, version = version + 1 where id = ? and version = ? and deleted_at is null",
                r.orgId(), Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), r.hoursWorked(), r.newHires(), r.leavers(),
                r.payCurrency(), SocialSupport.blank(r.notes()), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "These workforce figures were changed by someone else. Reload and try again.");
        }
        jdbc.update("delete from social.workforce_lines where snapshot_id = ?", id);
        writeLines(id, r.lines());
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        WorkforceSnapshot current = get(id);
        if ("approved".equals(current.status())) {
            throw ApiException.conflict("ESG-409-STATE", "Approved figures can't be deleted; reopen them first.");
        }
        periods.requireOpen(current.reportingPeriodStart(), current.reportingPeriodEnd());
        int n = jdbc.update("update social.workforce_snapshots set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", id, version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "These workforce figures were changed by someone else. Reload and try again.");
        }
    }

    @Transactional
    public WorkforceSnapshot act(UUID id, ActionRequest a) {
        WorkforceSnapshot s = get(id);
        String action = a.action();
        String perm = "submit".equals(action) ? "social:update" : "social:approve";
        if (!CurrentUser.hasPermission(perm)) {
            throw ApiException.forbidden("You need the " + perm + " permission to " + action + " workforce figures.");
        }
        if (!s.actions().contains(action)) {
            String blocked = s.blockedActions().get(action);
            if (blocked != null) {
                throw ApiException.conflict("ESG-409-SOD", blocked);
            }
            throw ApiException.conflict("ESG-409-STATE", "You can't " + action + " figures that are " + s.status() + ".");
        }
        UUID me = CurrentUser.access().userId();
        String note = SocialSupport.blank(a.note());
        if (("return".equals(action) || "reopen".equals(action)) && note == null) {
            throw FieldValidationException.of("note", "give a reason");
        }
        periods.requireOpen(s.reportingPeriodStart(), s.reportingPeriodEnd());
        String sql = switch (action) {
            case "submit" -> "update social.workforce_snapshots set status = 'submitted', submitted_by = ?, submitted_at = now(), status_reason = null, "
                    + "version = version + 1 where id = ? and version = ?";
            case "approve" -> "update social.workforce_snapshots set status = 'approved', approved_by = ?, approved_at = now(), status_reason = null, "
                    + "version = version + 1 where id = ? and version = ?";
            default -> "update social.workforce_snapshots set status = 'draft', approved_by = null, approved_at = null, status_reason = ?, "
                    + "version = version + 1 where id = ? and version = ?";
        };
        Object first = "submit".equals(action) || "approve".equals(action) ? me : note;
        if ("submit".equals(action) && s.metrics().headcount() == 0) {
            throw FieldValidationException.of("lines", "add the headcount before submitting");
        }
        if ("submit".equals(action) && s.reportingPeriodEnd().isAfter(support.today())) {
            throw ApiException.conflict("ESG-409-STATE", "The period hasn't ended yet; submit the figures after " + s.reportingPeriodEnd() + ".");
        }
        int n = jdbc.update(sql, first, id, a.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "These workforce figures were changed by someone else. Reload and try again.");
        }
        if ("submit".equals(action)) {
            String period = s.reportingPeriodStart() + " to " + s.reportingPeriodEnd();
            for (SocialDtos.Person p : support.holders("approve")) {
                if (!p.id().equals(me)) {
                    notifications.notifyAfterCommit(p.id(), "WORKFORCE_SNAPSHOT_SUBMITTED", Map.of("site", s.orgName(), "period", period));
                }
            }
        }
        return get(id);
    }

    // ---------------------------------------------------------------- aggregates used by overview, reports, compliance

    /** For each site, its latest approved snapshot ending on or before {@code asOf} and on or after {@code from}. */
    public List<UUID> latestApproved(LocalDate from, LocalDate asOf, boolean scoped) {
        StringBuilder where = new StringBuilder("where s.deleted_at is null and s.status = 'approved' and s.reporting_period_end between ? and ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(asOf)));
        if (scoped) {
            support.scope(where, args, "s.org_id");
        }
        return jdbc.queryForList("select distinct on (s.org_id) s.id from social.workforce_snapshots s " + where
                + " order by s.org_id, s.reporting_period_end desc", UUID.class, args.toArray());
    }

    /** Lines of several snapshots added together, with total leavers and the opening headcount where known. */
    public WorkforceMetrics.Result combined(List<UUID> snapshots) {
        if (snapshots.isEmpty()) {
            return null;
        }
        List<WorkforceMetrics.Line> lines = new ArrayList<>();
        Integer leavers = null;
        Integer opening = 0;
        for (UUID id : snapshots) {
            Map<String, Object> s = jdbc.queryForMap("select org_id, reporting_period_start, leavers from social.workforce_snapshots where id = ?", id);
            lines.addAll(lines(id).stream().map(WorkforceService::metricLine).toList());
            if (s.get("leavers") != null) {
                leavers = (leavers == null ? 0 : leavers) + ((Number) s.get("leavers")).intValue();
            }
            Integer o = opening((UUID) s.get("org_id"), ((java.sql.Date) s.get("reporting_period_start")).toLocalDate());
            opening = o == null || opening == null ? null : opening + o;
        }
        return WorkforceMetrics.compute(lines, leavers, opening);
    }

    // ---------------------------------------------------------------- internals

    private void check(WorkforceSnapshotRequest r) {
        support.requireOrg(r.orgId());
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.reportingPeriodEnd().isBefore(r.reportingPeriodStart())) {
            errors.put("reportingPeriodEnd", "must be on or after the start date");
        }
        Set<String> seen = new HashSet<>();
        boolean anyPay = false;
        for (int i = 0; i < r.lines().size(); i++) {
            WorkforceLineRequest l = r.lines().get(i);
            if (!seen.add(l.level() + "|" + l.gender() + "|" + l.ageBand())) {
                errors.put("lines[" + i + "]", "this level, gender and age band is already listed");
            }
            if (l.disabledHeadcount() != null && l.headcount() != null && l.disabledHeadcount() > l.headcount()) {
                errors.put("lines[" + i + "].disabledHeadcount", "can't be more than the headcount");
            }
            if (l.totalAnnualPay() != null && l.headcount() != null && l.headcount() == 0 && l.totalAnnualPay().signum() > 0) {
                errors.put("lines[" + i + "].totalAnnualPay", "a line with no people has no pay");
            }
            anyPay |= l.totalAnnualPay() != null;
        }
        if (anyPay && r.payCurrency() == null) {
            errors.put("payCurrency", "is required when pay is entered");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        periods.requireOpen(r.reportingPeriodStart(), r.reportingPeriodEnd());
    }

    private void writeLines(UUID snapshot, List<WorkforceLineRequest> lines) {
        for (WorkforceLineRequest l : lines) {
            jdbc.update("insert into social.workforce_lines (snapshot_id, employee_level, gender, age_band, headcount, disabled_headcount, "
                            + "total_annual_pay) values (?, ?, ?, ?, ?, ?, ?)", snapshot, l.level(), l.gender(), l.ageBand(), l.headcount(),
                    l.disabledHeadcount() == null ? 0 : l.disabledHeadcount(), l.totalAnnualPay());
        }
    }

    private List<WorkforceLine> lines(UUID snapshot) {
        return jdbc.query("select * from social.workforce_lines where snapshot_id = ? order by array_position(array['executive',"
                + "'senior_management','middle_management','non_management'], employee_level), array_position(array['female','male',"
                + "'undisclosed'], gender), array_position(array['under_30','30_50','over_50'], age_band)", (rs, i) -> new WorkforceLine(
                rs.getString("employee_level"), rs.getString("gender"), rs.getString("age_band"), rs.getInt("headcount"),
                rs.getInt("disabled_headcount"), rs.getBigDecimal("total_annual_pay")), snapshot);
    }

    private static WorkforceMetrics.Line metricLine(WorkforceLine l) {
        return new WorkforceMetrics.Line(l.level(), l.gender(), l.ageBand(), l.headcount(), l.disabledHeadcount(), l.totalAnnualPay());
    }

    /** Closing headcount of the site's approved snapshot that ends the day before {@code start}, if there is one. */
    private Integer opening(UUID org, LocalDate start) {
        List<Integer> n = jdbc.queryForList("select (select coalesce(sum(headcount), 0) from social.workforce_lines l where l.snapshot_id = s.id) "
                + "from social.workforce_snapshots s where s.org_id = ? and s.status = 'approved' and s.deleted_at is null "
                + "and s.reporting_period_end = ?", Integer.class, org, Date.valueOf(start.minusDays(1)));
        return n.isEmpty() ? null : n.get(0);
    }

    private WorkforceSnapshot snapshot(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        List<WorkforceLine> lines = lines(id);
        UUID org = rs.getObject("org_id", UUID.class);
        LocalDate start = rs.getObject("reporting_period_start", LocalDate.class);
        Integer leavers = (Integer) rs.getObject("leavers");
        WorkforceMetrics.Result metrics = WorkforceMetrics.compute(lines.stream().map(WorkforceService::metricLine).toList(), leavers,
                opening(org, start));
        UUID submittedBy = rs.getObject("submitted_by", UUID.class);
        List<String> actions = new ArrayList<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        UUID me = CurrentUser.access() == null ? null : CurrentUser.access().userId();
        for (String action : ACTIONS.get(status)) {
            String perm = "submit".equals(action) ? "social:update" : "social:approve";
            if (!CurrentUser.hasPermission(perm)) {
                continue;
            } else if ("approve".equals(action) && me != null && me.equals(submittedBy)) {
                blocked.put(action, "You submitted these figures, so someone else must approve them.");
            } else {
                actions.add(action);
            }
        }
        return new WorkforceSnapshot(id, org, rs.getString("org_name"), start, rs.getObject("reporting_period_end", LocalDate.class),
                rs.getBigDecimal("hours_worked"), (Integer) rs.getObject("new_hires"), leavers, rs.getString("pay_currency"),
                rs.getString("notes"), status, rs.getString("status_reason"), rs.getString("submitted_by_name"),
                SocialSupport.instant(rs.getTimestamp("submitted_at")), rs.getString("approved_by_name"),
                SocialSupport.instant(rs.getTimestamp("approved_at")), lines, metrics, actions, blocked, rs.getLong("version"));
    }
}
