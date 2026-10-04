package com.esg.carbon;

import com.esg.carbon.CarbonDtos.InstrumentRequest;
import com.esg.carbon.CarbonDtos.InstrumentResponse;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.OrgScopeService;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Energy attribute instruments — RECs, PPAs, green tariffs (BRD-5.2.2-05) — for market-based Scope 2 (GHG Protocol
 * Scope 2 Guidance). An instrument covers grid electricity of the same organisation in an overlapping period, up to its
 * MWh; the covered kWh are counted at the instrument's factor instead of the grid factor they were calculated with:
 * <pre>avoided (t) = covered kWh × (grid factor of those records − instrument factor) ÷ 1000;  market = location − avoided</pre>
 * Where no residual-mix factor is published (as in Nigeria) the uncovered electricity stays at the grid average, as the
 * Guidance's hierarchy allows (D22).
 */
@Service
public class InstrumentService {

    /** Per instrument: covered kWh and avoided tonnes against the overlapping grid-electricity records. */
    static final String AVOIDED_SQL = "select i.id, i.reporting_period_start as period_start, "
            + "least(i.mwh * 1000, coalesce(sum(r.activity_data), 0)) as covered_kwh, "
            + "case when coalesce(sum(r.activity_data), 0) = 0 then 0 else greatest(0, least(i.mwh * 1000, sum(r.activity_data)) "
            + "* (sum(r.co2e_tonnes) * 1000 / sum(r.activity_data) - i.emission_factor) / 1000) end as avoided "
            + "from carbon.energy_instruments i left join carbon.emission_records r on r.org_id = i.org_id "
            + "and r.scope = 'scope_2' and r.source_activity = 'electricity_grid' and r.deleted_at is null "
            + "and r.reporting_period_start <= i.reporting_period_end and r.reporting_period_end >= i.reporting_period_start "
            + "where i.deleted_at is null group by i.id";

    private static final String SELECT = "select i.*, o.name as org_name, a.covered_kwh, a.avoided from carbon.energy_instruments i "
            + "join auth.organisations o on o.id = i.org_id join (" + AVOIDED_SQL + ") a on a.id = i.id ";

    private final JdbcTemplate jdbc;
    private final OrgScopeService orgScope;
    private final EmissionService emissions;

    public InstrumentService(JdbcTemplate jdbc, OrgScopeService orgScope, EmissionService emissions) {
        this.jdbc = jdbc;
        this.orgScope = orgScope;
        this.emissions = emissions;
    }

    public List<InstrumentResponse> list() {
        StringBuilder where = new StringBuilder("where i.deleted_at is null");
        List<Object> args = new ArrayList<>();
        emissions.appendOrgScope(where, args, "i.org_id");
        return jdbc.query(SELECT + where + " order by i.reporting_period_start desc", (rs, n) -> response(rs), args.toArray());
    }

    public InstrumentResponse get(UUID id) {
        InstrumentResponse r = jdbc.query(SELECT + "where i.id = ? and i.deleted_at is null", (rs, n) -> response(rs), id).stream()
                .findFirst().orElseThrow(() -> ApiException.notFound("Energy instrument"));
        if (!orgScope.canAccess(r.orgId())) {
            throw ApiException.notFound("Energy instrument");
        }
        return r;
    }

    @Transactional
    public InstrumentResponse create(InstrumentRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into carbon.energy_instruments (id, org_id, instrument_type, energy_source, mwh, emission_factor, "
                            + "reporting_period_start, reporting_period_end, certificate_ref, supplier) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, r.orgId(), r.instrumentType(), r.energySource(), r.mwh(), factor(r), Date.valueOf(r.reportingPeriodStart()),
                    Date.valueOf(r.reportingPeriodEnd()), blank(r.certificateRef()), r.supplier().trim());
        } catch (DuplicateKeyException e) {
            throw FieldValidationException.of("certificateRef", "This certificate is already recorded (no double counting)");
        }
        return get(id);
    }

    @Transactional
    public InstrumentResponse update(UUID id, InstrumentRequest r) {
        get(id);
        check(r);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        int updated;
        try {
            updated = jdbc.update("update carbon.energy_instruments set org_id = ?, instrument_type = ?, energy_source = ?, mwh = ?, "
                            + "emission_factor = ?, reporting_period_start = ?, reporting_period_end = ?, certificate_ref = ?, supplier = ?, "
                            + "version = version + 1 where id = ? and version = ? and deleted_at is null",
                    r.orgId(), r.instrumentType(), r.energySource(), r.mwh(), factor(r), Date.valueOf(r.reportingPeriodStart()),
                    Date.valueOf(r.reportingPeriodEnd()), blank(r.certificateRef()), r.supplier().trim(), id, r.version());
        } catch (DuplicateKeyException e) {
            throw FieldValidationException.of("certificateRef", "This certificate is already recorded (no double counting)");
        }
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This instrument was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, Long version) {
        get(id);
        if (jdbc.update("update carbon.energy_instruments set deleted_at = now(), version = version + 1 where id = ? and version = ?",
                id, version) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This instrument was changed by someone else. Reload and try again.");
        }
    }

    private void check(InstrumentRequest r) {
        orgScope.requireAccess(r.orgId());
        if (r.reportingPeriodEnd().isBefore(r.reportingPeriodStart())) {
            throw FieldValidationException.of("reportingPeriodEnd", "must be on or after the start date");
        }
    }

    private static BigDecimal factor(InstrumentRequest r) {
        return r.emissionFactor() == null ? BigDecimal.ZERO : r.emissionFactor();
    }

    private static InstrumentResponse response(ResultSet rs) throws SQLException {
        BigDecimal covered = rs.getBigDecimal("covered_kwh");
        return new InstrumentResponse(rs.getObject("id", UUID.class), rs.getObject("org_id", UUID.class), rs.getString("org_name"),
                rs.getString("instrument_type"), rs.getString("energy_source"), rs.getBigDecimal("mwh"),
                rs.getBigDecimal("emission_factor").stripTrailingZeros(), rs.getDate("reporting_period_start").toLocalDate(),
                rs.getDate("reporting_period_end").toLocalDate(), rs.getString("certificate_ref"), rs.getString("supplier"),
                covered == null ? BigDecimal.ZERO : covered.divide(BigDecimal.valueOf(1000), 6, java.math.RoundingMode.HALF_UP),
                rs.getBigDecimal("avoided").setScale(6, java.math.RoundingMode.HALF_UP), rs.getLong("version"));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
