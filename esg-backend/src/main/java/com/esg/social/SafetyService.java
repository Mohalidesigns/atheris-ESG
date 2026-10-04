package com.esg.social;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.notifications.NotificationService;
import com.esg.security.CurrentUser;
import com.esg.service.ReportingPeriodService;
import com.esg.social.SocialDtos.ActionRequest;
import com.esg.social.SocialDtos.Incident;
import com.esg.social.SocialDtos.IncidentRequest;
import com.esg.social.SocialDtos.MonthCount;
import com.esg.social.SocialDtos.SafetyStatistics;
import com.esg.social.SocialDtos.YearRates;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Health and safety incidents (WP-33; TRD §8.7 SafetyIncidentManager; journey 16): reporting, the investigation
 * workflow (reported → investigating → actions open → closed; whoever records the findings can't also close the
 * incident, ESG-409-SOD) and LTIFR/TRIR from incidents and approved hours worked (SafetyRates).
 * Rates count employees only, over sites with approved hours for the year: an employee injury at a site with no hours
 * recorded is reported as excluded rather than silently inflating the rate. Contractors are counted separately
 * (GRI 403-9 a vs b) because their hours aren't collected yet (D31).
 */
@Service
public class SafetyService {

    // no-mock-gate: config — investigation workflow: which actions each status allows (V028 safety_incidents status check)
    static final Map<String, Set<String>> ACTIONS = Map.of(
            "reported", Set.of("start_investigation"),
            "investigating", Set.of("record_findings"),
            "actions_open", Set.of("close"),
            "closed", Set.of("reopen"));
    private static final Set<String> INJURY_TYPES = Set.of("injury", "illness");

    private static final String SELECT = "select i.*, o.name as org_name, iu.first_name || ' ' || iu.last_name as investigator_name, "
            + "fu.first_name || ' ' || fu.last_name as findings_by_name, cu.first_name || ' ' || cu.last_name as closed_by_name, "
            + "ru.first_name || ' ' || ru.last_name as reported_by_name from social.safety_incidents i join auth.organisations o on o.id = i.org_id "
            + "left join auth.users iu on iu.id = i.investigator_id left join auth.users fu on fu.id = i.findings_by "
            + "left join auth.users cu on cu.id = i.closed_by left join auth.users ru on ru.id = i.reported_by ";

    private final JdbcTemplate jdbc;
    private final SocialSupport support;
    private final ReportingPeriodService periods;
    private final NotificationService notifications;

    private final org.springframework.context.ApplicationEventPublisher events;

    public SafetyService(JdbcTemplate jdbc, SocialSupport support, ReportingPeriodService periods, NotificationService notifications,
                         org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.jdbc = jdbc;
        this.support = support;
        this.periods = periods;
        this.notifications = notifications;
    }

    public List<Incident> list(Integer year, String status, UUID orgId) {
        StringBuilder where = new StringBuilder("where i.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (year != null) {
            where.append(" and extract(year from i.reporting_period_start) = ?");
            args.add(year);
        }
        if (status != null) {
            if (!ACTIONS.containsKey(status)) {
                throw FieldValidationException.of("status", "must be an incident status");
            }
            where.append(" and i.status = ?");
            args.add(status);
        }
        if (orgId != null) {
            where.append(" and i.org_id = ?");
            args.add(orgId);
        }
        support.scope(where, args, "i.org_id");
        return jdbc.query(SELECT + where + " order by i.occurred_at desc", (rs, i) -> incident(rs), args.toArray());
    }

