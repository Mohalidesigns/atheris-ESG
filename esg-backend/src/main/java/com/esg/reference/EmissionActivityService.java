package com.esg.reference;

import com.esg.exception.FieldValidationException;
import com.esg.reference.ReferenceDtos.EmissionActivity;
import com.esg.reference.ReferenceDtos.FactorRegion;
import com.esg.security.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Emission activities (WP-52b F5/F11): every factor category with its GHG scope, Scope 3 category, method and the unit
 * its activity data is measured in. The calculator offers only the activities of the chosen scope; custom factors must
 * use a known activity or declare a new one with its scope, so a typo can't create a category that never matches.
 */
@Service
public class EmissionActivityService {

    // no-mock-gate: config — emission-factor regions (REGION_REGEX) with display names
    static final Map<String, String> REGIONS = Map.of("GLOBAL", "Global", "WEST_AFRICA", "West Africa", "EAST_AFRICA", "East Africa",
            "SOUTHERN_AFRICA", "Southern Africa", "NORTH_AFRICA", "North Africa", "CENTRAL_AFRICA", "Central Africa");
    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    private final JdbcTemplate jdbc;

    public EmissionActivityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Platform activities and the tenant's own (RLS), platform first. */
    public List<EmissionActivity> list() {
        return jdbc.query("select category, label, scope, scope3_category, factor_method, activity_unit, source, tenant_id is not null "
                        + "from carbon.emission_activities order by tenant_id nulls first, sort_order, label",
                (rs, i) -> new EmissionActivity(rs.getString(1), rs.getString(2), rs.getString(3), (Integer) rs.getObject(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8)));
    }

    /** The tenant's own activity wins over a platform one with the same category. */
    public Optional<EmissionActivity> find(String category) {
        return list().stream().filter(a -> a.category().equals(category))
                .max(Comparator.comparing(EmissionActivity::custom));
    }

    /** ISO 3166 countries (English names) and the factor regions. */
    public List<FactorRegion> regions() {
        List<FactorRegion> out = new ArrayList<>();
        REGIONS.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .forEach(e -> out.add(new FactorRegion(e.getKey(), e.getValue(), "region")));
        Arrays.stream(Locale.getISOCountries())
                .map(c -> new FactorRegion(c, Locale.of("", c).getDisplayCountry(Locale.ENGLISH), "country"))
                .sorted(Comparator.comparing(FactorRegion::name))
                .forEach(out::add);
        return out;
    }

    public static boolean knownRegion(String code) {
        return code != null && (REGIONS.containsKey(code) || COUNTRIES.contains(code));
    }

    /** Adds a tenant activity for a new category (F11). */
    public EmissionActivity createTenantActivity(String category, String label, String scope, Integer scope3Category,
                                                 String method, String activityUnit) {
        jdbc.update("insert into carbon.emission_activities (tenant_id, category, label, scope, scope3_category, factor_method, "
                        + "activity_unit, source) values (?, ?, ?, ?, ?, ?, ?, 'Added with a custom emission factor')",
                TenantContext.requireTenantId(), category, label.trim(), scope, "scope_3".equals(scope) ? scope3Category : null,
                method, "spend".equals(method) ? null : activityUnit);
        return find(category).orElseThrow(() -> FieldValidationException.of("category", "could not be added"));
    }
}
