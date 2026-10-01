package com.esg.carbon;

import com.esg.carbon.CarbonDtos.AssetClassTotal;
import com.esg.carbon.CarbonDtos.FinancedRequest;
import com.esg.carbon.CarbonDtos.FinancedResponse;
import com.esg.carbon.CarbonDtos.FinancedSummary;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PCAF financed emissions (BRD-5.2.2-08, TRD §8.2 FinancedEmissionsCalculator; D5 bank pilot). Each position stores
 * its attribution factor and financed emissions; totals carry the outstanding-weighted PCAF data quality score.
 */
@Service
public class FinancedService {

    private final JdbcTemplate jdbc;

    public FinancedService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FinancedResponse> list(Integer year) {
        return jdbc.query("select * from carbon.financed_positions where deleted_at is null and (?::int is null or reporting_year = ?) "
                + "order by reporting_year desc, asset_class, counterparty", (rs, i) -> response(rs), year, year);
    }

    public FinancedResponse get(UUID id) {
        return jdbc.query("select * from carbon.financed_positions where id = ? and deleted_at is null", (rs, i) -> response(rs), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Financed position"));
    }

    @Transactional
    public FinancedResponse create(FinancedRequest r) {
        check(r);
        BigDecimal af = PcafCalculator.attribution(r.outstandingAmount(), r.denominatorValue());
        UUID id = UUID.randomUUID();
        jdbc.update("insert into carbon.financed_positions (id, reporting_year, asset_class, counterparty, sector, outstanding_amount, "
                        + "denominator_value, currency, counterparty_emissions, data_quality_score, emissions_source, attribution_factor, "
                        + "financed_emissions) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, r.reportingYear(), r.assetClass(), r.counterparty().trim(), blank(r.sector()), r.outstandingAmount(),
                r.denominatorValue(), r.currency(), r.counterpartyEmissions(), r.dataQualityScore(), r.emissionsSource().trim(), af,
                PcafCalculator.financed(af, r.counterpartyEmissions()));
        return get(id);
    }

    @Transactional
    public FinancedResponse update(UUID id, FinancedRequest r) {
        get(id);
        check(r);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        BigDecimal af = PcafCalculator.attribution(r.outstandingAmount(), r.denominatorValue());
        int updated = jdbc.update("update carbon.financed_positions set reporting_year = ?, asset_class = ?, counterparty = ?, sector = ?, "
                        + "outstanding_amount = ?, denominator_value = ?, currency = ?, counterparty_emissions = ?, data_quality_score = ?, "
                        + "emissions_source = ?, attribution_factor = ?, financed_emissions = ?, version = version + 1 "
                        + "where id = ? and version = ? and deleted_at is null",
                r.reportingYear(), r.assetClass(), r.counterparty().trim(), blank(r.sector()), r.outstandingAmount(), r.denominatorValue(),
                r.currency(), r.counterpartyEmissions(), r.dataQualityScore(), r.emissionsSource().trim(), af,
                PcafCalculator.financed(af, r.counterpartyEmissions()), id, r.version());
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This position was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, Long version) {
        get(id);
        if (jdbc.update("update carbon.financed_positions set deleted_at = now(), version = version + 1 where id = ? and version = ?",
                id, version) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This position was changed by someone else. Reload and try again.");
        }
    }

    /** Totals by asset class with the outstanding-weighted data quality score (PCAF Part A §5.5). */
    public FinancedSummary summary(int year) {
        List<AssetClassTotal> classes = jdbc.query("select asset_class, count(*), sum(outstanding_amount), sum(financed_emissions), "
                        + "case when sum(outstanding_amount) = 0 then avg(data_quality_score) "
                        + "else sum(outstanding_amount * data_quality_score) / sum(outstanding_amount) end "
                        + "from carbon.financed_positions where deleted_at is null and reporting_year = ? group by asset_class order by asset_class",
                (rs, i) -> new AssetClassTotal(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4).setScale(6, RoundingMode.HALF_UP),
                        rs.getBigDecimal(5).setScale(2, RoundingMode.HALF_UP)), year);
        BigDecimal total = classes.stream().map(AssetClassTotal::financedEmissions).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = classes.stream().map(AssetClassTotal::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weighted = outstanding.signum() == 0 ? null : classes.stream()
                .map(c -> c.outstanding().multiply(c.weightedDataQuality())).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(outstanding, 2, RoundingMode.HALF_UP);
        return new FinancedSummary(year, total, weighted, classes);
    }

    private static void check(FinancedRequest r) {
        if (r.outstandingAmount().compareTo(r.denominatorValue()) > 0) {
            throw FieldValidationException.of("outstandingAmount", "can't exceed the denominator (attribution factor above 1)");
        }
    }

    private static FinancedResponse response(ResultSet rs) throws SQLException {
        return new FinancedResponse(rs.getObject("id", UUID.class), rs.getInt("reporting_year"), rs.getString("asset_class"),
                rs.getString("counterparty"), rs.getString("sector"), rs.getBigDecimal("outstanding_amount"),
                rs.getBigDecimal("denominator_value"), rs.getString("currency"), rs.getBigDecimal("counterparty_emissions"),
                rs.getInt("data_quality_score"), rs.getString("emissions_source"), rs.getBigDecimal("attribution_factor"),
                rs.getBigDecimal("financed_emissions"), rs.getLong("version"));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