    public Incident get(UUID id) {
        Incident i = jdbc.query(SELECT + "where i.id = ? and i.deleted_at is null", (rs, n) -> incident(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Incident"));
        support.visible(i.orgId(), "Incident");
        return i;
    }

    @Transactional
    public Incident create(IncidentRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into social.safety_incidents (id, org_id, title, incident_type, outcome, person_type, occurred_at, lost_days, "
                        + "location, description, reported_by, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'reported')", id, r.orgId(),
                r.title().trim(), r.incidentType(), r.outcome(), r.personType(), timestamp(r.occurredAt()), r.lostDays(),
                SocialSupport.blank(r.location()), SocialSupport.blank(r.description()), CurrentUser.access().userId());
        Incident created = get(id);
        events.publishEvent(new SafetyEvents.IncidentRecorded(id));
        if (SafetyRates.lostTime(r.outcome())) {
            notifySerious(created);
        }
        return created;
    }

    @Transactional
    public Incident update(UUID id, IncidentRequest r) {
        Incident current = get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        if ("closed".equals(current.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This incident is closed; reopen it to change it.");
        }
        check(r);
        int n = jdbc.update("update social.safety_incidents set org_id = ?, title = ?, incident_type = ?, outcome = ?, person_type = ?, "
                        + "occurred_at = ?, lost_days = ?, location = ?, description = ?, version = version + 1 "
                        + "where id = ? and version = ? and deleted_at is null", r.orgId(), r.title().trim(), r.incidentType(), r.outcome(),
                r.personType(), timestamp(r.occurredAt()), r.lostDays(), SocialSupport.blank(r.location()),
                SocialSupport.blank(r.description()), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This incident was changed by someone else. Reload and try again.");
        }
        Incident updated = get(id);
        if (SafetyRates.lostTime(r.outcome()) && !current.lostTime()) {
            notifySerious(updated);
        }
        return updated;
    }

    @Transactional
    public void delete(UUID id, long version) {
        Incident current = get(id);
        if (!"reported".equals(current.status())) {
            throw ApiException.conflict("ESG-409-STATE", "Only an incident not yet under investigation can be deleted.");
        }
        LocalDate day = current.occurredAt().toLocalDate();
        periods.requireOpen(day, day);
        int n = jdbc.update("update social.safety_incidents set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", id, version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This incident was changed by someone else. Reload and try again.");
        }
    }

    @Transactional
    public Incident act(UUID id, ActionRequest a) {
        Incident current = get(id);
        String action = a.action();
        String perm = "close".equals(action) || "reopen".equals(action) ? "social:approve" : "social:update";
        if (!CurrentUser.hasPermission(perm)) {
            throw ApiException.forbidden("You need the " + perm + " permission to " + action.replace('_', ' ') + ".");
        }
        if (!current.actions().contains(action)) {
            String blocked = current.blockedActions().get(action);
            if (blocked != null) {
                throw ApiException.conflict("ESG-409-SOD", blocked);
            }
            throw ApiException.conflict("ESG-409-STATE", "You can't " + action.replace('_', ' ') + " an incident that is "
                    + current.status().replace('_', ' ') + ".");
        }
        LocalDate day = current.occurredAt().toLocalDate();
        periods.requireOpen(day, day);
        UUID me = CurrentUser.access().userId();
        Map<String, String> errors = new LinkedHashMap<>();
        int n;
        switch (action) {
            case "start_investigation" -> {
                if (a.investigatorId() == null) {
                    errors.put("investigatorId", "choose who investigates");
                } else if (!support.holds(a.investigatorId(), "update")) {
                    errors.put("investigatorId", "must be someone who can update social records");
                }
                fail(errors);
                n = jdbc.update("update social.safety_incidents set status = 'investigating', investigator_id = ?, status_reason = null, "
                        + "version = version + 1 where id = ? and version = ?", a.investigatorId(), id, a.version());
            }
            case "record_findings" -> {
                if (SocialSupport.blank(a.rootCause()) == null) {
                    errors.put("rootCause", "is required");
                }
                if (SocialSupport.blank(a.correctiveActions()) == null) {
                    errors.put("correctiveActions", "is required");
                }
                fail(errors);
                n = jdbc.update("update social.safety_incidents set status = 'actions_open', root_cause = ?, corrective_actions = ?, findings_by = ?, "
                        + "findings_at = now(), version = version + 1 where id = ? and version = ?", a.rootCause().trim(),
                        a.correctiveActions().trim(), me, id, a.version());
            }
            case "close" -> n = jdbc.update("update social.safety_incidents set status = 'closed', closed_by = ?, closed_at = now(), "
                    + "status_reason = ?, version = version + 1 where id = ? and version = ?", me, SocialSupport.blank(a.note()), id, a.version());
            default -> {
                if (SocialSupport.blank(a.note()) == null) {
                    errors.put("note", "give a reason");
                }
                fail(errors);
                n = jdbc.update("update social.safety_incidents set status = 'investigating', closed_by = null, closed_at = null, status_reason = ?, "
                        + "version = version + 1 where id = ? and version = ?", a.note().trim(), id, a.version());
            }
        }
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This incident was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    // ---------------------------------------------------------------- statistics (journey 16)

    public SafetyStatistics statistics(int year, UUID orgId, boolean scoped) {
        Year y = year(year, orgId, scoped);
        List<YearRates> trend = new ArrayList<>();
        for (int t = year - 4; t <= year; t++) {
            Year at = t == year ? y : year(t, orgId, scoped);
            trend.add(new YearRates(t, at.hours, at.lti, at.recordable, SafetyRates.ltifr(at.lti, at.hours), SafetyRates.trir(at.recordable, at.hours)));
        }
        StringBuilder where = new StringBuilder("where i.deleted_at is null and extract(year from i.reporting_period_start) = ?");
        List<Object> args = new ArrayList<>(List.of(year));
        filter(where, args, orgId, scoped);
        List<MonthCount> months = new ArrayList<>();
        Map<Integer, MonthCount> byMonth = new LinkedHashMap<>();
        jdbc.query("select extract(month from i.reporting_period_start)::int as m, count(*) as n, "
                + "count(*) filter (where i.outcome in ('medical_treatment', 'restricted_work', 'lost_time', 'fatality')) as rec, "
                + "count(*) filter (where i.outcome in ('lost_time', 'fatality')) as lti, count(*) filter (where i.incident_type = 'near_miss') as nm "
                + "from social.safety_incidents i " + where + " group by 1", rs -> {
                    byMonth.put(rs.getInt("m"), new MonthCount(rs.getInt("m"), rs.getLong("n"), rs.getLong("rec"), rs.getLong("lti"), rs.getLong("nm")));
                }, args.toArray());
        for (int m = 1; m <= 12; m++) {
            months.add(byMonth.getOrDefault(m, new MonthCount(m, 0, 0, 0, 0)));
        }
        StringBuilder open = new StringBuilder("where i.deleted_at is null and i.status <> 'closed'");
        List<Object> openArgs = new ArrayList<>();
        filter(open, openArgs, orgId, scoped);
        Long openInvestigations = jdbc.queryForObject("select count(*) from social.safety_incidents i " + open, Long.class, openArgs.toArray());
        return new SafetyStatistics(year, y.hours, y.sites, y.lti, y.recordable, y.fatalities, y.lostDays, SafetyRates.ltifr(y.lti, y.hours),
                SafetyRates.trir(y.recordable, y.hours), y.excluded, y.contractorRecordable, y.nearMisses,
                openInvestigations == null ? 0 : openInvestigations, trend, months);
    }

    private record Year(BigDecimal hours, int sites, long lti, long recordable, long fatalities, long lostDays, long excluded,
                        long contractorRecordable, long nearMisses) {}

    /** One year's counts: employee incidents at sites with approved hours for the year, and what was left out. */
    private Year year(int year, UUID orgId, boolean scoped) {
        StringBuilder hw = new StringBuilder("where s.deleted_at is null and s.status = 'approved' and s.hours_worked is not null "
                + "and extract(year from s.reporting_period_end) = ?");
        List<Object> hwArgs = new ArrayList<>(List.of(year));
        filter(hw, hwArgs, orgId, scoped, "s.org_id");
        Map<String, Object> hours = jdbc.queryForMap("select coalesce(sum(s.hours_worked), 0) as hours, count(distinct s.org_id) as sites, "
                + "coalesce(array_agg(distinct s.org_id) filter (where s.org_id is not null), '{}') as orgs from social.workforce_snapshots s " + hw,
                hwArgs.toArray());
        StringBuilder where = new StringBuilder("where i.deleted_at is null and extract(year from i.reporting_period_start) = ?");
        List<Object> args = new ArrayList<>();
        args.add(hours.get("orgs"));
        args.add(year);
        filter(where, args, orgId, scoped);
        Map<String, Object> c = jdbc.queryForMap("select "
                + "count(*) filter (where i.person_type = 'employee' and i.outcome in ('lost_time', 'fatality') and i.org_id = any(?)) as lti, "
                + "count(*) filter (where i.person_type = 'employee' and i.outcome in ('medical_treatment', 'restricted_work', 'lost_time', 'fatality') "
                + "  and i.org_id = any(?)) as rec, "
                + "count(*) filter (where i.person_type = 'employee' and i.outcome = 'fatality' and i.org_id = any(?)) as fatal, "
                + "coalesce(sum(i.lost_days) filter (where i.person_type = 'employee' and i.org_id = any(?)), 0) as days, "
                + "count(*) filter (where i.person_type = 'employee' and i.outcome in ('medical_treatment', 'restricted_work', 'lost_time', 'fatality') "
                + "  and not (i.org_id = any(?))) as excluded, "
                + "count(*) filter (where i.person_type = 'contractor' and i.outcome in ('medical_treatment', 'restricted_work', 'lost_time', 'fatality')) as contractor, "
                + "count(*) filter (where i.incident_type = 'near_miss') as near_miss "
                + "from social.safety_incidents i " + where,
                expand(args));
        return new Year((BigDecimal) hours.get("hours"), ((Number) hours.get("sites")).intValue(), n(c, "lti"), n(c, "rec"), n(c, "fatal"),
                n(c, "days"), n(c, "excluded"), n(c, "contractor"), n(c, "near_miss"));
    }

    /** The site-array parameter appears five times in the count query, then the where-clause parameters follow. */
    private static Object[] expand(List<Object> args) {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            out.add(args.get(0));
        }
        out.addAll(args.subList(1, args.size()));
        return out.toArray();
    }

    private static long n(Map<String, Object> m, String k) {
        return m.get(k) == null ? 0 : ((Number) m.get(k)).longValue();
    }

    private void filter(StringBuilder where, List<Object> args, UUID orgId, boolean scoped) {
        filter(where, args, orgId, scoped, "i.org_id");
    }

    private void filter(StringBuilder where, List<Object> args, UUID orgId, boolean scoped, String column) {
        if (orgId != null) {
            where.append(" and ").append(column).append(" in (with recursive t as (select id from auth.organisations where id = ? "
                    + "union select o.id from auth.organisations o join t on o.parent_org_id = t.id) select id from t)");
            args.add(orgId);
        }
        if (scoped) {
            support.scope(where, args, column);
        }
    }

    // ---------------------------------------------------------------- internals

    private void check(IncidentRequest r) {
        support.requireOrg(r.orgId());
        Map<String, String> errors = new LinkedHashMap<>();
        boolean injury = INJURY_TYPES.contains(r.incidentType());
        if (injury && "none".equals(r.outcome())) {
            errors.put("outcome", "an injury or illness needs its outcome (first aid or worse)");
        }
        if (!injury && !"none".equals(r.outcome())) {
            errors.put("outcome", "only an injury or illness has an outcome; record a hurt person as an injury");
        }
        if (injury && r.personType() == null) {
            errors.put("personType", "say who was hurt");
        }
        if ("lost_time".equals(r.outcome()) && (r.lostDays() == null || r.lostDays() < 1)) {
            errors.put("lostDays", "a lost-time injury has at least one lost day");
        }
        // Days away or restricted (the OSHA DART count) belong to lost-time and restricted-work cases only.
        if (!"lost_time".equals(r.outcome()) && !"restricted_work".equals(r.outcome()) && r.lostDays() != null && r.lostDays() > 0) {
            errors.put("lostDays", "days lost or restricted apply to lost-time and restricted-work cases only");
        }
        if (r.occurredAt().isAfter(LocalDateTime.now(support.clock().withZone(SocialSupport.LAGOS)))) {
            errors.put("occurredAt", "can't be in the future");
        }
        fail(errors);
        LocalDate day = r.occurredAt().toLocalDate();
        periods.requireOpen(day, day);
    }

    private static void fail(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private void notifySerious(Incident i) {
        for (SocialDtos.Person p : support.holders("approve")) {
            notifications.notifyAfterCommit(p.id(), "SAFETY_INCIDENT_SERIOUS", Map.of("title", i.title(), "outcome",
                    i.outcome().replace('_', ' '), "site", i.orgName(), "date", i.occurredAt().toLocalDate().toString()));
        }
    }

    private static Timestamp timestamp(LocalDateTime local) {
        return Timestamp.from(local.atZone(SocialSupport.LAGOS).toInstant());
    }

    private Incident incident(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        String outcome = rs.getString("outcome");
        UUID findingsBy = rs.getObject("findings_by", UUID.class);
        UUID me = CurrentUser.userIdOrNull();
        List<String> actions = new ArrayList<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        for (String action : ACTIONS.get(status)) {
            String perm = "close".equals(action) || "reopen".equals(action) ? "social:approve" : "social:update";
            if (!CurrentUser.hasPermission(perm)) {
                continue;
            } else if ("close".equals(action) && me != null && me.equals(findingsBy)) {
                blocked.put(action, "You recorded the findings, so someone else must confirm the actions and close the incident.");
            } else {
                actions.add(action);
            }
        }
        LocalDateTime occurred = rs.getTimestamp("occurred_at").toInstant().atZone(SocialSupport.LAGOS).toLocalDateTime();
        return new Incident(rs.getObject("id", UUID.class), rs.getObject("org_id", UUID.class), rs.getString("org_name"), rs.getString("title"),
                rs.getString("incident_type"), outcome, rs.getString("person_type"), occurred, (Integer) rs.getObject("lost_days"),
                rs.getString("location"), rs.getString("description"), SafetyRates.recordable(outcome), SafetyRates.lostTime(outcome), status,
                rs.getString("status_reason"), rs.getObject("investigator_id", UUID.class), rs.getString("investigator_name"),
                rs.getString("root_cause"), rs.getString("corrective_actions"), rs.getString("findings_by_name"),
                SocialSupport.instant(rs.getTimestamp("findings_at")), rs.getString("closed_by_name"), SocialSupport.instant(rs.getTimestamp("closed_at")),
                rs.getString("reported_by_name"), SocialSupport.instant(rs.getTimestamp("created_at")), actions, blocked, rs.getLong("version"));
    }
}
