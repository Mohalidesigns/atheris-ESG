package com.esg.service;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.model.dto.TenancyDtos.PeriodRequest;
import com.esg.model.dto.TenancyDtos.PeriodResponse;
import com.esg.security.TenantContext;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporting periods (WP-14): monthly/quarterly/annual, open or locked. A locked period rejects data writes;
 * {@link #requireOpen} gives the friendly 409 and the database trigger auth.assert_periods_open is the backstop.
 */
@Service
public class ReportingPeriodService {

    private static final RowMapper<PeriodResponse> MAPPER = (rs, i) -> {
        Timestamp locked = rs.getTimestamp("locked_at");
        return new PeriodResponse(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("period_type"),
                rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(), rs.getString("status"),
                locked == null ? null : locked.toInstant(), rs.getLong("version"));
    };
    private static final String COLUMNS = "id, name, period_type, start_date, end_date, status, locked_at, version";

    private final JdbcTemplate jdbc;

    public ReportingPeriodService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PeriodResponse> list() {
        return jdbc.query("select " + COLUMNS + " from auth.reporting_periods order by start_date desc, period_type", MAPPER);
    }

    @Transactional
    public PeriodResponse create(PeriodRequest r) {
        validateDates(r, null);
        UUID id = jdbc.queryForObject("insert into auth.reporting_periods (name, period_type, start_date, end_date) "
                + "values (?, ?, ?, ?) returning id", UUID.class, r.name().trim(), r.periodType(),
                Date.valueOf(r.startDate()), Date.valueOf(r.endDate()));
        return find(id);
    }

    @Transactional
    public PeriodResponse update(UUID id, PeriodRequest r) {
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        PeriodResponse current = find(id);
        if ("locked".equals(current.status())) {
            throw ApiException.conflict("ESG-409-LOCKED", "Unlock the period before editing it");
        }
        validateDates(r, id);
        int updated = jdbc.update("update auth.reporting_periods set name = ?, period_type = ?, start_date = ?, end_date = ?, "
                        + "version = version + 1 where id = ? and version = ?", r.name().trim(), r.periodType(),
                Date.valueOf(r.startDate()), Date.valueOf(r.endDate()), id, r.version());
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This period was changed by someone else. Reload and try again.");
        }
        return find(id);
    }

    @Transactional
    public PeriodResponse lock(UUID id) {
        find(id);
        jdbc.update("update auth.reporting_periods set status = 'locked', locked_at = now(), locked_by = ?, "
                + "version = version + 1 where id = ? and status = 'open'", UUID.fromString(TenantContext.getUserId()), id);
        return find(id);
    }

    @Transactional
    public PeriodResponse unlock(UUID id) {
        find(id);
        jdbc.update("update auth.reporting_periods set status = 'open', locked_at = null, locked_by = null, "
                + "version = version + 1 where id = ? and status = 'locked'", id);
        return find(id);
    }

    /** 409 if any locked period overlaps [start, end]. */
    public void requireOpen(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return;
        }
        List<String> locked = jdbc.queryForList("select name from auth.reporting_periods where status = 'locked' "
                + "and start_date <= ? and end_date >= ? order by start_date limit 1", String.class,
                Date.valueOf(end), Date.valueOf(start));
        if (!locked.isEmpty()) {
            throw ApiException.conflict("ESG-409-LOCKED", "Reporting period \"" + locked.get(0)
                    + "\" is locked. Ask a director to unlock it before changing its data.");
        }
    }

    private void validateDates(PeriodRequest r, UUID excludeId) {
        if (r.endDate().isBefore(r.startDate())) {
            throw FieldValidationException.of("endDate", "must be on or after the start date");
        }
        Long overlaps = jdbc.queryForObject("select count(*) from auth.reporting_periods where period_type = ? "
                        + "and start_date <= ? and end_date >= ? and (?::uuid is null or id <> ?::uuid)", Long.class,
                r.periodType(), Date.valueOf(r.endDate()), Date.valueOf(r.startDate()), excludeId, excludeId);
        if (overlaps != null && overlaps > 0) {
            throw FieldValidationException.of("startDate", "Overlaps another " + r.periodType() + " period");
        }
    }

    private PeriodResponse find(UUID id) {
        List<PeriodResponse> rows = jdbc.query("select " + COLUMNS + " from auth.reporting_periods where id = ?", MAPPER, id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Reporting period");
        }
        return rows.get(0);
    }
}
