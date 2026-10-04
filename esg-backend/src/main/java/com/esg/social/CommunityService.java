package com.esg.social;

import com.esg.datahub.FxRateService;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.service.ReportingPeriodService;
import com.esg.social.SocialDtos.Evidence;
import com.esg.social.SocialDtos.EvidenceRequest;
import com.esg.social.SocialDtos.Expenditure;
import com.esg.social.SocialDtos.ExpenditureRequest;
import com.esg.social.SocialDtos.Project;
import com.esg.social.SocialDtos.ProjectRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Community projects (WP-33; TRD §8.7 CommunityProjectTracker; BRD 6.4 host communities; journey 17): registration,
 * budget against dated expenditure (converted to the tenant base currency with a dated rate — no rate, no guess),
 * beneficiaries, SDGs, the PIA 2021 host-community flag, and geo-tagged evidence photos.
 */
@Service
public class CommunityService {

    /** Evidence further than this from the project's own location is flagged for the reviewer. */
    static final BigDecimal FAR_KM = BigDecimal.valueOf(25);

    private static final String SELECT = "select p.*, o.name as org_name, "
            + "(select coalesce(sum(e.amount), 0) from social.project_expenditures e where e.project_id = p.id and e.deleted_at is null) as spent, "
            + "(select coalesce(sum(e.amount_base), 0) from social.project_expenditures e where e.project_id = p.id and e.deleted_at is null) as spent_base, "
            + "(select count(*) from social.project_evidence v where v.project_id = p.id and v.deleted_at is null) as evidence_count, "
            + "(select count(*) from social.grievances g where g.project_id = p.id and g.deleted_at is null and g.status not in ('resolved', 'closed')) "
            + "as open_grievances from social.community_projects p join auth.organisations o on o.id = p.org_id ";

    private final JdbcTemplate jdbc;
    private final SocialSupport support;
    private final ReportingPeriodService periods;
    private final FxRateService fx;

    public CommunityService(JdbcTemplate jdbc, SocialSupport support, ReportingPeriodService periods, FxRateService fx) {
        this.jdbc = jdbc;
        this.support = support;
        this.periods = periods;
        this.fx = fx;
    }

