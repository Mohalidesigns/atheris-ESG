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
            + "ghg_basis, derivation, factor_version, valid_from, valid_to, verification_status, is_custom, version";

    private final JdbcTemplate jdbc;

    public EmissionFactorService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        UUID id = jdbc.queryForObject("insert into carbon.emission_factors (region, category, sub_category, factor_value, unit, "
                + "source, valid_from, valid_to, is_custom, tenant_id, verification_status) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, true, ?, 'tenant_provided') returning id", UUID.class,
                r.region(), r.category(), blankToNull(r.subCategory()), r.factorValue(), r.unit().trim(), r.source().trim(),
                r.validFrom(), r.validTo(), TenantContext.requireTenantId());
        return get(id);
    }

    /**
     * Updates a custom factor. Once a calculation has used it, its value can't change (that would silently alter
     * reported emissions); end it with validTo and add a new factor instead.
     */
    @Transactional
    public EmissionFactor updateCustom(UUID id, CustomFactorRequest r) {
        checkDates(r);
        EmissionFactor current = get(id);
        if (!current.custom()) {
            throw ApiException.forbidden("Platform emission factors are read-only");
        }
        if (r.version() == null) {
            throw FieldValidationException.of("version", "must not be null");
        }
        boolean used = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from carbon.emission_records where emission_factor_id = ?)", Boolean.class, id));
        boolean valueChanged = current.factorValue().compareTo(r.factorValue()) != 0 || !current.unit().equals(r.unit().trim())
                || !current.category().equals(r.category()) || !current.region().equals(r.region());
        if (used && valueChanged) {
            throw ApiException.conflict("ESG-409-FACTOR-IN-USE",
                    "This factor has been used in calculations, so its value, unit, category and region can't change. "
                            + "Set an end date and add a new factor.");
        }
        int updated = jdbc.update("update carbon.emission_factors set region = ?, category = ?, sub_category = ?, factor_value = ?, "
                + "unit = ?, source = ?, valid_from = ?, valid_to = ?, version = version + 1, updated_at = now() "
                + "where id = ? and version = ? and is_custom",
                r.region(), r.category(), blankToNull(r.subCategory()), r.factorValue(), r.unit().trim(), r.source().trim(),
                r.validFrom(), r.validTo(), id, r.version());
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "Someone else changed this factor. Reload and try again.");
        }
        return get(id);
    }

    private static void checkDates(CustomFactorRequest r) {
        if (r.validTo() != null && r.validTo().isBefore(r.validFrom())) {
            throw FieldValidationException.of("validTo", "must be on or after the start date");
        }
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
                rs.getLong("version"));
    }
}
