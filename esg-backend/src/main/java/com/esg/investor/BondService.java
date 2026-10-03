package com.esg.investor;

import com.esg.analytics.AnalyticsDtos.Kpi;
import com.esg.analytics.AnalyticsDtos.KpiPoint;
import com.esg.analytics.KpiService;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.investor.InvestorDtos.Allocation;
import com.esg.investor.InvestorDtos.AllocationRequest;
import com.esg.investor.InvestorDtos.Bond;
import com.esg.investor.InvestorDtos.BondRequest;
import com.esg.investor.InvestorDtos.SptStatus;
import com.esg.security.OrgScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sustainable finance instruments (WP-40, D35 (6); TRD BondMonitoringDashboard): green, social and sustainability bonds
 * with use-of-proceeds allocations, sustainability-linked bonds tied to a custom KPI and its target, and sukuk with their
 * Shariah review. Investors see the instruments marked visible.
 */
@Service
public class BondService {

    private final JdbcTemplate jdbc;
    private final InvestorSupport support;
    private final KpiService kpis;
    private final OrgScopeService orgScope;

    public BondService(JdbcTemplate jdbc, InvestorSupport support, KpiService kpis, OrgScopeService orgScope) {
        this.jdbc = jdbc;
        this.support = support;
        this.kpis = kpis;
        this.orgScope = orgScope;
    }

    public List<Bond> list() {
        return jdbc.query("select * from investor.bonds where deleted_at is null" + (support.manager() ? "" : " and visible_to_investors")
                + " order by status, issue_date desc", (rs, i) -> bond(rs));
    }