    public List<Project> list(String status) {
        StringBuilder where = new StringBuilder("where p.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            if (!status.matches(SocialDtos.PROJECT_STATUS_REGEX)) {
                throw FieldValidationException.of("status", "must be a project status");
            }
            where.append(" and p.status = ?");
            args.add(status);
        }
        support.scope(where, args, "p.org_id");
        String base = base();
        return jdbc.query(SELECT + where + " order by lower(p.name)", (rs, i) -> project(rs, base, false), args.toArray());
    }

    public Project get(UUID id) {
        String base = base();
        Project p = jdbc.query(SELECT + "where p.id = ? and p.deleted_at is null", (rs, i) -> project(rs, base, true), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Community project"));
        support.visible(p.orgId(), "Community project");
        return p;
    }

    @Transactional
    public Project create(ProjectRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into social.community_projects (id, org_id, name, category, description, community, state_region, "
                    + "lga, latitude, longitude, budget, currency, planned_beneficiaries, actual_beneficiaries, status, start_date, end_date, sdgs, "
                    + "pia_host_community) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setObject(1, id);
            bind(ps, con, r, 2);
            return ps;
        });
        return get(id);
    }

    @Transactional
    public Project update(UUID id, ProjectRequest r) {
        Project current = get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r);
        if (!current.currency().equals(r.currency()) && !current.expenditures().isEmpty()) {
            throw FieldValidationException.of("currency", "can't change once expenditure is recorded");
        }
        int n = jdbc.update(con -> {
            var ps = con.prepareStatement("update social.community_projects set org_id = ?, name = ?, category = ?, description = ?, community = ?, "
                    + "state_region = ?, lga = ?, latitude = ?, longitude = ?, budget = ?, currency = ?, planned_beneficiaries = ?, "
                    + "actual_beneficiaries = ?, status = ?, start_date = ?, end_date = ?, sdgs = ?, pia_host_community = ?, version = version + 1 "
                    + "where id = ? and version = ? and deleted_at is null");
            int next = bind(ps, con, r, 1);
            ps.setObject(next, id);
            ps.setLong(next + 1, r.version());
            return ps;
        });
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This project was changed by someone else. Reload and try again.");
        }
        if (current.latitude() != null || r.latitude() != null) {
            refreshDistances(id);
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        Project current = get(id);
        if (!current.expenditures().isEmpty() || current.openGrievances() > 0) {
            throw ApiException.conflict("ESG-409-IN-USE", "This project has expenditure or open grievances; cancel it instead.");
        }
        int n = jdbc.update("update social.community_projects set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", id, version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This project was changed by someone else. Reload and try again.");
        }
    }

    // ---------------------------------------------------------------- expenditure

    @Transactional
    public Project addExpenditure(UUID projectId, ExpenditureRequest r) {
        Project p = get(projectId);
        if (r.spentOn().isAfter(support.today())) {
            throw FieldValidationException.of("spentOn", "can't be in the future");
        }
        periods.requireOpen(r.spentOn(), r.spentOn());
        String base = base();
        BigDecimal rate = null;
        LocalDate rateDate = null;
        BigDecimal amountBase = r.amount();
        if (!p.currency().equals(base)) {
            FxRateService.Rate found = fx.resolve(p.currency(), base, r.spentOn()).orElseThrow(() -> FieldValidationException.of("amount",
                    "No " + p.currency() + "→" + base + " exchange rate on or before " + r.spentOn() + ". Add one under Data Hub → Exchange rates."));
            rate = found.rate();
            rateDate = found.rateDate();
            amountBase = r.amount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }
        jdbc.update("insert into social.project_expenditures (project_id, spent_on, description, amount, currency, amount_base, base_currency, "
                        + "fx_rate, fx_rate_date) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", projectId, Date.valueOf(r.spentOn()), r.description().trim(),
                r.amount(), p.currency(), amountBase, base, rate, rateDate == null ? null : Date.valueOf(rateDate));
        return get(projectId);
    }

    @Transactional
    public Project deleteExpenditure(UUID projectId, UUID expenditureId, long version) {
        Project p = get(projectId);
        Expenditure e = p.expenditures().stream().filter(x -> x.id().equals(expenditureId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Expenditure"));
        periods.requireOpen(e.spentOn(), e.spentOn());
        int n = jdbc.update("update social.project_expenditures set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", expenditureId, version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This expenditure was changed by someone else. Reload and try again.");
        }
        return get(projectId);
    }

    // ---------------------------------------------------------------- geo-tagged evidence (journey 17)

    @Transactional
    public Project addEvidence(UUID projectId, EvidenceRequest r) {
        Project p = get(projectId);
        Map<String, String> errors = new LinkedHashMap<>();
        List<Map<String, Object>> file = jdbc.queryForList("select resource_type, resource_id from files.file_metadata where id = ? "
                + "and deleted_at is null", r.fileId());
        if (file.isEmpty() || !"community_project".equals(file.get(0).get("resource_type")) || !projectId.equals(file.get(0).get("resource_id"))) {
            errors.put("fileId", "upload the photo to this project first");
        }
        if (r.capturedOn().isAfter(support.today())) {
            errors.put("capturedOn", "can't be in the future");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        BigDecimal distance = distanceKm(p.latitude(), p.longitude(), r.latitude(), r.longitude());
        jdbc.update("insert into social.project_evidence (project_id, file_id, caption, latitude, longitude, captured_on, distance_km) "
                        + "values (?, ?, ?, ?, ?, ?, ?)", projectId, r.fileId(), SocialSupport.blank(r.caption()), r.latitude(), r.longitude(),
                Date.valueOf(r.capturedOn()), distance);
        return get(projectId);
    }

    @Transactional
    public Project deleteEvidence(UUID projectId, UUID evidenceId) {
        get(projectId);
        int n = jdbc.update("update social.project_evidence set deleted_at = now(), version = version + 1 where id = ? and project_id = ? "
                + "and deleted_at is null", evidenceId, projectId);
        if (n == 0) {
            throw ApiException.notFound("Evidence");
        }
        return get(projectId);
    }

    /** Great-circle distance (haversine, mean Earth radius 6,371 km), 2 decimals; null without both points. */
    static BigDecimal distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }
        double p1 = Math.toRadians(lat1.doubleValue());
        double p2 = Math.toRadians(lat2.doubleValue());
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return BigDecimal.valueOf(2 * 6371.0 * Math.asin(Math.sqrt(a))).setScale(2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- aggregates

    /** Community investment in the base currency: expenditure dated in the period (scoped to the user's organisations). */
    public BigDecimal investment(LocalDate from, LocalDate to, boolean scoped) {
        StringBuilder where = new StringBuilder("where e.deleted_at is null and p.deleted_at is null and e.spent_on between ? and ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to)));
        if (scoped) {
            support.scope(where, args, "p.org_id");
        }
        return jdbc.queryForObject("select coalesce(sum(e.amount_base), 0) from social.project_expenditures e join social.community_projects p "
                + "on p.id = e.project_id " + where, BigDecimal.class, args.toArray());
    }

    String base() {
        return jdbc.queryForObject("select base_currency from auth.tenant_settings", String.class);
    }

    // ---------------------------------------------------------------- internals

    private void check(ProjectRequest r) {
        support.requireOrg(r.orgId());
        Map<String, String> errors = new LinkedHashMap<>();
        if ((r.latitude() == null) != (r.longitude() == null)) {
            errors.put(r.latitude() == null ? "latitude" : "longitude", "give both latitude and longitude, or neither");
        }
        if (r.startDate() != null && r.endDate() != null && r.endDate().isBefore(r.startDate())) {
            errors.put("endDate", "must be on or after the start date");
        }
        if ("completed".equals(r.status()) && r.actualBeneficiaries() == null) {
            errors.put("actualBeneficiaries", "record how many people benefited before completing the project");
        }
        if (r.sdgs() != null && r.sdgs().stream().distinct().count() != r.sdgs().size()) {
            errors.put("sdgs", "lists a goal twice");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private int bind(java.sql.PreparedStatement ps, java.sql.Connection con, ProjectRequest r, int i) throws SQLException {
        ps.setObject(i++, r.orgId());
        ps.setString(i++, r.name().trim());
        ps.setString(i++, r.category());
        ps.setString(i++, SocialSupport.blank(r.description()));
        ps.setString(i++, SocialSupport.blank(r.community()));
        ps.setString(i++, SocialSupport.blank(r.stateRegion()));
        ps.setString(i++, SocialSupport.blank(r.lga()));
        ps.setBigDecimal(i++, r.latitude());
        ps.setBigDecimal(i++, r.longitude());
        ps.setBigDecimal(i++, r.budget());
        ps.setString(i++, r.currency());
        ps.setObject(i++, r.plannedBeneficiaries());
        ps.setObject(i++, r.actualBeneficiaries());
        ps.setString(i++, r.status());
        ps.setDate(i++, r.startDate() == null ? null : Date.valueOf(r.startDate()));
        ps.setDate(i++, r.endDate() == null ? null : Date.valueOf(r.endDate()));
        ps.setArray(i++, con.createArrayOf("int4", (r.sdgs() == null ? List.<Integer>of() : r.sdgs().stream().sorted().toList()).toArray()));
        ps.setBoolean(i++, r.piaHostCommunity());
        return i;
    }

    private void refreshDistances(UUID projectId) {
        Map<String, Object> p = jdbc.queryForMap("select latitude, longitude from social.community_projects where id = ?", projectId);
        jdbc.query("select id, latitude, longitude from social.project_evidence where project_id = ? and deleted_at is null", rs -> {
            jdbc.update("update social.project_evidence set distance_km = ? where id = ?", distanceKm((BigDecimal) p.get("latitude"),
                    (BigDecimal) p.get("longitude"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude")), rs.getObject("id", UUID.class));
        }, projectId);
    }

    private Project project(ResultSet rs, String base, boolean detail) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        BigDecimal budget = rs.getBigDecimal("budget");
        BigDecimal spent = rs.getBigDecimal("spent");
        BigDecimal used = budget == null || budget.signum() == 0 ? null
                : spent.multiply(BigDecimal.valueOf(100)).divide(budget, 1, RoundingMode.HALF_UP);
        List<Expenditure> expenditures = detail ? jdbc.query("select * from social.project_expenditures where project_id = ? and deleted_at is null "
                + "order by spent_on desc, created_at desc", (e, i) -> new Expenditure(e.getObject("id", UUID.class), e.getObject("spent_on", LocalDate.class),
                e.getString("description"), e.getBigDecimal("amount"), e.getString("currency"), e.getBigDecimal("amount_base"),
                e.getString("base_currency"), e.getBigDecimal("fx_rate"), e.getObject("fx_rate_date", LocalDate.class), e.getLong("version")), id)
                : List.of();
        List<Evidence> evidence = detail ? jdbc.query("select v.*, f.original_name, f.content_type from social.project_evidence v "
                + "join files.file_metadata f on f.id = v.file_id where v.project_id = ? and v.deleted_at is null order by v.captured_on desc",
                (v, i) -> {
                    BigDecimal d = v.getBigDecimal("distance_km");
                    return new Evidence(v.getObject("id", UUID.class), v.getObject("file_id", UUID.class), v.getString("original_name"),
                            v.getString("content_type"), v.getString("caption"), v.getBigDecimal("latitude"), v.getBigDecimal("longitude"),
                            v.getObject("captured_on", LocalDate.class), d, d != null && d.compareTo(FAR_KM) > 0);
                }, id) : List.of();
        return new Project(id, rs.getObject("org_id", UUID.class), rs.getString("org_name"), rs.getString("name"), rs.getString("category"),
                rs.getString("description"), rs.getString("community"), rs.getString("state_region"), rs.getString("lga"),
                rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), budget, rs.getString("currency"), spent, rs.getBigDecimal("spent_base"),
                base, used, (Integer) rs.getObject("planned_beneficiaries"), (Integer) rs.getObject("actual_beneficiaries"), rs.getString("status"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class), SocialSupport.ints(rs.getArray("sdgs")),
                rs.getBoolean("pia_host_community"), rs.getInt("evidence_count"), rs.getInt("open_grievances"), expenditures, evidence,
                rs.getLong("version"));
    }
}
