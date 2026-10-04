package com.esg.supplychain;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.supplychain.SupplyDtos.Overview;
import com.esg.supplychain.SupplyDtos.ScoreHistory;
import com.esg.supplychain.SupplyDtos.Supplier;
import com.esg.supplychain.SupplyDtos.SupplierRequest;
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
 * Supplier register (WP-31; TRD §8.6 SupplierOnboardingService; BRD 5.2.6). Full or simplified onboarding; the score and
 * risk tier come from the latest reviewed questionnaire; "local" means in the tenant's own country (local content).
 */
@Service
public class SupplierService {

    private static final String SELECT = "select s.*, sec.name as sector_name, u.first_name || ' ' || u.last_name as owner_name, "
            + "(select count(*) from supply_chain.corrective_actions a where a.supplier_id = s.id and a.deleted_at is null "
            + "and a.status not in ('verified', 'closed')) as open_actions, "
            + "(select count(*) from supply_chain.questionnaires q where q.supplier_id = s.id and q.deleted_at is null "
            + "and q.status in ('sent', 'in_progress', 'submitted')) as open_questionnaires "
            + "from supply_chain.suppliers s left join reference.sectors sec on sec.code = s.sector_code left join auth.users u on u.id = s.owner_user_id ";

    private final JdbcTemplate jdbc;
    private final SupplySupport support;

    public SupplierService(JdbcTemplate jdbc, SupplySupport support) {
        this.jdbc = jdbc;
        this.support = support;
    }

    public List<Supplier> list(String status, String riskLevel, String tier) {
        StringBuilder where = new StringBuilder("where s.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            check(status, SupplyDtos.SUPPLIER_STATUS_REGEX, "status");
            where.append(" and s.status = ?");
            args.add(status);
        }
        if (riskLevel != null) {
            if ("unassessed".equals(riskLevel)) {
                where.append(" and s.risk_level is null");
            } else {
                check(riskLevel, "^(low|medium|high)$", "riskLevel");
                where.append(" and s.risk_level = ?");
                args.add(riskLevel);
            }
        }
        if (tier != null) {
            check(tier, SupplyDtos.TIER_REGEX, "tier");
            where.append(" and s.tier = ?");
            args.add(tier);
        }
        String country = tenantCountry();
        Map<UUID, BigDecimal> spend = spendByCurrentYear();
        return jdbc.query(SELECT + where + " order by lower(s.name)", (rs, i) -> supplier(rs, country, spend, false), args.toArray());
    }

