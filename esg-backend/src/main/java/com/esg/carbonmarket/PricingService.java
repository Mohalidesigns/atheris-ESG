package com.esg.carbonmarket;

import com.esg.carbonmarket.CarbonMarketDtos.Exposure;
import com.esg.carbonmarket.CarbonMarketDtos.ExposureRow;
import com.esg.carbonmarket.CarbonMarketDtos.Offsetting;
import com.esg.carbonmarket.CarbonMarketDtos.Price;
import com.esg.carbonmarket.CarbonMarketDtos.PriceRequest;
import com.esg.carbonmarket.CarbonMarketDtos.Settings;
import com.esg.carbonmarket.CarbonMarketDtos.SettingsRequest;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal carbon pricing and offset monitoring (WP-41, D36 (6)–(7); TRD InternalCarbonPricing, BRD offset-to-reduction
 * ratio). Gross emissions come from Carbon (location-based Scope 2); retired credits are shown beside them, never netted
 * out of the inventory.
 */
@Service
public class PricingService {

    private final JdbcTemplate jdbc;

    public PricingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- settings

    public Settings settings() {
        return jdbc.query("select offset_ceiling_pct, version from carbon_market.settings", (rs, i) -> new Settings(rs.getBigDecimal(1), rs.getLong(2)))
                .stream().findFirst().orElse(new Settings(new BigDecimal("10.0"), 0));
    }

