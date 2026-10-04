package com.esg.service;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.model.dto.TenancyDtos.SettingsRequest;
import com.esg.model.dto.TenancyDtos.SettingsResponse;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant settings: base currency, fiscal year start, reporting time zone, default frameworks (WP-14). */
@Service
public class TenantSettingsService {

    private final JdbcTemplate jdbc;

    public TenantSettingsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        List<SettingsResponse> rows = jdbc.query("select t.name, s.base_currency, s.fiscal_year_start_month, "
                + "s.reporting_timezone, s.default_framework_codes, s.version, s.gwp_set, s.provisional_factor_policy from auth.tenant_settings s "
                + "join auth.tenants t on t.id = s.tenant_id", (rs, i) -> {
                    Array codes = rs.getArray(5);
                    return new SettingsResponse(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4),
                            codes == null ? List.of() : Arrays.asList((String[]) codes.getArray()), rs.getLong(6), rs.getString(7),
                            rs.getString(8));
                });
        if (rows.isEmpty()) {
            throw ApiException.notFound("Tenant settings");
        }
        return rows.get(0);
    }

    /** IANA time-zone ids (Region/City), sorted: what Settings › Reporting time zone may hold (F10). */
    public static List<String> timeZones() {
        return java.time.ZoneId.getAvailableZoneIds().stream()
                .filter(z -> z.contains("/") && !z.startsWith("Etc/") && !z.startsWith("SystemV/") && Character.isUpperCase(z.charAt(0)))
                .sorted().toList();
    }

    @Transactional
    public SettingsResponse update(SettingsRequest r) {
        if (!timeZones().contains(r.reportingTimezone().trim())) {
            throw new FieldValidationException("ESG-400-TIMEZONE",
                    Map.of("reportingTimezone", "Choose a time zone from the list, e.g. Africa/Lagos"));
        }
        List<String> codes = r.defaultFrameworkCodes().stream().map(String::trim).distinct().toList();
        if (!codes.isEmpty()) {
            List<String> known = jdbc.query(con -> {
                var ps = con.prepareStatement("select code from compliance.regulatory_frameworks where code = any(?) and is_active");
                ps.setArray(1, con.createArrayOf("varchar", codes.toArray()));
                return ps;
            }, (rs, i) -> rs.getString(1));
            List<String> unknown = codes.stream().filter(c -> !known.contains(c)).toList();
            if (!unknown.isEmpty()) {
                throw FieldValidationException.of("defaultFrameworkCodes", "Unknown framework(s): " + String.join(", ", unknown));
            }
        }
        int updated = jdbc.update(con -> {
            var ps = con.prepareStatement("update auth.tenant_settings set base_currency = ?, fiscal_year_start_month = ?, "
                    + "reporting_timezone = ?, default_framework_codes = ?, gwp_set = coalesce(?, gwp_set), "
                    + "provisional_factor_policy = coalesce(?, provisional_factor_policy), version = version + 1 where version = ?");
            ps.setString(1, r.baseCurrency());
            ps.setInt(2, r.fiscalYearStartMonth());
            ps.setString(3, r.reportingTimezone().trim());
            ps.setArray(4, con.createArrayOf("varchar", codes.toArray()));
            ps.setString(5, r.gwpSet() == null || r.gwpSet().isBlank() ? null : r.gwpSet());
            ps.setString(6, r.provisionalFactorPolicy() == null || r.provisionalFactorPolicy().isBlank() ? null : r.provisionalFactorPolicy());
            ps.setLong(7, r.version());
            return ps;
        });
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "Settings were changed by someone else. Reload and try again.");
        }
        return get();
    }
}
