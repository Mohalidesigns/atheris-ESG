package com.esg.reference;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.reference.ReferenceDtos.CustomFactorRequest;
import com.esg.reference.ReferenceDtos.EmissionFactor;
import com.esg.reference.ReferenceDtos.ResolvedFactor;
import com.esg.security.TenantContext;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emission factors (TRD §8.2 EmissionFactorManager). RLS shows platform factors plus the tenant's own custom ones.
 * Lookup order: tenant custom (country, then region, then global) → platform country → platform region → global.
 * Within a level the factor valid on the date with the latest valid_from (then highest version) wins.
 */
@Service
@Transactional(readOnly = true)
public class EmissionFactorService {

    static final String GLOBAL = "GLOBAL";
    private static final String COLUMNS = "id, region, category, sub_category, factor_value, unit, source, source_year, gwp_set, "
            + "ghg_basis, derivation, factor_version, valid_from, valid_to, verification_status, is_custom, version, "
            + "co2_factor, ch4_factor, n2o_factor, factor_method, spend_currency";

    private final JdbcTemplate jdbc;
    private final EmissionActivityService activities;

    public EmissionFactorService(JdbcTemplate jdbc, EmissionActivityService activities) {
        this.jdbc = jdbc;
        this.activities = activities;
    }

    public List<EmissionFactor> list(String region, String category) {
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from carbon.emission_factors where true");
        List<Object> args = new ArrayList<>();
        if (region != null && !region.isBlank()) {
            sql.append(" and region = ?");
            args.add(region);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" and category = ?");
            args.add(category);
        }
        sql.append(" order by is_custom desc, category, region, valid_from desc, factor_version desc");
        return jdbc.query(sql.toString(), (rs, i) -> map(rs), args.toArray());
    }

    public EmissionFactor get(UUID id) {
        return jdbc.query("select " + COLUMNS + " from carbon.emission_factors where id = ?", (rs, i) -> map(rs), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Emission factor"));
    }

    /** Finds the factor for a category in a country on a date, walking the hierarchy; 404 if nothing applies. */
    public ResolvedFactor resolve(String category, String country, LocalDate date, BigDecimal activityData) {
        String region = country == null ? null
                : jdbc.query("select region from reference.countries where code = ?", (rs, i) -> rs.getString(1), country)
                        .stream().findFirst().orElse(null);
        List<String> places = new ArrayList<>();
        if (country != null) {
            places.add(country);
        }
        if (region != null) {
            places.add(region);
        }
        places.add(GLOBAL);

        List<String> searched = new ArrayList<>();
        for (boolean custom : new boolean[] {true, false}) {
            for (String place : places) {
                String level = custom ? "tenant_custom" : place.equals(GLOBAL) ? "global" : place.equals(country) ? "country" : "region";
                searched.add(level + ":" + place);
                List<EmissionFactor> hits = jdbc.query("select " + COLUMNS + " from carbon.emission_factors "
                        + "where category = ? and region = ? and is_custom = ? and valid_from <= ? "
                        + "and (valid_to is null or valid_to >= ?) order by valid_from desc, factor_version desc limit 1",
                        (rs, i) -> map(rs), category, place, custom, date, date);
                if (!hits.isEmpty()) {
                    EmissionFactor f = hits.get(0);
                    BigDecimal tonnes = activityData == null ? null : EmissionCalculator.co2eTonnes(activityData, f.factorValue());
                    return new ResolvedFactor(f, level, searched, activityData, tonnes);
                }
            }
        }
        throw ApiException.notFound("Emission factor for " + category + (country == null ? "" : " in " + country) + " on " + date);
    }

    @Transactional
    public EmissionFactor createCustom(CustomFactorRequest r) {
        checkDates(r);
        checkActivity(r, null);
        UUID id = jdbc.queryForObject("insert into carbon.emission_factors (region, category, sub_category, factor_value, unit, "
                + "source, valid_from, valid_to, is_custom, tenant_id, verification_status, factor_method, spend_currency) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, true, ?, 'tenant_provided', ?, ?) returning id", UUID.class,
                r.region(), r.category(), blankToNull(r.subCategory()), r.factorValue(), r.effectiveUnit(), r.source().trim(),
                r.validFrom(), r.validTo(), TenantContext.requireTenantId(), r.method(), spendCurrency(r));
        return get(id);
    }

    /**
     * Updates a custom factor. Once a calculation has used it, its value can't change (that would silently alter
     * reported emissions); end it with validTo and add a new factor instead.
     */
    @Transactional
    public EmissionFactor updateCustom(UUID id, CustomFactorRequest r) {
        checkDates(r);
        checkActivity(r, id);
        EmissionFactor current = get(id);
        if (!current.custom()) {
            throw ApiException.forbidden("Platform emission factors are read-only");
        }
        if (r.version() == null) {
            throw FieldValidationException.of("version", "must not be null");
        }
        boolean used = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from carbon.emission_records where emission_factor_id = ?)", Boolean.class, id));
        boolean valueChanged = current.factorValue().compareTo(r.factorValue()) != 0 || !current.unit().equals(r.effectiveUnit())
                || !current.category().equals(r.category()) || !current.region().equals(r.region())
                || !current.factorMethod().equals(r.method()) || !java.util.Objects.equals(current.spendCurrency(), spendCurrency(r));
        if (used && valueChanged) {
            throw ApiException.conflict("ESG-409-FACTOR-IN-USE",
                    "This factor has been used in calculations, so its value, unit, category and region can't change. "
                            + "Set an end date and add a new factor.");
        }
        int updated = jdbc.update("update carbon.emission_factors set region = ?, category = ?, sub_category = ?, factor_value = ?, "
                + "unit = ?, source = ?, valid_from = ?, valid_to = ?, factor_method = ?, spend_currency = ?, version = version + 1, "
                + "updated_at = now() where id = ? and version = ? and is_custom",
                r.region(), r.category(), blankToNull(r.subCategory()), r.factorValue(), r.effectiveUnit(), r.source().trim(),
                r.validFrom(), r.validTo(), r.method(), spendCurrency(r), id, r.version());
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "Someone else changed this factor. Reload and try again.");
        }
        return get(id);
    }

    private static void checkDates(CustomFactorRequest r) {
        if (r.validTo() != null && r.validTo().isBefore(r.validFrom())) {
            throw FieldValidationException.of("validTo", "must be on or after the start date");
        }
        boolean spend = "spend".equals(r.method());
        if (spend && (r.spendCurrency() == null || r.spendCurrency().isBlank())) {
            throw FieldValidationException.of("spendCurrency", "is required for a spend-based factor");
        }
        if (!spend && r.spendCurrency() != null && !r.spendCurrency().isBlank()) {
            throw FieldValidationException.of("spendCurrency", "only applies to spend-based factors");
        }
    }

    /**
     * F11: the region must be an ISO country or a factor region; the category an existing activity (its method and unit
     * must match) or a new one declared with its name and GHG scope, which is then added for the tenant.
     */
    private void checkActivity(CustomFactorRequest r, UUID factorId) {
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        if (!EmissionActivityService.knownRegion(r.region())) {
            errors.put("region", "Choose a country or region from the list");
        }
        boolean spend = "spend".equals(r.method());
        String unit = r.unit() == null ? "" : r.unit().trim();
        String per = unit.startsWith("kgCO2e/") ? unit.substring("kgCO2e/".length()) : "";
        java.util.Optional<ReferenceDtos.EmissionActivity> activity = activities.find(r.category());
        if (spend) {
            if (!unit.isBlank() && r.spendCurrency() != null && !per.equals(r.spendCurrency())) {
                errors.put("unit", "must be kgCO2e/" + r.spendCurrency() + " for a spend-based factor in " + r.spendCurrency());
            }
        } else if (per.isBlank()) {
            errors.put("unit", unit.isBlank() ? "is required" : "must be kgCO2e per unit, e.g. kgCO2e/litre");
        }
        if (activity.isPresent()) {
            ReferenceDtos.EmissionActivity a = activity.get();
            if (!a.factorMethod().equals(r.method())) {
                errors.put("factorMethod", a.label() + " is " + ("spend".equals(a.factorMethod()) ? "spend-based" : "activity-based"));
            } else if (!spend && a.activityUnit() != null && !per.isBlank() && !a.activityUnit().equals(per)) {
                // A tenant's own activity follows its only factor; once others share it, the unit is fixed like a platform one.
                boolean onlyFactor = a.custom() && Boolean.FALSE.equals(jdbc.queryForObject("select exists (select 1 from "
                        + "carbon.emission_factors where tenant_id is not null and category = ? and id is distinct from ?)",
                        Boolean.class, r.category(), factorId));
                if (onlyFactor && errors.isEmpty()) {
                    jdbc.update("update carbon.emission_activities set activity_unit = ? where tenant_id is not null and category = ?",
                            per, r.category());
                } else {
                    errors.putIfAbsent("unit", "must be kgCO2e/" + a.activityUnit() + " for " + a.label());
                }
            }
        } else {
            if (r.activityLabel() == null || r.activityLabel().isBlank()) {
                errors.put("activityLabel", "Name the new activity, or choose an existing one");
            }
            if (r.activityScope() == null || r.activityScope().isBlank()) {
                errors.put("activityScope", "Choose the GHG scope of the new activity");
            } else if ("scope_3".equals(r.activityScope()) && r.activityScope3Category() == null) {
                errors.put("activityScope3Category", "Choose the Scope 3 category");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        if (activity.isEmpty()) {
            activities.createTenantActivity(r.category(), r.activityLabel(), r.activityScope(), r.activityScope3Category(),
                    r.method(), per);
        }
    }

    private static String spendCurrency(CustomFactorRequest r) {
        return "spend".equals(r.method()) ? r.spendCurrency() : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static EmissionFactor map(ResultSet rs) throws SQLException {
        return new EmissionFactor(rs.getObject("id", UUID.class), rs.getString("region"), rs.getString("category"),
                rs.getString("sub_category"), rs.getBigDecimal("factor_value").stripTrailingZeros(), rs.getString("unit"),
                rs.getString("source"), (Integer) rs.getObject("source_year"), rs.getString("gwp_set"), rs.getString("ghg_basis"),
                rs.getString("derivation"), rs.getString("factor_version"), rs.getObject("valid_from", LocalDate.class),
                rs.getObject("valid_to", LocalDate.class), rs.getString("verification_status"), rs.getBoolean("is_custom"),
                rs.getLong("version"), rs.getBigDecimal("co2_factor"), rs.getBigDecimal("ch4_factor"), rs.getBigDecimal("n2o_factor"),
                rs.getString("factor_method"), rs.getString("spend_currency"));
    }
}
