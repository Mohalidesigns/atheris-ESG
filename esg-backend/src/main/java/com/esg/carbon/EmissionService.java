package com.esg.carbon;

import com.esg.audit.LineageService;
import com.esg.carbon.CarbonDtos.Bucket;
import com.esg.carbon.CarbonDtos.EmissionRequest;
import com.esg.carbon.CarbonDtos.EmissionResponse;
import com.esg.carbon.CarbonDtos.FactorUsed;
import com.esg.carbon.CarbonDtos.Intensity;
import com.esg.carbon.CarbonDtos.Recalculation;
import com.esg.carbon.CarbonDtos.Summary;
import com.esg.datahub.FxRateService;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.reference.EmissionFactorService;
import com.esg.reference.ReferenceDtos.EmissionFactor;
import com.esg.reference.ReferenceDtos.ResolvedFactor;
import com.esg.security.CurrentUser;
import com.esg.security.OrgScopeService;
import com.esg.service.ReportingPeriodService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emission records (TRD §4.4, §8.2). CO2e is always calculated here — never accepted from the client — and every
 * record keeps the factor id, version, value, unit, lookup level and GWP set it used (WP-21 acceptance). Records come
 * from manual entry, approved Data Hub points (TRD §6.5.1) or gas flaring records.
 */
@Service
public class EmissionService {

    static final String SELECT = "select r.*, o.name as org_name, f.source as factor_source, f.category as factor_category, "
            + "f.region as factor_region from carbon.emission_records r join auth.organisations o on o.id = r.org_id "
            + "left join carbon.emission_factors f on f.id = r.emission_factor_id ";

    private final JdbcTemplate jdbc;
    private final EmissionFactorService factors;
    private final OrgScopeService orgScope;
    private final ReportingPeriodService periods;
    private final FxRateService fx;
    private final LineageService lineage;
    private final com.esg.reference.EmissionActivityService activities;

    public EmissionService(JdbcTemplate jdbc, EmissionFactorService factors, OrgScopeService orgScope,
                           ReportingPeriodService periods, FxRateService fx, LineageService lineage,
                           com.esg.reference.EmissionActivityService activities) {
        this.activities = activities;
        this.jdbc = jdbc;
        this.factors = factors;
        this.orgScope = orgScope;
        this.periods = periods;
        this.fx = fx;
        this.lineage = lineage;
    }

    // ================================================================== read