    public Bond get(UUID id) {
        Bond b = jdbc.query("select * from investor.bonds where id = ? and deleted_at is null", (rs, i) -> bond(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Instrument"));
        if (!support.manager() && !b.visibleToInvestors()) {
            throw ApiException.notFound("Instrument");
        }
        return b;
    }

    @Transactional
    public Bond create(BondRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into investor.bonds (id, instrument, name, isin, issue_date, maturity_date, amount, currency, framework_reference, external_reviewer, "
                + "shariah_reviewer, shariah_pronouncement_on, kpi_id, spt_value, spt_observation_year, coupon_step_up_bps, visible_to_investors, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", args(id, r));
        return get(id);
    }

    @Transactional
    public Bond update(UUID id, BondRequest r) {
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r);
        Object[] a = args(id, r);
        Object[] withVersion = new Object[a.length + 1];
        System.arraycopy(a, 1, withVersion, 0, a.length - 1);
        withVersion[a.length - 1] = id;
        withVersion[a.length] = r.version();
        int n = jdbc.update("update investor.bonds set instrument = ?, name = ?, isin = ?, issue_date = ?, maturity_date = ?, amount = ?, currency = ?, "
                + "framework_reference = ?, external_reviewer = ?, shariah_reviewer = ?, shariah_pronouncement_on = ?, kpi_id = ?, spt_value = ?, "
                + "spt_observation_year = ?, coupon_step_up_bps = ?, visible_to_investors = ?, status = ?, version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", withVersion);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This instrument was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from investor.bond_allocations where bond_id = ? and deleted_at is null)",
                Boolean.class, id))) {
            throw ApiException.conflict("ESG-409-IN-USE", "Remove the allocations first (or mark the instrument redeemed).");
        }
        jdbc.update("update investor.bonds set deleted_at = now(), version = version + 1 where id = ?", id);
    }

    // ---------------------------------------------------------------- allocations (period-locked by their date)

    @Transactional
    public Bond addAllocation(UUID bondId, AllocationRequest r) {
        Bond b = get(bondId);
        checkAllocation(b, r, null);
        jdbc.update("insert into investor.bond_allocations (bond_id, org_id, category, project, amount, allocated_on, impact_note) values (?, ?, ?, ?, ?, ?, ?)",
                bondId, r.orgId(), r.category(), r.project().trim(), r.amount(), Date.valueOf(r.allocatedOn()), ScorecardService.blank(r.impactNote()));
        return get(bondId);
    }

    @Transactional
    public Bond updateAllocation(UUID bondId, UUID id, AllocationRequest r) {
        Bond b = get(bondId);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        checkAllocation(b, r, id);
        int n = jdbc.update("update investor.bond_allocations set org_id = ?, category = ?, project = ?, amount = ?, allocated_on = ?, impact_note = ?, "
                + "version = version + 1 where id = ? and bond_id = ? and version = ? and deleted_at is null", r.orgId(), r.category(), r.project().trim(),
                r.amount(), Date.valueOf(r.allocatedOn()), ScorecardService.blank(r.impactNote()), id, bondId, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This allocation was changed by someone else. Reload and try again.");
        }
        return get(bondId);
    }

    @Transactional
    public Bond deleteAllocation(UUID bondId, UUID id) {
        get(bondId);
        if (jdbc.update("update investor.bond_allocations set deleted_at = now(), version = version + 1 where id = ? and bond_id = ? and deleted_at is null",
                id, bondId) == 0) {
            throw ApiException.notFound("Allocation");
        }
        return get(bondId);
    }

    // ---------------------------------------------------------------- internals

    private void check(BondRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        boolean slb = "sustainability_linked_bond".equals(r.instrument());
        boolean sukuk = r.instrument().endsWith("sukuk");
        if (!r.maturityDate().isAfter(r.issueDate())) {
            errors.put("maturityDate", "must be after the issue date");
        }
        if (slb) {
            if (r.kpiId() == null) {
                errors.put("kpiId", "a sustainability-linked bond needs its KPI");
            } else if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from analytics.custom_kpis where id = ? and deleted_at is null)",
                    Boolean.class, r.kpiId()))) {
                errors.put("kpiId", "must be one of your KPIs");
            }
            if (r.sptValue() == null) {
                errors.put("sptValue", "set the sustainability performance target");
            }
            if (r.sptObservationYear() == null) {
                errors.put("sptObservationYear", "set the year the target is observed");
            }
        } else if (r.kpiId() != null || r.sptValue() != null || r.sptObservationYear() != null || r.couponStepUpBps() != null) {
            errors.put("kpiId", "only a sustainability-linked bond has a KPI and target");
        }
        if (sukuk && (r.shariahReviewer() == null || r.shariahReviewer().isBlank())) {
            errors.put("shariahReviewer", "a sukuk needs its Shariah reviewer");
        }
        if (!sukuk && (r.shariahReviewer() != null && !r.shariahReviewer().isBlank() || r.shariahPronouncementOn() != null)) {
            errors.put("shariahReviewer", "only a sukuk has a Shariah review");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private void checkAllocation(Bond b, AllocationRequest r, UUID id) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from auth.organisations where id = ? and deleted_at is null)", Boolean.class,
                r.orgId()))) {
            throw FieldValidationException.of("orgId", "must be an organisation in your tenant");
        }
        orgScope.requireAccess(r.orgId());
        if (r.allocatedOn().isBefore(b.issueDate())) {
            throw FieldValidationException.of("allocatedOn", "can't be before the issue date (" + b.issueDate() + ")");
        }
        if (r.allocatedOn().isAfter(support.today())) {
            throw FieldValidationException.of("allocatedOn", "can't be in the future");
        }
        BigDecimal others = jdbc.queryForObject("select coalesce(sum(amount), 0) from investor.bond_allocations where bond_id = ? and deleted_at is null "
                + "and id is distinct from ?", BigDecimal.class, b.id(), id);
        if (others.add(r.amount()).compareTo(b.amount()) > 0) {
            throw FieldValidationException.of("amount", "would allocate more than the " + b.currency() + " " + b.amount().toPlainString() + " raised ("
                    + b.amount().subtract(others).toPlainString() + " left)");
        }
    }

    private static Object[] args(UUID id, BondRequest r) {
        boolean slb = "sustainability_linked_bond".equals(r.instrument());
        return new Object[] {id, r.instrument(), r.name().trim(), ScorecardService.blank(r.isin()), Date.valueOf(r.issueDate()), Date.valueOf(r.maturityDate()),
            r.amount(), r.currency(), r.frameworkReference().trim(), ScorecardService.blank(r.externalReviewer()), ScorecardService.blank(r.shariahReviewer()),
            r.shariahPronouncementOn() == null ? null : Date.valueOf(r.shariahPronouncementOn()), slb ? r.kpiId() : null, slb ? r.sptValue() : null,
            slb ? r.sptObservationYear() : null, slb ? r.couponStepUpBps() : null, r.visibleToInvestors(), r.status()};
    }

    private Bond bond(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        BigDecimal amount = rs.getBigDecimal("amount");
        List<Allocation> allocations = jdbc.query("select a.*, o.name as org_name from investor.bond_allocations a join auth.organisations o on o.id = a.org_id "
                + "where a.bond_id = ? and a.deleted_at is null order by a.allocated_on, a.project", (r, i) -> new Allocation(r.getObject("id", UUID.class),
                r.getObject("org_id", UUID.class), r.getString("org_name"), r.getString("category"), r.getString("project"), r.getBigDecimal("amount"),
                r.getObject("allocated_on", LocalDate.class), r.getString("impact_note"), r.getLong("version")), id);
        BigDecimal allocated = allocations.stream().map(Allocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        allocations.forEach(a -> byCategory.merge(a.category(), a.amount(), BigDecimal::add));
        UUID kpiId = rs.getObject("kpi_id", UUID.class);
        BigDecimal spt = rs.getBigDecimal("spt_value");
        Integer year = (Integer) rs.getObject("spt_observation_year");
        return new Bond(id, rs.getString("instrument"), rs.getString("name"), rs.getString("isin"), rs.getObject("issue_date", LocalDate.class),
                rs.getObject("maturity_date", LocalDate.class), amount, rs.getString("currency"), rs.getString("framework_reference"),
                rs.getString("external_reviewer"), rs.getString("shariah_reviewer"), rs.getObject("shariah_pronouncement_on", LocalDate.class), kpiId, spt, year,
                (Integer) rs.getObject("coupon_step_up_bps"), rs.getBoolean("visible_to_investors"), rs.getString("status"), allocated, amount.subtract(allocated),
                allocated.multiply(BigDecimal.valueOf(100)).divide(amount, 1, RoundingMode.HALF_UP), byCategory,
                kpiId == null ? null : spt(kpiId, spt, year), allocations, rs.getLong("version"));
    }

    /** Whether the sustainability performance target was met in its observation year (pending until that year has a value). */
    private SptStatus spt(UUID kpiId, BigDecimal target, int year) {
        Kpi k;
        try {
            k = kpis.get(kpiId);
        } catch (ApiException e) {
            return new SptStatus(null, null, null, target, year, null, "unknown", "The KPI is no longer available");
        }
        KpiPoint p = kpis.valueFor(k, year);
        if (p.value() == null) {
            return new SptStatus(k.name(), k.unit(), k.direction(), target, year, null, "pending", p.note());
        }
        boolean met = "lower_better".equals(k.direction()) ? p.value().compareTo(target) <= 0 : p.value().compareTo(target) >= 0;
        return new SptStatus(k.name(), k.unit(), k.direction(), target, year, p.value(), met ? "met" : "missed",
                met ? "Target met" : "Target missed: the coupon step-up applies");
    }
}