    public Supplier get(UUID id) {
        return jdbc.query(SELECT + "where s.id = ? and s.deleted_at is null", (rs, i) -> supplier(rs, tenantCountry(), spendByCurrentYear(), true), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Supplier"));
    }

    @Transactional
    public Supplier create(SupplierRequest r) {
        validate(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into supply_chain.suppliers (id, name, tier, onboarding, sector_code, country_code, state_region, registration_number, "
                        + "contact_name, contact_email, contact_phone, description, status, owner_user_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, r.name().trim(), r.tier(), r.onboarding(), blank(r.sectorCode()), r.countryCode(), blank(r.stateRegion()),
                blank(r.registrationNumber()), blank(r.contactName()), blank(r.contactEmail()), blank(r.contactPhone()), blank(r.description()),
                r.status(), r.ownerUserId());
        return get(id);
    }

    @Transactional
    public Supplier update(UUID id, SupplierRequest r) {
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        validate(r);
        int n = jdbc.update("update supply_chain.suppliers set name = ?, tier = ?, onboarding = ?, sector_code = ?, country_code = ?, state_region = ?, "
                        + "registration_number = ?, contact_name = ?, contact_email = ?, contact_phone = ?, description = ?, status = ?, owner_user_id = ?, "
                        + "version = version + 1 where id = ? and version = ? and deleted_at is null", r.name().trim(), r.tier(), r.onboarding(),
                blank(r.sectorCode()), r.countryCode(), blank(r.stateRegion()), blank(r.registrationNumber()), blank(r.contactName()),
                blank(r.contactEmail()), blank(r.contactPhone()), blank(r.description()), r.status(), r.ownerUserId(), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This supplier was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, long version) {
        Supplier s = get(id);
        if (s.openActions() > 0 || s.openQuestionnaires() > 0 || Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from supply_chain.supplier_spend where supplier_id = ? and deleted_at is null)", Boolean.class, id))) {
            throw ApiException.conflict("ESG-409-IN-USE", "This supplier has spend, questionnaires or corrective actions; offboard it instead.");
        }
        if (jdbc.update("update supply_chain.suppliers set deleted_at = now(), version = version + 1 where id = ? and version = ? and deleted_at is null",
                id, version) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This supplier was changed by someone else. Reload and try again.");
        }
    }

    // ---------------------------------------------------------------- overview and benchmarks

    public Overview overview(int year) {
        Map<String, Object> c = jdbc.queryForMap("select count(*) as n, count(*) filter (where status = 'active') as active, "
                + "count(*) filter (where esg_score is not null) as assessed, round(avg(esg_score), 1) as avg, "
                + "count(*) filter (where risk_level = 'low') as low, count(*) filter (where risk_level = 'medium') as medium, "
                + "count(*) filter (where risk_level = 'high') as high, count(*) filter (where risk_level is null) as unassessed "
                + "from supply_chain.suppliers where deleted_at is null and status <> 'offboarded'");
        Map<String, Integer> byRisk = new LinkedHashMap<>();
        for (String k : List.of("high", "medium", "low", "unassessed")) {
            byRisk.put(k, ((Number) c.get(k)).intValue());
        }
        Map<String, Object> a = jdbc.queryForMap("select count(*) filter (where status not in ('verified', 'closed')) as open, "
                + "count(*) filter (where status not in ('verified', 'closed') and due_date < ?) as overdue from supply_chain.corrective_actions "
                + "where deleted_at is null", Date.valueOf(support.today()));
        Integer review = jdbc.queryForObject("select count(*) from supply_chain.questionnaires where deleted_at is null and status = 'submitted'",
                Integer.class);
        String country = tenantCountry();
        Map<String, Object> spend = jdbc.queryForMap("select coalesce(sum(e.co2e_tonnes), 0) as tonnes from supply_chain.supplier_spend p "
                + "join carbon.emission_records e on e.id = p.emission_record_id and e.deleted_at is null where p.deleted_at is null "
                + "and extract(year from p.reporting_period_end) = ?", year);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal local = BigDecimal.ZERO;
        for (Map<String, Object> row : jdbc.queryForList("select s.country_code, p.amount, p.currency, p.reporting_period_end from supply_chain.supplier_spend p "
                + "join supply_chain.suppliers s on s.id = p.supplier_id where p.deleted_at is null and extract(year from p.reporting_period_end) = ?", year)) {
            BigDecimal base = toBase((BigDecimal) row.get("amount"), (String) row.get("currency"), ((Date) row.get("reporting_period_end")).toLocalDate());
            if (base == null) {
                continue;
            }
            total = total.add(base);
            if (country != null && country.equals(row.get("country_code"))) {
                local = local.add(base);
            }
        }
        Map<String, BigDecimal> sectors = new LinkedHashMap<>();
        jdbc.query("select coalesce(sector_code, '—'), round(avg(esg_score), 1) from supply_chain.suppliers where deleted_at is null "
                + "and esg_score is not null group by 1 order by 2 desc", rs -> {
                    sectors.put(rs.getString(1), rs.getBigDecimal(2));
                });
        return new Overview(((Number) c.get("n")).intValue(), ((Number) c.get("active")).intValue(), ((Number) c.get("assessed")).intValue(), byRisk,
                (BigDecimal) c.get("avg"), ((Number) a.get("open")).intValue(), ((Number) a.get("overdue")).intValue(), review == null ? 0 : review,
                total.setScale(2, RoundingMode.HALF_UP), total.signum() == 0 ? null : local.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP),
                base(), ((BigDecimal) spend.get("tonnes")).setScale(3, RoundingMode.HALF_UP), sectors, year);
    }

    String base() {
        return jdbc.queryForObject("select base_currency from auth.tenant_settings", String.class);
    }

    /** Amount in the base currency with the latest rate on or before the date; null when there is no rate. */
    BigDecimal toBase(BigDecimal amount, String currency, LocalDate on) {
        String base = base();
        if (base.equals(currency)) {
            return amount;
        }
        List<BigDecimal> rate = jdbc.queryForList("select rate from data_hub.fx_rates where from_currency = ? and to_currency = ? and rate_date <= ? "
                + "order by rate_date desc limit 1", BigDecimal.class, currency, base, Date.valueOf(on));
        return rate.isEmpty() ? null : amount.multiply(rate.get(0));
    }

    private String tenantCountry() {
        return jdbc.queryForObject("select country_code from auth.tenants where id = auth.current_tenant_id()", String.class);
    }

    private Map<UUID, BigDecimal> spendByCurrentYear() {
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        int year = support.today().getYear();
        for (Map<String, Object> row : jdbc.queryForList("select supplier_id, amount, currency, reporting_period_end from supply_chain.supplier_spend "
                + "where deleted_at is null and extract(year from reporting_period_end) in (?, ?)", year, year - 1)) {
            BigDecimal base = toBase((BigDecimal) row.get("amount"), (String) row.get("currency"), ((Date) row.get("reporting_period_end")).toLocalDate());
            if (base != null) {
                out.merge((UUID) row.get("supplier_id"), base, BigDecimal::add);
            }
        }
        return out;
    }

    private void validate(SupplierRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.sectorCode() != null && !r.sectorCode().isBlank() && !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from reference.sectors where code = ?)", Boolean.class, r.sectorCode()))) {
            errors.put("sectorCode", "must be a sector from the catalogue");
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from reference.countries where code = ?)", Boolean.class, r.countryCode()))) {
            errors.put("countryCode", "must be a country from the catalogue");
        }
        if (r.ownerUserId() != null && !support.holds(r.ownerUserId(), "update")) {
            errors.put("ownerUserId", "must be someone who can update suppliers");
        }
        if ("full".equals(r.onboarding()) && blank(r.contactEmail()) == null) {
            errors.put("contactEmail", "is required for full onboarding (the questionnaire link is emailed)");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static void check(String v, String regex, String field) {
        if (!v.matches(regex)) {
            throw FieldValidationException.of(field, "isn't a valid value");
        }
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private Supplier supplier(ResultSet rs, String country, Map<UUID, BigDecimal> spend, boolean detail) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<ScoreHistory> history = detail ? jdbc.query("select id, title, reviewed_at, score, score_e, score_s, score_g from supply_chain.questionnaires "
                + "where supplier_id = ? and status = 'reviewed' and deleted_at is null order by reviewed_at", (h, i) -> new ScoreHistory(
                h.getObject(1, UUID.class), h.getString(2), h.getTimestamp(3).toInstant().atZone(SupplySupport.LAGOS).toLocalDate(), h.getBigDecimal(4),
                h.getBigDecimal(5), h.getBigDecimal(6), h.getBigDecimal(7)), id) : List.of();
        java.sql.Timestamp assessed = rs.getTimestamp("last_assessed_at");
        BigDecimal s = spend.get(id);
        return new Supplier(id, rs.getString("name"), rs.getString("tier"), rs.getString("onboarding"), rs.getString("sector_code"),
                rs.getString("sector_name"), rs.getString("country_code"), rs.getString("state_region"), rs.getString("registration_number"),
                rs.getString("contact_name"), rs.getString("contact_email"), rs.getString("contact_phone"), rs.getString("description"),
                rs.getString("status"), rs.getObject("owner_user_id", UUID.class), rs.getString("owner_name"), rs.getBigDecimal("esg_score"),
                rs.getString("risk_level"), assessed == null ? null : assessed.toInstant(), country != null && country.equals(rs.getString("country_code")),
                s == null ? null : s.setScale(2, RoundingMode.HALF_UP), rs.getInt("open_actions"), rs.getInt("open_questionnaires"), history,
                rs.getLong("version"));
    }
}