    public Page<EmissionResponse> list(String scope, String source, UUID orgId, LocalDate from, LocalDate to, Pageable pageable) {
        StringBuilder where = new StringBuilder("where r.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (scope != null) {
            if (!scope.matches(CarbonDtos.SCOPE_REGEX)) {
                throw FieldValidationException.of("scope", "must be scope_1, scope_2 or scope_3");
            }
            where.append(" and r.scope = ?");
            args.add(scope);
        }
        if (source != null) {
            where.append(" and r.record_source = ?");
            args.add(source);
        }
        if (orgId != null) {
            where.append(" and r.org_id = ?");
            args.add(orgId);
        }
        if (from != null) {
            where.append(" and r.reporting_period_end >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" and r.reporting_period_start <= ?");
            args.add(Date.valueOf(to));
        }
        appendOrgScope(where, args, "r.org_id");
        Long total = jdbc.queryForObject("select count(*) from carbon.emission_records r " + where, Long.class, args.toArray());
        String order = pageable.getSort().stream().findFirst().map(o -> column(o.getProperty()) + (o.isAscending() ? " asc" : " desc"))
                .orElse("r.reporting_period_start desc, r.created_at desc");
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageable.getPageSize());
        pageArgs.add(pageable.getOffset());
        List<EmissionResponse> rows = jdbc.query(SELECT + where + " order by " + order + " limit ? offset ?",
                (rs, i) -> response(rs), pageArgs.toArray());
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    public EmissionResponse get(UUID id) {
        EmissionResponse r = jdbc.query(SELECT + "where r.id = ? and r.deleted_at is null", (rs, i) -> response(rs), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Emission record"));
        if (!orgScope.canAccess(r.orgId())) {
            throw ApiException.notFound("Emission record");
        }
        return r;
    }

    // ================================================================== write

    @Transactional
    public EmissionResponse create(EmissionRequest r) {
        Calculation c = calculate(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into carbon.emission_records (id, org_id, scope, scope3_category, source_activity, activity_data, "
                        + "activity_unit, emission_factor_id, factor_version, factor_value, factor_unit, factor_level, gwp_set, "
                        + "co2e_tonnes, co2_tonnes, ch4_tonnes, n2o_tonnes, reporting_period_start, reporting_period_end, "
                        + "calculation_method, spend_amount, spend_currency, fx_rate, notes, record_source, calculated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'manual', now())",
                id, r.orgId(), r.scope(), r.scope3Category(), r.sourceActivity(), c.activity(), c.unit(), c.factor().id(),
                c.factor().factorVersion(), c.factor().factorValue(), c.factor().unit(), c.level(), c.result().gwpSet(),
                c.result().co2eTonnes(), c.result().co2Tonnes(), c.result().ch4Tonnes(), c.result().n2oTonnes(),
                Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), r.method(), c.spendAmount(),
                c.spendCurrency(), c.fxRate(), blank(r.notes()));
        recordLineage(id, c);
        return get(id);
    }

    /** Runs the calculation without saving (the calculator's result step). */
    public CarbonDtos.Preview preview(EmissionRequest r) {
        Calculation c = calculate(r);
        EmissionFactor f = c.factor();
        return new CarbonDtos.Preview(c.activity(), c.unit(), c.spendAmount(), c.spendCurrency(), c.fxRate(),
                new FactorUsed(f.id(), f.factorVersion(), f.factorValue(), f.unit(), c.level(), f.source(), f.category(), f.region(),
                        f.verificationStatus()),
                c.result().gwpSet(), c.result().perGas(), c.result().co2eTonnes(), c.result().co2Tonnes(), c.result().ch4Tonnes(),
                c.result().n2oTonnes());
    }

    /** Manual, unverified records only; recalculated from the new values. */
    @Transactional
    public EmissionResponse update(UUID id, EmissionRequest r) {
        EmissionResponse current = editable(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        periods.requireOpen(current.reportingPeriodStart(), current.reportingPeriodEnd());
        Calculation c = calculate(r);
        int updated = jdbc.update("update carbon.emission_records set org_id = ?, scope = ?, scope3_category = ?, source_activity = ?, "
                        + "activity_data = ?, activity_unit = ?, emission_factor_id = ?, factor_version = ?, factor_value = ?, "
                        + "factor_unit = ?, factor_level = ?, gwp_set = ?, co2e_tonnes = ?, co2_tonnes = ?, ch4_tonnes = ?, "
                        + "n2o_tonnes = ?, reporting_period_start = ?, reporting_period_end = ?, calculation_method = ?, "
                        + "spend_amount = ?, spend_currency = ?, fx_rate = ?, notes = ?, calculated_at = now(), "
                        + "version = version + 1 where id = ? and version = ? and deleted_at is null",
                r.orgId(), r.scope(), r.scope3Category(), r.sourceActivity(), c.activity(), c.unit(), c.factor().id(),
                c.factor().factorVersion(), c.factor().factorValue(), c.factor().unit(), c.level(), c.result().gwpSet(),
                c.result().co2eTonnes(), c.result().co2Tonnes(), c.result().ch4Tonnes(), c.result().n2oTonnes(),
                Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), r.method(), c.spendAmount(),
                c.spendCurrency(), c.fxRate(), blank(r.notes()), id, r.version());
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This record was changed by someone else. Reload and try again.");
        }
        recordLineage(id, c);
        return get(id);
    }

    @Transactional
    public void delete(UUID id, Long version) {
        EmissionResponse current = editable(id);
        periods.requireOpen(current.reportingPeriodStart(), current.reportingPeriodEnd());
        int updated = jdbc.update("update carbon.emission_records set deleted_at = now(), version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", id, version);
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This record was changed by someone else. Reload and try again.");
        }
    }