    @Transactional
    public Settings updateSettings(SettingsRequest r) {
        long version = r.version() == null ? 0 : r.version();
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from carbon_market.settings)", Boolean.class));
        int n = !exists && version == 0
                ? jdbc.update("insert into carbon_market.settings (offset_ceiling_pct) values (?)", r.offsetCeilingPct())
                : jdbc.update("update carbon_market.settings set offset_ceiling_pct = ?, version = version + 1 where version = ?", r.offsetCeilingPct(), version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "The settings were changed by someone else. Reload and try again.");
        }
        return settings();
    }

    // ---------------------------------------------------------------- internal prices

    public List<Price> prices() {
        return jdbc.query("select * from carbon_market.internal_prices where deleted_at is null order by price_type, effective_from desc", (rs, i) -> price(rs));
    }

    public Price price(UUID id) {
        return jdbc.query("select * from carbon_market.internal_prices where id = ? and deleted_at is null", (rs, i) -> price(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Internal carbon price"));
    }

    @Transactional
    public Price createPrice(PriceRequest r) {
        check(r, null);
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into carbon_market.internal_prices (id, price_type, price, currency, effective_from, scopes, note) "
                    + "values (?, ?, ?, ?, ?, ?, ?)");
            ps.setObject(1, id);
            ps.setString(2, r.priceType());
            ps.setBigDecimal(3, r.price());
            ps.setString(4, r.currency());
            ps.setDate(5, Date.valueOf(r.effectiveFrom()));
            ps.setArray(6, con.createArrayOf("varchar", r.scopes().stream().distinct().sorted().toArray()));
            ps.setString(7, CreditService.blank(r.note()));
            return ps;
        });
        return price(id);
    }

    @Transactional
    public Price updatePrice(UUID id, PriceRequest r) {
        price(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r, id);
        int n = jdbc.update(con -> {
            var ps = con.prepareStatement("update carbon_market.internal_prices set price_type = ?, price = ?, currency = ?, effective_from = ?, scopes = ?, "
                    + "note = ?, version = version + 1 where id = ? and version = ? and deleted_at is null");
            ps.setString(1, r.priceType());
            ps.setBigDecimal(2, r.price());
            ps.setString(3, r.currency());
            ps.setDate(4, Date.valueOf(r.effectiveFrom()));
            ps.setArray(5, con.createArrayOf("varchar", r.scopes().stream().distinct().sorted().toArray()));
            ps.setString(6, CreditService.blank(r.note()));
            ps.setObject(7, id);
            ps.setLong(8, r.version());
            return ps;
        });
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This price was changed by someone else. Reload and try again.");
        }
        return price(id);
    }

    @Transactional
    public void deletePrice(UUID id) {
        price(id);
        jdbc.update("update carbon_market.internal_prices set deleted_at = now(), version = version + 1 where id = ?", id);
    }

    private void check(PriceRequest r, UUID id) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from carbon_market.internal_prices where price_type = ? and effective_from = ? "
                + "and deleted_at is null and id is distinct from ?)", Boolean.class, r.priceType(), Date.valueOf(r.effectiveFrom()), id))) {
            throw FieldValidationException.of("effectiveFrom", "a " + r.priceType() + " price already starts on this date");
        }
    }

    private static Price price(ResultSet rs) throws SQLException {
        Array a = rs.getArray("scopes");
        List<String> scopes = a == null ? List.of() : Arrays.stream((Object[]) a.getArray()).map(String::valueOf).toList();
        return new Price(rs.getObject("id", UUID.class), rs.getString("price_type"), rs.getBigDecimal("price"), rs.getString("currency"),
                rs.getObject("effective_from", LocalDate.class), scopes, rs.getString("note"), rs.getLong("version"));
    }

    /** The price in force at the year end × each organisation's emissions in the scopes it covers. */
    public Exposure exposure(int year, String priceType) {
        if (!"shadow".equals(priceType) && !"fee".equals(priceType)) {
            throw FieldValidationException.of("priceType", "must be shadow or fee");
        }
        List<Price> inForce = jdbc.query("select * from carbon_market.internal_prices where deleted_at is null and price_type = ? and effective_from <= ? "
                + "order by effective_from desc limit 1", (rs, i) -> price(rs), priceType, Date.valueOf(LocalDate.of(year, 12, 31)));
        if (inForce.isEmpty()) {
            return new Exposure(year, priceType, null, null, null, List.of(), "No " + priceType + " price was in force at the end of " + year + ".");
        }
        Price p = inForce.get(0);
        List<ExposureRow> rows = new ArrayList<>();
        jdbc.query("select o.id, o.name, coalesce(sum(e.co2e_tonnes), 0) from carbon.emission_records e join auth.organisations o on o.id = e.org_id "
                + "where e.deleted_at is null and extract(year from e.reporting_period_end) = ? and e.scope = any(?) group by o.id, o.name order by 3 desc",
                rs -> {
                    BigDecimal t = rs.getBigDecimal(3).setScale(3, RoundingMode.HALF_UP);
                    rows.add(new ExposureRow(rs.getObject(1, UUID.class), rs.getString(2), t, t.multiply(p.price()).setScale(2, RoundingMode.HALF_UP)));
                }, year, p.scopes().toArray(String[]::new));
        BigDecimal tonnes = rows.stream().map(ExposureRow::tonnes).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = rows.stream().map(ExposureRow::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Exposure(year, priceType, p, tonnes, cost, rows, rows.isEmpty() ? "No emissions recorded for " + year + " in the scopes covered." : null);
    }

    // ---------------------------------------------------------------- offsetting

    public Offsetting offsetting(int year) {
        Map<String, BigDecimal> gross = new HashMap<>();
        jdbc.query("select scope, coalesce(sum(co2e_tonnes), 0) from carbon.emission_records where deleted_at is null and extract(year from reporting_period_end) = ? "
                + "group by scope", rs -> {
                    gross.put(rs.getString(1), rs.getBigDecimal(2).setScale(3, RoundingMode.HALF_UP));
                }, year);
        Map<String, BigDecimal> retired = new HashMap<>();
        jdbc.query("select purpose, sum(quantity) from carbon_market.retirements where status = 'retired' and claimed_year = ? group by purpose", rs -> {
            retired.put(rs.getString(1), rs.getBigDecimal(2));
        }, year);
        BigDecimal zero = BigDecimal.ZERO.setScale(3);
        BigDecimal s1 = gross.getOrDefault("scope_1", zero);
        BigDecimal s2 = gross.getOrDefault("scope_2", zero);
        BigDecimal c1 = retired.getOrDefault("scope_1", BigDecimal.ZERO);
        BigDecimal c2 = retired.getOrDefault("scope_2", BigDecimal.ZERO);
        BigDecimal gross12 = s1.add(s2);
        BigDecimal credits12 = c1.add(c2);
        // Baseline: the earliest absolute Scope 1+2 target's base value, else last year's Scope 1+2.
        List<Map<String, Object>> target = jdbc.queryForList("select base_value, base_year, target_name from carbon.reduction_targets where deleted_at is null "
                + "and scope = 'scope_1_2' and coalesce(target_type, 'absolute') = 'absolute' and base_year < ? order by created_at limit 1", year);
        BigDecimal baseline;
        String basis;
        if (!target.isEmpty()) {
            baseline = (BigDecimal) target.get(0).get("base_value");
            basis = "base year " + target.get(0).get("base_year") + " of the target \"" + target.get(0).get("target_name") + "\"";
        } else {
            baseline = jdbc.queryForObject("select sum(co2e_tonnes) from carbon.emission_records where deleted_at is null and scope in ('scope_1', 'scope_2') "
                    + "and extract(year from reporting_period_end) = ?", BigDecimal.class, year - 1);
            basis = baseline == null ? "no target and no " + (year - 1) + " emissions to compare with" : (year - 1) + " Scope 1+2 emissions (no target set)";
        }
        BigDecimal reductions = baseline == null ? null : baseline.subtract(gross12).setScale(3, RoundingMode.HALF_UP);
        BigDecimal share = CarbonMarketMath.share(credits12, gross12);
        BigDecimal ratio = CarbonMarketMath.offsetToReduction(credits12, reductions);
        BigDecimal ceiling = settings().offsetCeilingPct();
        boolean over = CarbonMarketMath.overReliance(credits12, reductions, share, ceiling);
        String message = credits12.signum() == 0 ? "No credits retired against " + year + " Scope 1 or 2."
                : over ? (reductions == null || reductions.signum() <= 0 ? "Credits are being used while Scope 1+2 emissions have not fallen."
                    : credits12.compareTo(reductions) > 0 ? "More tonnes are offset than were actually reduced."
                    : "Credits cover " + share + "% of gross Scope 1+2, above the " + ceiling + "% ceiling.")
                : "Credits stay below real reductions and the " + ceiling + "% ceiling.";
        return new Offsetting(year, s1, s2, gross.getOrDefault("scope_3", zero), c1, c2, retired.getOrDefault("scope_3", BigDecimal.ZERO),
                retired.getOrDefault("voluntary", BigDecimal.ZERO), CarbonMarketMath.net(gross12, credits12), share, baseline, basis, reductions, ratio, ceiling,
                over, message);
    }
}
