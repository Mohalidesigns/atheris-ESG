package com.esg.carbon;

import com.esg.carbon.CarbonDtos.FlaringRequest;
import com.esg.carbon.CarbonDtos.FlaringResponse;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.OrgScopeService;
import com.esg.service.ReportingPeriodService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gas flaring (TRD §8.2 GasFlaringCalculator, BRD-5.2.2-04, journey 7). Each record keeps its calculation and drives
 * one Scope 1 emission record (source "gas_flaring"), so flaring counts in every total exactly once.
 */
@Service
public class FlaringService {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.5");
    private static final String SELECT = "select g.*, o.name as facility_name, "
            + "(select r.id from carbon.emission_records r where r.flaring_record_id = g.id and r.deleted_at is null limit 1) as record_id "
            + "from carbon.gas_flaring_records g join auth.organisations o on o.id = g.facility_id ";

    private final JdbcTemplate jdbc;
    private final OrgScopeService orgScope;
    private final ReportingPeriodService periods;
    private final EmissionService emissions;
    private final SatelliteFlareVerifier satellite;
    private final ObjectMapper json;

    public FlaringService(JdbcTemplate jdbc, OrgScopeService orgScope, ReportingPeriodService periods, EmissionService emissions,
                          SatelliteFlareVerifier satellite, ObjectMapper json) {
        this.jdbc = jdbc;
        this.orgScope = orgScope;
        this.periods = periods;
        this.emissions = emissions;
        this.satellite = satellite;
        this.json = json;
    }

    public List<FlaringResponse> list(Integer year) {
        StringBuilder where = new StringBuilder("where g.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (year != null) {
            where.append(" and extract(year from g.reporting_period_start) = ?");
            args.add(year);
        }
        emissions.appendOrgScope(where, args, "g.facility_id");
        return jdbc.query(SELECT + where + " order by g.reporting_period_start desc, o.name", (rs, i) -> response(rs), args.toArray());
    }

    public FlaringResponse get(UUID id) {
        FlaringResponse r = jdbc.query(SELECT + "where g.id = ? and g.deleted_at is null", (rs, i) -> response(rs), id).stream()
                .findFirst().orElseThrow(() -> ApiException.notFound("Flaring record"));
        if (!orgScope.canAccess(r.facilityId())) {
            throw ApiException.notFound("Flaring record");
        }
        return r;
    }

    @Transactional
    public FlaringResponse create(FlaringRequest r) {
        Calc c = calc(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into carbon.gas_flaring_records (id, facility_id, volume_mcf, gas_composition, co2e_tonnes, co2_tonnes, "
                        + "ch4_tonnes, gwp_set, combustion_efficiency, flaring_reason, reporting_date, reporting_period_start, "
                        + "reporting_period_end, notes) values (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, r.facilityId(), r.volumeMcf(), write(c.composition()), c.result().co2eTonnes(), c.result().co2Tonnes(),
                c.result().ch4Tonnes(), c.gwp().set(), c.efficiency(), r.flaringReason(), Date.valueOf(r.reportingPeriodEnd()),
                Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), blank(r.notes()));
        upsertRecord(id, r, c);
        return get(id);
    }