    /**
     * WP-31: the Scope 3 record behind a supplier spend line, created or recalculated with the spend-based method
     * (record_source 'supply_chain', so it's edited through the spend line, not here). Returns its id.
     */
    @Transactional
    public UUID upsertForSupplierSpend(UUID spendId, EmissionRequest r) {
        Calculation c = calculate(r);
        List<UUID> existing = jdbc.queryForList("select id from carbon.emission_records where supplier_spend_id = ? and deleted_at is null",
                UUID.class, spendId);
        UUID id = existing.isEmpty() ? UUID.randomUUID() : existing.get(0);
        if (existing.isEmpty()) {
            jdbc.update("insert into carbon.emission_records (id, org_id, scope, scope3_category, source_activity, activity_data, "
                            + "activity_unit, emission_factor_id, factor_version, factor_value, factor_unit, factor_level, gwp_set, "
                            + "co2e_tonnes, co2_tonnes, ch4_tonnes, n2o_tonnes, reporting_period_start, reporting_period_end, "
                            + "calculation_method, spend_amount, spend_currency, fx_rate, notes, record_source, supplier_spend_id, calculated_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'supply_chain', ?, now())",
                    id, r.orgId(), r.scope(), r.scope3Category(), r.sourceActivity(), c.activity(), c.unit(), c.factor().id(),
                    c.factor().factorVersion(), c.factor().factorValue(), c.factor().unit(), c.level(), c.result().gwpSet(),
                    c.result().co2eTonnes(), c.result().co2Tonnes(), c.result().ch4Tonnes(), c.result().n2oTonnes(),
                    Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), r.method(), c.spendAmount(),
                    c.spendCurrency(), c.fxRate(), blank(r.notes()), spendId);
        } else {
            jdbc.update("update carbon.emission_records set org_id = ?, scope3_category = ?, source_activity = ?, activity_data = ?, activity_unit = ?, "
                            + "emission_factor_id = ?, factor_version = ?, factor_value = ?, factor_unit = ?, factor_level = ?, gwp_set = ?, co2e_tonnes = ?, "
                            + "co2_tonnes = ?, ch4_tonnes = ?, n2o_tonnes = ?, reporting_period_start = ?, reporting_period_end = ?, spend_amount = ?, "
                            + "spend_currency = ?, fx_rate = ?, notes = ?, calculated_at = now(), version = version + 1 where id = ?",
                    r.orgId(), r.scope3Category(), r.sourceActivity(), c.activity(), c.unit(), c.factor().id(), c.factor().factorVersion(),
                    c.factor().factorValue(), c.factor().unit(), c.level(), c.result().gwpSet(), c.result().co2eTonnes(), c.result().co2Tonnes(),
                    c.result().ch4Tonnes(), c.result().n2oTonnes(), Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()),
                    c.spendAmount(), c.spendCurrency(), c.fxRate(), blank(r.notes()), id);
        }
        recordLineage(id, c);
        return id;
    }

    /** A removed spend line no longer backs a Scope 3 record. */
    @Transactional
    public void removeForSupplierSpend(UUID spendId) {
        jdbc.update("update carbon.emission_records set deleted_at = now(), version = version + 1 where supplier_spend_id = ? and deleted_at is null",
                spendId);
    }

    /** Marks a record verified (carbon_data:approve): recalculation then leaves it alone. */
    @Transactional
    public EmissionResponse verify(UUID id, Long version) {
        EmissionResponse current = get(id);
        if (current.verified()) {
            throw ApiException.conflict("ESG-409-STATE", "This record is already verified");
        }
        int updated = jdbc.update("update carbon.emission_records set is_verified = true, verified_by = ?, version = version + 1 "
                + "where id = ? and version = ?", CurrentUser.access().userId(), id, version);
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This record was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    /**
     * Re-resolves the factor (and the tenant's GWP set) for every unverified, factor-based record in the range and
     * updates those whose factor or result changed — e.g. after a new factor version or custom factor (TRD §6.4.2 POST
     * /carbon/calculate, "trigger emission calculation job"; BRD-5.2.2-02 audit trail). Records whose factor was chosen explicitly keep it.
     */
    @Transactional
    public Recalculation recalculate(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = jdbc.queryForList("select r.id, r.org_id, r.source_activity, r.activity_data, "
                + "r.reporting_period_start, r.reporting_period_end, r.emission_factor_id, r.factor_version, r.co2e_tonnes, "
                + "r.gwp_set, r.calculation_method from carbon.emission_records r where r.deleted_at is null and not r.is_verified "
                + "and r.calculation_method <> 'flaring_composition' and coalesce(r.factor_level, 'country') <> 'selected' "
                + "and r.reporting_period_end >= ? and r.reporting_period_start <= ?", Date.valueOf(from), Date.valueOf(to));
        List<String> changes = new ArrayList<>();
        EmissionEngine.Gwp gwp = gwp();
        int updated = 0;
        for (Map<String, Object> row : rows) {
            UUID orgId = (UUID) row.get("org_id");
            if (!orgScope.canAccess(orgId)) {
                continue;
            }
            LocalDate end = ((Date) row.get("reporting_period_end")).toLocalDate();
            ResolvedFactor resolved;
            try {
                resolved = factors.resolve((String) row.get("source_activity"), country(orgId), end, null);
                requireUsable(resolved.factor());
            } catch (ApiException e) {
                continue; // no (usable) factor applies any more: leave the record as calculated
            }
            BigDecimal activity = (BigDecimal) row.get("activity_data");
            EmissionEngine.Result result = EmissionEngine.calculate(activity, resolved.factor(), gwp);
            boolean sameFactor = resolved.factor().id().equals(row.get("emission_factor_id"))
                    && resolved.factor().factorVersion().equals(row.get("factor_version"));
            boolean sameResult = result.co2eTonnes().compareTo((BigDecimal) row.get("co2e_tonnes")) == 0
                    && Objects.equals(result.gwpSet(), row.get("gwp_set"));
            if (sameFactor && sameResult) {
                continue;
            }
            UUID id = (UUID) row.get("id");
            jdbc.update("update carbon.emission_records set emission_factor_id = ?, factor_version = ?, factor_value = ?, factor_unit = ?, "
                            + "factor_level = ?, gwp_set = ?, co2e_tonnes = ?, co2_tonnes = ?, ch4_tonnes = ?, n2o_tonnes = ?, "
                            + "calculated_at = now(), version = version + 1 where id = ?",
                    resolved.factor().id(), resolved.factor().factorVersion(), resolved.factor().factorValue(), resolved.factor().unit(),
                    level(resolved.level()), result.gwpSet(), result.co2eTonnes(), result.co2Tonnes(), result.ch4Tonnes(),
                    result.n2oTonnes(), id);
            lineage.record("emission_factor", resolved.factor().id(), "emission_record", id, "recalculated_from",
                    Map.of("from", row.get("co2e_tonnes"), "to", result.co2eTonnes()));
            changes.add(row.get("source_activity") + " " + row.get("reporting_period_start") + ": " + row.get("co2e_tonnes")
                    + " → " + result.co2eTonnes() + " t");
            updated++;
        }
        return new Recalculation(rows.size(), updated, changes);
    }

    // ================================================================== Data Hub link (TRD §6.5.1)

    /** An approved Data Hub point that is activity data becomes (or replaces) its emission record. */
    @Transactional
    public void fromApprovedDataPoint(UUID dataPointId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select p.org_id, p.metric_key, p.numeric_value, p.reporting_period_start, "
                + "p.reporting_period_end, a.source_activity, a.scope, a.scope3_category, a.activity_unit "
                + "from data_hub.data_points p join reference.metric_activities a on a.metric_key = p.metric_key "
                + "where p.id = ? and p.validation_status = 'approved' and p.numeric_value is not null and p.deleted_at is null",
                dataPointId);
        if (rows.isEmpty()) {
            return; // not activity data (or not approved)
        }
        Map<String, Object> p = rows.get(0);
        UUID orgId = (UUID) p.get("org_id");
        LocalDate start = ((Date) p.get("reporting_period_start")).toLocalDate();
        LocalDate end = ((Date) p.get("reporting_period_end")).toLocalDate();
        String activity = (String) p.get("source_activity");
        ResolvedFactor resolved;
        try {
            resolved = factors.resolve(activity, country(orgId), end, null);
        } catch (ApiException e) {
            return; // no factor for this activity here: the point stays in the Data Hub, nothing is invented
        }
        try {
            requireUsable(resolved.factor());
        } catch (ApiException e) {
            return; // the tenant blocks provisional factors: no record until a reviewed or custom factor exists
        }
        BigDecimal amount = (BigDecimal) p.get("numeric_value");
        EmissionEngine.Result result = EmissionEngine.calculate(amount, resolved.factor(), gwp());
        // Phase 3 (1): a corrected point (reopened, edited, approved again) gets a new record; the one it replaces is
        // kept (soft-deleted) and linked to it, so the record's history is traceable.
        List<UUID> previous = jdbc.queryForList("select id from carbon.emission_records where data_point_id = ? "
                + "order by created_at desc limit 1", UUID.class, dataPointId);
        removeForDataPoint(dataPointId);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into carbon.emission_records (id, org_id, scope, scope3_category, source_activity, activity_data, activity_unit, "
                        + "emission_factor_id, factor_version, factor_value, factor_unit, factor_level, gwp_set, co2e_tonnes, co2_tonnes, "
                        + "ch4_tonnes, n2o_tonnes, reporting_period_start, reporting_period_end, calculation_method, record_source, "
                        + "data_point_id, calculated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'activity_based', "
                        + "'data_hub', ?, now())",
                id, orgId, p.get("scope"), p.get("scope3_category"), activity, amount, p.get("activity_unit"), resolved.factor().id(),
                resolved.factor().factorVersion(), resolved.factor().factorValue(), resolved.factor().unit(), level(resolved.level()),
                result.gwpSet(), result.co2eTonnes(), result.co2Tonnes(), result.ch4Tonnes(), result.n2oTonnes(), Date.valueOf(start),
                Date.valueOf(end), dataPointId);
        lineage.record("data_point", dataPointId, "emission_record", id, "calculated_from", Map.of("activityData", amount));
        lineage.record("emission_factor", resolved.factor().id(), "emission_record", id, "calculated_from",
                Map.of("factorVersion", resolved.factor().factorVersion(), "co2eTonnes", result.co2eTonnes()));
        if (!previous.isEmpty()) {
            lineage.record("emission_record", previous.get(0), "emission_record", id, "superseded_by",
                    Map.of("dataPointId", dataPointId, "activityData", amount));
        }
    }

    /** A reopened data point no longer backs an emission record. */
    @Transactional
    public void removeForDataPoint(UUID dataPointId) {
        jdbc.update("update carbon.emission_records set deleted_at = now(), version = version + 1 "
                + "where data_point_id = ? and deleted_at is null", dataPointId);
    }

    // ================================================================== dashboard (Carbon Dashboard, TRD §7.4)

    public Summary summary(int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        StringBuilder where = new StringBuilder("where r.deleted_at is null and r.reporting_period_start between ? and ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to)));
        appendOrgScope(where, args, "r.org_id");
        Map<String, BigDecimal> scopes = scopeTotals(year, true);
        BigDecimal s1 = scopes.getOrDefault("scope_1", BigDecimal.ZERO);
        BigDecimal s2 = scopes.getOrDefault("scope_2", BigDecimal.ZERO);
        BigDecimal s3 = scopes.getOrDefault("scope_3", BigDecimal.ZERO);
        BigDecimal avoided = jdbc.queryForObject("select coalesce(sum(i.avoided), 0) from (" + InstrumentService.AVOIDED_SQL + ") i "
                + "where i.period_start between ? and ?", BigDecimal.class, Date.valueOf(from), Date.valueOf(to));
        BigDecimal s2Market = s2.subtract(avoided == null ? BigDecimal.ZERO : avoided).max(BigDecimal.ZERO);
        BigDecimal flaring = jdbc.queryForObject("select coalesce(sum(r.co2e_tonnes), 0) from carbon.emission_records r " + where
                + " and r.calculation_method = 'flaring_composition'", BigDecimal.class, args.toArray());
        Integer count = jdbc.queryForObject("select count(*) from carbon.emission_records r " + where, Integer.class, args.toArray());

        // F15: a record covering several months is split across them pro rata by days (a quarter's diesel isn't all in
        // its first month). Records belong to the year they start in, like the totals, so a record running into next
        // year shows its later months as they are (e.g. 2027-01) and the months add up to the total.
        List<Bucket> byMonth = jdbc.query("select to_char(m, 'YYYY-MM') as month, sum(r.co2e_tonnes * "
                + "((least(r.reporting_period_end, (m + interval '1 month' - interval '1 day')::date) - greatest(r.reporting_period_start, m::date) + 1)::numeric "
                + "/ (r.reporting_period_end - r.reporting_period_start + 1))) from carbon.emission_records r "
                + "cross join lateral generate_series(date_trunc('month', r.reporting_period_start), "
                + "date_trunc('month', r.reporting_period_end), interval '1 month') m " + where + " group by month order by month",
                (rs, i) -> new Bucket(rs.getString(1), rs.getString(1), scale(rs.getBigDecimal(2))), args.toArray());
        List<Bucket> bySource = jdbc.query("select r.source_activity, sum(r.co2e_tonnes) s from carbon.emission_records r " + where
                + " group by r.source_activity order by s desc",
                (rs, i) -> new Bucket(rs.getString(1), rs.getString(1), scale(rs.getBigDecimal(2))), args.toArray());
        List<Bucket> byOrg = jdbc.query("select o.id::text, o.name, sum(r.co2e_tonnes) s from carbon.emission_records r "
                + "join auth.organisations o on o.id = r.org_id " + where + " group by o.id, o.name order by s desc",
                (rs, i) -> new Bucket(rs.getString(1), rs.getString(2), scale(rs.getBigDecimal(3))), args.toArray());
        List<Bucket> byCat = jdbc.query("select r.scope3_category::text, c.name, sum(r.co2e_tonnes) s from carbon.emission_records r "
                + "left join reference.scope3_categories c on c.category = r.scope3_category " + where
                + " and r.scope = 'scope_3' group by r.scope3_category, c.name order by r.scope3_category",
                (rs, i) -> new Bucket(rs.getString(1), rs.getString(2), scale(rs.getBigDecimal(3))), args.toArray());
        BigDecimal total = s1.add(s2).add(s3);
        return new Summary(year, scale(s1), scale(s2), scale(s2Market), scale(s3), scale(total), scale(s1.add(s2Market).add(s3)),
                scale(flaring), byMonth, bySource, byOrg, byCat, intensity(year, total), count == null ? 0 : count, intensityGaps(year));
    }

    /**
     * Tonnes per scope (location-based Scope 2) of the records that start in the year: the basis of the Overview's
     * totals and of reduction-target actuals (F6), so the two always agree. {@code orgScoped}: only the caller's
     * organisations (the Overview); targets are tenant-wide.
     */
    public Map<String, BigDecimal> scopeTotals(int year, boolean orgScoped) {
        StringBuilder where = new StringBuilder("where r.deleted_at is null and r.reporting_period_start between ? and ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(LocalDate.of(year, 1, 1)), Date.valueOf(LocalDate.of(year, 12, 31))));
        if (orgScoped) {
            appendOrgScope(where, args, "r.org_id");
        }
        Map<String, BigDecimal> scopes = new LinkedHashMap<>();
        jdbc.query("select r.scope, sum(r.co2e_tonnes) from carbon.emission_records r " + where + " group by r.scope",
                rs -> {
                    scopes.put(rs.getString(1), rs.getBigDecimal(2));
                }, args.toArray());
        return scopes;
    }

    /** One period of the executive trend (WP-24): tonnes by scope, location-based Scope 2. */
    public record TrendPoint(String period, BigDecimal scope1, BigDecimal scope2Location, BigDecimal scope3, BigDecimal total) {}

    /**
     * WP-24: emissions per year or quarter between {@code from} and {@code to} (inclusive), in the caller's org scope.
     * A record spanning several months is split pro rata by days, as on the Carbon Overview (F15), so periods add up.
     */
    public List<TrendPoint> trend(LocalDate from, LocalDate to, boolean quarterly) {
        StringBuilder where = new StringBuilder("where r.deleted_at is null and r.reporting_period_end >= ? and r.reporting_period_start <= ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to)));
        appendOrgScope(where, args, "r.org_id");
        args.add(Date.valueOf(from.withDayOfMonth(1)));
        args.add(Date.valueOf(to));
        String bucket = quarterly ? "to_char(m, 'YYYY') || '-Q' || extract(quarter from m)" : "to_char(m, 'YYYY')";
        Map<String, Map<String, BigDecimal>> byPeriod = new java.util.TreeMap<>();
        jdbc.query("select " + bucket + " as period, r.scope, sum(r.co2e_tonnes * "
                + "((least(r.reporting_period_end, (m + interval '1 month' - interval '1 day')::date) - greatest(r.reporting_period_start, m::date) + 1)::numeric "
                + "/ (r.reporting_period_end - r.reporting_period_start + 1))) from carbon.emission_records r "
                + "cross join lateral generate_series(date_trunc('month', r.reporting_period_start), date_trunc('month', r.reporting_period_end), "
                + "interval '1 month') m " + where + " and m >= ? and m <= ? group by period, r.scope", rs -> {
                    byPeriod.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>()).put(rs.getString(2), rs.getBigDecimal(3));
                }, args.toArray());
        List<TrendPoint> out = new ArrayList<>();
        for (String period : periods(from, to, quarterly)) {
            Map<String, BigDecimal> s = byPeriod.getOrDefault(period, Map.of());
            BigDecimal s1 = s.getOrDefault("scope_1", BigDecimal.ZERO);
            BigDecimal s2 = s.getOrDefault("scope_2", BigDecimal.ZERO);
            BigDecimal s3 = s.getOrDefault("scope_3", BigDecimal.ZERO);
            out.add(new TrendPoint(period, scale(s1), scale(s2), scale(s3), scale(s1.add(s2).add(s3))));
        }
        return out;
    }

    private static List<String> periods(LocalDate from, LocalDate to, boolean quarterly) {
        List<String> out = new ArrayList<>();
        for (LocalDate d = from.withDayOfMonth(1); !d.isAfter(to); d = d.plusMonths(quarterly ? 3 : 12)) {
            out.add(quarterly ? d.getYear() + "-Q" + ((d.getMonthValue() - 1) / 3 + 1) : String.valueOf(d.getYear()));
        }
        return out;
    }

    /** BRD-5.2.2-07: carbon intensity by revenue, headcount and production, from approved Data Hub points of the year. */
    List<Intensity> intensity(int year, BigDecimal tonnes) {
        List<Intensity> out = new ArrayList<>();
        Map<String, BigDecimal> denominators = denominators(year);
        String base = jdbc.queryForObject("select base_currency from auth.tenant_settings", String.class);
        BigDecimal revenue = denominators.get("revenue");
        if (revenue != null && revenue.signum() > 0) {
            BigDecimal millions = revenue.divide(BigDecimal.valueOf(1_000_000), MathContext.DECIMAL64);
            out.add(new Intensity("revenue", revenue, base, tonnes.divide(millions, 6, RoundingMode.HALF_UP), "per million " + base));
        }
        BigDecimal employees = denominators.get("employees");
        if (employees != null && employees.signum() > 0) {
            out.add(new Intensity("employees", employees, "employees", tonnes.divide(employees, 6, RoundingMode.HALF_UP), "per employee"));
        }
        BigDecimal production = denominators.get("production");
        if (production != null && production.signum() > 0) {
            out.add(new Intensity("production", production, "units", tonnes.divide(production, 6, RoundingMode.HALF_UP), "per unit produced"));
        }
        return out;
    }

    // no-mock-gate: config — the Data Hub metric behind each intensity denominator (as in denominators())
    private static final Map<String, String> DENOMINATOR_METRICS = new LinkedHashMap<>(Map.of(
            "revenue", "revenue", "employees", "employees_total", "production", "production_output"));

    /** Phase 3 (4): denominators without an approved value this year, with the points still awaiting approval. */
    List<CarbonDtos.IntensityGap> intensityGaps(int year) {
        Map<String, BigDecimal> have = denominators(year);
        List<CarbonDtos.IntensityGap> gaps = new ArrayList<>();
        for (String denominator : List.of("revenue", "employees", "production")) {
            BigDecimal v = have.get(denominator);
            if (v != null && v.signum() > 0) {
                continue;
            }
            String key = DENOMINATOR_METRICS.get(denominator);
            StringBuilder where = new StringBuilder("where p.metric_key = ? and p.deleted_at is null "
                    + "and p.validation_status in ('submitted', 'validated') and extract(year from p.reporting_period_start) = ?");
            List<Object> args = new ArrayList<>(List.of(key, year));
            appendOrgScope(where, args, "p.org_id");
            Integer waiting = jdbc.queryForObject("select count(*) from data_hub.data_points p " + where, Integer.class, args.toArray());
            Map<String, Object> m = jdbc.queryForMap("select name, unit from reference.metrics where metric_key = ?", key);
            gaps.add(new CarbonDtos.IntensityGap(denominator, key, (String) m.get("name"), (String) m.get("unit"),
                    waiting == null ? 0 : waiting));
        }
        return gaps;
    }

    /** Revenue and production summed over the year; headcount = average of the year's values. Approved points only. */
    Map<String, BigDecimal> denominators(int year) {
        Map<String, BigDecimal> d = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder("where p.validation_status = 'approved' and p.deleted_at is null "
                + "and extract(year from p.reporting_period_start) = ?");
        List<Object> args = new ArrayList<>(List.of(year));
        appendOrgScope(where, args, "p.org_id");
        jdbc.query("select p.metric_key, case when p.metric_key = 'employees_total' then avg(p.numeric_value) "
                + "else sum(p.numeric_value) end from data_hub.data_points p " + where
                + " and p.metric_key in ('revenue', 'employees_total', 'production_output') group by p.metric_key", rs -> {
                    String key = switch (rs.getString(1)) {
                        case "employees_total" -> "employees";
                        case "production_output" -> "production";
                        default -> "revenue";
                    };
                    d.put(key, rs.getBigDecimal(2));
                }, args.toArray());
        return d;
    }

    // ================================================================== the calculation

    record Calculation(BigDecimal activity, String unit, EmissionFactor factor, String level, EmissionEngine.Result result,
                       BigDecimal spendAmount, String spendCurrency, BigDecimal fxRate) {}

    private Calculation calculate(EmissionRequest r) {
        orgScope.requireAccess(r.orgId());
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.reportingPeriodEnd().isBefore(r.reportingPeriodStart())) {
            errors.put("reportingPeriodEnd", "must be on or after the start date");
        }
        if ("scope_3".equals(r.scope()) && r.scope3Category() == null) {
            errors.put("scope3Category", "is required for Scope 3");
        }
        if (!"scope_3".equals(r.scope()) && r.scope3Category() != null) {
            errors.put("scope3Category", "only applies to Scope 3");
        }
        boolean spend = "spend_based".equals(r.method());
        if (spend) {
            if (r.spendAmount() == null) {
                errors.put("spendAmount", "is required for spend-based calculations");
            }
            if (r.spendCurrency() == null || r.spendCurrency().isBlank()) {
                errors.put("spendCurrency", "is required for spend-based calculations");
            }
        } else if (r.activityData() == null) {
            errors.put("activityData", "is required");
        }
        // F5: the activity must belong to the chosen scope (and Scope 3 category).
        activities.find(r.sourceActivity()).ifPresentOrElse(a -> {
            if (!a.scope().equals(r.scope())) {
                errors.put("sourceActivity", a.label() + " is a " + scopeName(a.scope()) + " activity, not " + scopeName(r.scope()));
            } else if ("scope_3".equals(a.scope()) && r.scope3Category() != null && !r.scope3Category().equals(a.scope3Category())) {
                errors.put("sourceActivity", a.label() + " belongs to Scope 3 category " + a.scope3Category());
            }
        }, () -> errors.put("sourceActivity", "Unknown activity: choose one from the list or add a custom factor for it"));
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        periods.requireOpen(r.reportingPeriodStart(), r.reportingPeriodEnd());

        EmissionFactor factor;
        String level;
        if (r.emissionFactorId() != null) {
            factor = factors.get(r.emissionFactorId()); // RLS: platform factors + the tenant's own
            level = "selected";
            if (!factor.category().equals(r.sourceActivity())) {
                throw FieldValidationException.of("emissionFactorId", "is a " + factor.category() + " factor, not " + r.sourceActivity());
            }
            if (factor.validFrom().isAfter(r.reportingPeriodEnd()) || (factor.validTo() != null && factor.validTo().isBefore(r.reportingPeriodEnd()))) {
                throw FieldValidationException.of("emissionFactorId", "isn't valid on " + r.reportingPeriodEnd());
            }
        } else {
            ResolvedFactor resolved;
            try {
                resolved = factors.resolve(r.sourceActivity(), country(r.orgId()), r.reportingPeriodEnd(), null);
            } catch (ApiException e) {
                throw FieldValidationException.of("sourceActivity", "No emission factor for " + r.sourceActivity()
                        + " applies to this organisation on " + r.reportingPeriodEnd() + ". Add a custom factor or choose one.");
            }
            factor = resolved.factor();
            level = level(resolved.level());
        }
        requireUsable(factor);
        if (spend != "spend".equals(factor.factorMethod())) {
            throw FieldValidationException.of(r.emissionFactorId() != null ? "emissionFactorId" : "sourceActivity",
                    spend ? "needs a spend-based factor (kgCO2e per currency unit)" : "has a spend-based factor; use the spend-based method");
        }

        BigDecimal activity;
        String unit;
        BigDecimal fxRate = null;
        String factorUnit = factor.unit().contains("/") ? factor.unit().substring(factor.unit().indexOf('/') + 1) : factor.unit();
        if (spend) {
            activity = r.spendAmount();
            if (!r.spendCurrency().equals(factor.spendCurrency())) {
                FxRateService.Rate rate = fx.resolve(r.spendCurrency(), factor.spendCurrency(), r.reportingPeriodEnd())
                        .orElseThrow(() -> FieldValidationException.of("spendCurrency", "No " + r.spendCurrency() + "→"
                                + factor.spendCurrency() + " exchange rate on or before " + r.reportingPeriodEnd()
                                + ". Add one under Data Hub → Exchange rates."));
                fxRate = rate.rate();
                activity = r.spendAmount().multiply(rate.rate()).setScale(6, RoundingMode.HALF_UP);
            }
            unit = factor.spendCurrency();
        } else {
            activity = r.activityData();
            unit = r.activityUnit() == null || r.activityUnit().isBlank() ? factorUnit : r.activityUnit().trim();
            if (!unit.equals(factorUnit)) {
                throw FieldValidationException.of("activityUnit", "must be " + factorUnit + " for this factor (" + factor.unit() + ")");
            }
        }
        EmissionEngine.Result result = EmissionEngine.calculate(activity, factor, gwp());
        return new Calculation(activity, unit, factor, level, result, spend ? r.spendAmount() : null,
                spend ? r.spendCurrency() : null, fxRate);
    }

    private void recordLineage(UUID recordId, Calculation c) {
        lineage.record("emission_factor", c.factor().id(), "emission_record", recordId, "calculated_from",
                Map.of("activityData", c.activity(), "activityUnit", c.unit(), "factorVersion", c.factor().factorVersion(),
                        "co2eTonnes", c.result().co2eTonnes()));
    }

    private EmissionResponse editable(UUID id) {
        EmissionResponse r = get(id);
        if (!"manual".equals(r.recordSource())) {
            throw ApiException.conflict("ESG-409-STATE", "This record comes from " + ("data_hub".equals(r.recordSource())
                    ? "an approved Data Hub point; reopen the point to change it" : "supply_chain".equals(r.recordSource())
                    ? "supplier spend; edit the spend line in Supply Chain instead" : "a flaring record; edit that instead"));
        }
        if (r.verified()) {
            throw ApiException.conflict("ESG-409-STATE", "A verified record can't be changed");
        }
        return r;
    }

    /** The tenant's GWP set (AR6 by default) with its CH4 and N2O values. */
    EmissionEngine.Gwp gwp() {
        String set = jdbc.queryForObject("select gwp_set from auth.tenant_settings", String.class);
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        jdbc.query("select gas, value from reference.gwp_values where gwp_set = ?", rs -> {
            values.put(rs.getString(1), rs.getBigDecimal(2));
        }, set);
        return new EmissionEngine.Gwp(set, values.get("CH4"), values.get("N2O"));
    }

    /** The organisation's country, else the tenant's. */
    String country(UUID orgId) {
        return jdbc.queryForObject("select coalesce(o.country_code, t.country_code) from auth.organisations o "
                + "join auth.tenants t on t.id = o.tenant_id where o.id = ?", String.class, orgId);
    }

    void appendOrgScope(StringBuilder where, List<Object> args, String column) {
        OrgScopeService.QueryScope scope = orgScope.queryScope();
        if (!scope.all()) {
            where.append(" and ").append(column).append(" = any(?::uuid[])");
            args.add(scope.orgIds().stream().map(UUID::toString).toArray(String[]::new));
        }
    }

    static String level(String resolverLevel) {
        return "tenant_custom".equals(resolverLevel) ? "custom" : resolverLevel;
    }

    private static String column(String property) {
        return switch (property) {
            case "co2eTonnes" -> "r.co2e_tonnes";
            case "scope" -> "r.scope";
            case "sourceActivity" -> "r.source_activity";
            case "createdAt" -> "r.created_at";
            default -> "r.reporting_period_start";
        };
    }

    /**
     * S7: a factor still pending SME review may be used (the record is flagged provisional) unless the tenant set
     * provisional factors to "block".
     */
    private void requireUsable(EmissionFactor factor) {
        if (!"pending_sme".equals(factor.verificationStatus())) {
            return;
        }
        String policy = jdbc.query("select provisional_factor_policy from auth.tenant_settings", (rs, i) -> rs.getString(1))
                .stream().findFirst().orElse("allow_flagged");
        if ("block".equals(policy)) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "ESG-409-FACTOR-PENDING",
                    "The " + factor.category() + " factor (" + factor.source() + ") is still pending expert review, and your "
                            + "organisation blocks provisional factors (Administration → Settings). Add a custom factor or ask an "
                            + "administrator.");
        }
    }

    private static String scopeName(String scope) {
        return "Scope " + scope.substring(scope.length() - 1);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(6, RoundingMode.HALF_UP);
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    static EmissionResponse response(ResultSet rs) throws SQLException {
        UUID factorId = rs.getObject("emission_factor_id", UUID.class);
        FactorUsed factor = factorId == null ? null : new FactorUsed(factorId, rs.getString("factor_version"),
                rs.getBigDecimal("factor_value") == null ? null : rs.getBigDecimal("factor_value").stripTrailingZeros(),
                rs.getString("factor_unit"), rs.getString("factor_level"), rs.getString("factor_source"),
                rs.getString("factor_category"), rs.getString("factor_region"), rs.getString("factor_review_status"));
        Timestamp calculated = rs.getTimestamp("calculated_at");
        return new EmissionResponse(rs.getObject("id", UUID.class), rs.getObject("org_id", UUID.class), rs.getString("org_name"),
                rs.getString("scope"), (Integer) rs.getObject("scope3_category"), rs.getString("source_activity"),
                rs.getBigDecimal("activity_data"), rs.getString("activity_unit"), rs.getString("calculation_method"),
                rs.getBigDecimal("spend_amount"), rs.getString("spend_currency"), rs.getBigDecimal("fx_rate"), factor,
                rs.getString("gwp_set"), rs.getBigDecimal("co2e_tonnes"), rs.getBigDecimal("co2_tonnes"), rs.getBigDecimal("ch4_tonnes"),
                rs.getBigDecimal("n2o_tonnes"), rs.getString("record_source"), rs.getObject("data_point_id", UUID.class),
                rs.getObject("flaring_record_id", UUID.class), rs.getDate("reporting_period_start").toLocalDate(),
                rs.getDate("reporting_period_end").toLocalDate(), rs.getString("notes"), rs.getBoolean("is_verified"),
                calculated == null ? null : calculated.toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
