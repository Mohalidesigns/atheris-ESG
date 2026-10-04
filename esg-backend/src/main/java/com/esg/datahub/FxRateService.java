package com.esg.datahub;

import com.esg.datahub.DataHubDtos.FxRateRequest;
import com.esg.datahub.DataHubDtos.FxRateResponse;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant exchange rates (BRD-5.2.1-06). No rates are invented or fetched: each one carries the source the tenant
 * entered (an FX provider adapter can feed this table later, D21). Conversion uses the latest rate dated on or before
 * the reporting period end.
 */
@Service
public class FxRateService {

    private final JdbcTemplate jdbc;

    public FxRateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Rate(BigDecimal rate, LocalDate rateDate, String source) {}

    public List<FxRateResponse> list(String fromCurrency, String toCurrency) {
        return jdbc.query("select * from data_hub.fx_rates where (?::text is null or from_currency = ?) "
                        + "and (?::text is null or to_currency = ?) order by rate_date desc, from_currency limit 500",
                (rs, i) -> response(rs), blank(fromCurrency), blank(fromCurrency), blank(toCurrency), blank(toCurrency));
    }

    public FxRateResponse get(UUID id) {
        return jdbc.query("select * from data_hub.fx_rates where id = ?", (rs, i) -> response(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Exchange rate"));
    }

    @Transactional
    public FxRateResponse create(FxRateRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into data_hub.fx_rates (id, from_currency, to_currency, rate, rate_date, source) values (?, ?, ?, ?, ?, ?)",
                    id, r.fromCurrency(), r.toCurrency(), r.rate(), Date.valueOf(r.rateDate()), r.source().trim());
        } catch (DuplicateKeyException e) {
            throw FieldValidationException.of("rateDate", "A " + r.fromCurrency() + "→" + r.toCurrency() + " rate for this date already exists");
        }
        return get(id);
    }

    @Transactional
    public FxRateResponse update(UUID id, FxRateRequest r) {
        check(r);
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        int updated;
        try {
            updated = jdbc.update("update data_hub.fx_rates set from_currency = ?, to_currency = ?, rate = ?, rate_date = ?, "
                            + "source = ?, version = version + 1 where id = ? and version = ?", r.fromCurrency(), r.toCurrency(),
                    r.rate(), Date.valueOf(r.rateDate()), r.source().trim(), id, r.version());
        } catch (DuplicateKeyException e) {
            throw FieldValidationException.of("rateDate", "A " + r.fromCurrency() + "→" + r.toCurrency() + " rate for this date already exists");
        }
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This exchange rate was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        jdbc.update("delete from data_hub.fx_rates where id = ?", id);
    }

    /** Latest rate from → to dated on or before {@code onOrBefore}. */
    public Optional<Rate> resolve(String from, String to, LocalDate onOrBefore) {
        return jdbc.query("select rate, rate_date, source from data_hub.fx_rates where from_currency = ? and to_currency = ? "
                        + "and rate_date <= ? order by rate_date desc limit 1",
                (rs, i) -> new Rate(rs.getBigDecimal(1), rs.getDate(2).toLocalDate(), rs.getString(3)),
                from, to, Date.valueOf(onOrBefore)).stream().findFirst();
    }

    private static void check(FxRateRequest r) {
        if (r.fromCurrency().equals(r.toCurrency())) {
            throw FieldValidationException.of("toCurrency", "must differ from the source currency");
        }
    }

    private static FxRateResponse response(ResultSet rs) throws SQLException {
        return new FxRateResponse(rs.getObject("id", UUID.class), rs.getString("from_currency"), rs.getString("to_currency"),
                rs.getBigDecimal("rate"), rs.getDate("rate_date").toLocalDate(), rs.getString("source"),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