    @Transactional
    public FlaringResponse update(UUID id, FlaringRequest r) {
        FlaringResponse current = get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        periods.requireOpen(current.reportingPeriodStart(), current.reportingPeriodEnd());
        Calc c = calc(r);
        int updated = jdbc.update("update carbon.gas_flaring_records set facility_id = ?, volume_mcf = ?, gas_composition = ?::jsonb, "
                        + "co2e_tonnes = ?, co2_tonnes = ?, ch4_tonnes = ?, gwp_set = ?, combustion_efficiency = ?, flaring_reason = ?, "
                        + "reporting_date = ?, reporting_period_start = ?, reporting_period_end = ?, notes = ?, version = version + 1 "
                        + "where id = ? and version = ? and deleted_at is null",
                r.facilityId(), r.volumeMcf(), write(c.composition()), c.result().co2eTonnes(), c.result().co2Tonnes(),
                c.result().ch4Tonnes(), c.gwp().set(), c.efficiency(), r.flaringReason(), Date.valueOf(r.reportingPeriodEnd()),
                Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), blank(r.notes()), id, r.version());
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This flaring record was changed by someone else. Reload and try again.");
        }
        upsertRecord(id, r, c);
        return get(id);
    }

    @Transactional
    public void delete(UUID id, Long version) {
        FlaringResponse current = get(id);
        periods.requireOpen(current.reportingPeriodStart(), current.reportingPeriodEnd());
        int updated = jdbc.update("update carbon.gas_flaring_records set deleted_at = now(), version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", id, version);
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This flaring record was changed by someone else. Reload and try again.");
        }
        jdbc.update("update carbon.emission_records set deleted_at = now(), version = version + 1 "
                + "where flaring_record_id = ? and deleted_at is null", id);
    }

    /** Asks the satellite adapter (the sandbox says plainly that it isn't connected); the result is kept as evidence. */
    @Transactional
    public FlaringResponse satelliteCheck(UUID id) {
        FlaringResponse current = get(id);
        Map<String, Object> result = satellite.check(current.facilityName(), emissions.country(current.facilityId()),
                current.reportingPeriodStart(), current.reportingPeriodEnd());
        jdbc.update("update carbon.gas_flaring_records set satellite_check = ?::jsonb where id = ?", write(result), id);
        return get(id);
    }

    private record Calc(Map<String, BigDecimal> composition, BigDecimal efficiency, EmissionEngine.Gwp gwp,
                        FlaringCalculator.Result result) {}

    private Calc calc(FlaringRequest r) {
        orgScope.requireAccess(r.facilityId());
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.reportingPeriodEnd().isBefore(r.reportingPeriodStart())) {
            errors.put("reportingPeriodEnd", "must be on or after the start date");
        }
        Map<String, BigDecimal> composition = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : r.gasComposition().entrySet()) {
            if (!FlaringCalculator.CARBON_ATOMS.containsKey(e.getKey())) {
                errors.put("gasComposition", "unknown component '" + e.getKey() + "'; use " + String.join(", ", FlaringCalculator.CARBON_ATOMS.keySet()));
                continue;
            }
            if (e.getValue() == null || e.getValue().signum() < 0 || e.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                errors.put("gasComposition." + e.getKey(), "must be between 0 and 100");
                continue;
            }
            composition.put(e.getKey(), e.getValue());
            total = total.add(e.getValue());
        }
        if (errors.isEmpty() && total.subtract(BigDecimal.valueOf(100)).abs().compareTo(TOLERANCE) > 0) {
            errors.put("gasComposition", "mole % must add up to 100 (now " + total.stripTrailingZeros().toPlainString() + ")");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        periods.requireOpen(r.reportingPeriodStart(), r.reportingPeriodEnd());
        BigDecimal efficiency = r.combustionEfficiency() == null ? FlaringCalculator.DEFAULT_EFFICIENCY : r.combustionEfficiency();
        EmissionEngine.Gwp gwp = emissions.gwp();
        return new Calc(composition, efficiency, gwp, FlaringCalculator.calculate(r.volumeMcf(), composition, efficiency, gwp.ch4()));
    }

    /** The flaring record's Scope 1 emission record (one per flaring record). */
    private void upsertRecord(UUID flaringId, FlaringRequest r, Calc c) {
        int updated = jdbc.update("update carbon.emission_records set org_id = ?, activity_data = ?, co2e_tonnes = ?, co2_tonnes = ?, "
                        + "ch4_tonnes = ?, gwp_set = ?, reporting_period_start = ?, reporting_period_end = ?, calculated_at = now(), "
                        + "version = version + 1 where flaring_record_id = ? and deleted_at is null",
                r.facilityId(), r.volumeMcf(), c.result().co2eTonnes(), c.result().co2Tonnes(), c.result().ch4Tonnes(), c.gwp().set(),
                Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), flaringId);
        if (updated == 0) {
            jdbc.update("insert into carbon.emission_records (org_id, scope, source_activity, activity_data, activity_unit, co2e_tonnes, "
                            + "co2_tonnes, ch4_tonnes, gwp_set, reporting_period_start, reporting_period_end, calculation_method, "
                            + "record_source, flaring_record_id, calculated_at) values (?, 'scope_1', 'gas_flaring', ?, 'Mcf', ?, ?, ?, ?, ?, ?, "
                            + "'flaring_composition', 'flaring', ?, now())",
                    r.facilityId(), r.volumeMcf(), c.result().co2eTonnes(), c.result().co2Tonnes(), c.result().ch4Tonnes(),
                    c.gwp().set(), Date.valueOf(r.reportingPeriodStart()), Date.valueOf(r.reportingPeriodEnd()), flaringId);
        }
    }

    private FlaringResponse response(ResultSet rs) throws SQLException {
        return new FlaringResponse(rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getString("facility_name"),
                rs.getBigDecimal("volume_mcf"), readMap(rs.getString("gas_composition"), new TypeReference<>() {}),
                rs.getBigDecimal("combustion_efficiency"), rs.getString("flaring_reason"),
                rs.getDate("reporting_period_start").toLocalDate(), rs.getDate("reporting_period_end").toLocalDate(),
                rs.getBigDecimal("co2_tonnes"), rs.getBigDecimal("ch4_tonnes"), rs.getBigDecimal("co2e_tonnes"), rs.getString("gwp_set"),
                rs.getBoolean("satellite_verified"), readMap(rs.getString("satellite_check"), new TypeReference<>() {}),
                rs.getString("notes"), rs.getObject("record_id", UUID.class), rs.getLong("version"));
    }

    private <T> T readMap(String value, TypeReference<T> type) {
        if (value == null) {
            return null;
        }
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
