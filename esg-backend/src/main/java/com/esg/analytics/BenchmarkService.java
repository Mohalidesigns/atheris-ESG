package com.esg.analytics;

import com.esg.analytics.AnalyticsDtos.BenchmarkDataset;
import com.esg.analytics.AnalyticsDtos.BenchmarkDatasetRequest;
import com.esg.analytics.AnalyticsDtos.BenchmarkValue;
import com.esg.analytics.AnalyticsDtos.BenchmarkValueRequest;
import com.esg.analytics.AnalyticsDtos.Comparison;
import com.esg.analytics.AnalyticsDtos.InternalBenchmark;
import com.esg.analytics.AnalyticsDtos.InternalRow;
import com.esg.analytics.AnalyticsDtos.Placement;
import com.esg.analytics.AnalyticsSupport.MetricInfo;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Benchmarks (WP-35, D34 (5); TRD BenchmarkingEngine). No benchmark figures ship with the product: a tenant imports the
 * datasets it holds, each with its publisher, year and source document, and every comparison names its dataset. Each
 * organisation's approved annual value is placed in a quartile band; an internal benchmark ranks the organisations.
 */
@Service
public class BenchmarkService {

    private static final String SELECT = "select d.*, s.name as sector_name, f.original_name as file_name from analytics.benchmark_datasets d "
            + "left join reference.sectors s on s.code = d.sector_code left join files.file_metadata f on f.id = d.source_file_id ";

    private final JdbcTemplate jdbc;
    private final AnalyticsSupport support;

    public BenchmarkService(JdbcTemplate jdbc, AnalyticsSupport support) {
        this.jdbc = jdbc;
        this.support = support;
    }

    public List<BenchmarkDataset> list() {
        Map<String, MetricInfo> metrics = support.metrics();
        return jdbc.query(SELECT + "where d.deleted_at is null order by d.status, d.publication_year desc, lower(d.name)", (rs, i) -> dataset(rs, metrics));
    }

    public BenchmarkDataset get(UUID id) {
        return jdbc.query(SELECT + "where d.id = ? and d.deleted_at is null", (rs, i) -> dataset(rs, support.metrics()), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Benchmark dataset"));
    }

    @Transactional
    public BenchmarkDataset create(BenchmarkDatasetRequest r) {
        check(r, null);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into analytics.benchmark_datasets (id, name, publisher, publication_year, source_url, sector_code, country_code, source_file_id, "
                + "notes, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, r.name().trim(), r.publisher().trim(), r.publicationYear(), blank(r.sourceUrl()),
                blank(r.sectorCode()), blank(r.countryCode()), r.sourceFileId(), blank(r.notes()), r.status());
        insertValues(id, r.values());
        linkFile(r.sourceFileId(), id);
        return get(id);
    }

    @Transactional
    public BenchmarkDataset update(UUID id, BenchmarkDatasetRequest r) {
        get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r, id);
        int n = jdbc.update("update analytics.benchmark_datasets set name = ?, publisher = ?, publication_year = ?, source_url = ?, sector_code = ?, "
                + "country_code = ?, source_file_id = ?, notes = ?, status = ?, version = version + 1 where id = ? and version = ? and deleted_at is null",
                r.name().trim(), r.publisher().trim(), r.publicationYear(), blank(r.sourceUrl()), blank(r.sectorCode()), blank(r.countryCode()),
                r.sourceFileId(), blank(r.notes()), r.status(), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This dataset was changed by someone else. Reload and try again.");
        }
        jdbc.update("delete from analytics.benchmark_values where dataset_id = ?", id);
        insertValues(id, r.values());
        linkFile(r.sourceFileId(), id);
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        jdbc.update("update analytics.benchmark_datasets set deleted_at = now(), version = version + 1 where id = ?", id);
    }

    // ---------------------------------------------------------------- comparisons

    /** Every active dataset's figures for the year (or its latest earlier year), with each organisation's band. */
    public List<Comparison> compare(int year, String metricKey) {
        Map<String, MetricInfo> metrics = support.metrics();
        List<Map<String, Object>> rows = jdbc.queryForList("select distinct on (d.id, v.metric_key) d.id, d.name, d.publisher, d.publication_year, v.metric_key, "
                + "v.data_year, v.p25, v.median, v.p75, v.sample_size from analytics.benchmark_values v join analytics.benchmark_datasets d on d.id = v.dataset_id "
                + "where d.deleted_at is null and d.status = 'active' and v.data_year <= ?" + (metricKey == null ? "" : " and v.metric_key = ?")
                + " order by d.id, v.metric_key, v.data_year desc", metricKey == null ? new Object[] {year} : new Object[] {year, metricKey});
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<String> keys = new HashSet<>();
        rows.forEach(r -> keys.add((String) r.get("metric_key")));
        Map<UUID, Map<String, Map<Integer, BigDecimal>>> byOrg = support.annualByOrg(keys, year, year, null);
        Map<String, Map<Integer, BigDecimal>> total = support.annual(keys, year, year, null);
        Map<UUID, String> names = support.orgNames();
        List<Comparison> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String key = (String) r.get("metric_key");
            MetricInfo m = metrics.get(key);
            if (m == null) {
                continue;
            }
            double p25 = ((BigDecimal) r.get("p25")).doubleValue();
            double med = ((BigDecimal) r.get("median")).doubleValue();
            double p75 = ((BigDecimal) r.get("p75")).doubleValue();
            List<Placement> orgs = new ArrayList<>();
            byOrg.forEach((org, values) -> {
                BigDecimal v = values.getOrDefault(key, Map.of()).get(year);
                if (v != null) {
                    orgs.add(new Placement(org, names.get(org), v, AnalyticsMath.band(v.doubleValue(), p25, med, p75, m.direction())));
                }
            });
            orgs.sort(Comparator.comparing(Placement::orgName, Comparator.nullsLast(String::compareTo)));
            BigDecimal t = total.getOrDefault(key, Map.of()).get(year);
            out.add(new Comparison(key, m.name(), m.unit(), m.direction(), m.comparable(), (UUID) r.get("id"), (String) r.get("name"), (String) r.get("publisher"),
                    ((Number) r.get("publication_year")).intValue(), ((Number) r.get("data_year")).intValue(), (BigDecimal) r.get("p25"),
                    (BigDecimal) r.get("median"), (BigDecimal) r.get("p75"), (Integer) r.get("sample_size"),
                    t == null ? null : new Placement(null, "All organisations", t, AnalyticsMath.band(t.doubleValue(), p25, med, p75, m.direction())), orgs));
        }
        out.sort(Comparator.comparing(Comparison::metricName).thenComparing(Comparison::datasetName));
        return out;
    }

    /** The organisations the user can see, ranked on one metric for the year (best first; neutral metrics highest first). */
    public InternalBenchmark internal(String metricKey, int year) {
        MetricInfo m = support.metrics().get(metricKey);
        if (m == null) {
            throw FieldValidationException.of("metric", "must be a numeric catalogue metric");
        }
        Map<UUID, String> names = support.orgNames();
        List<Map.Entry<UUID, BigDecimal>> values = new ArrayList<>();
        support.annualByOrg(List.of(metricKey), year, year, null).forEach((org, byMetric) -> {
            BigDecimal v = byMetric.getOrDefault(metricKey, Map.of()).get(year);
            if (v != null) {
                values.add(Map.entry(org, v));
            }
        });
        Comparator<Map.Entry<UUID, BigDecimal>> order = Map.Entry.comparingByValue();
        values.sort("lower_better".equals(m.direction()) ? order : order.reversed());
        List<InternalRow> rows = new ArrayList<>();
        int rank = 0;
        BigDecimal previous = null;
        for (int i = 0; i < values.size(); i++) {
            BigDecimal v = values.get(i).getValue();
            if (previous == null || v.compareTo(previous) != 0) {
                rank = i + 1;
            }
            previous = v;
            rows.add(new InternalRow(values.get(i).getKey(), names.get(values.get(i).getKey()), v, rank));
        }
        return new InternalBenchmark(metricKey, m.name(), m.unit(), m.direction(), m.comparable(), year, rows);
    }

    // ---------------------------------------------------------------- internals

    private void check(BenchmarkDatasetRequest r, UUID id) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, MetricInfo> metrics = support.metrics();
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from analytics.benchmark_datasets where lower(name) = lower(?) and deleted_at is null "
                + "and id is distinct from ?)", Boolean.class, r.name().trim(), id))) {
            errors.put("name", "is already used by another dataset");
        }
        if (blank(r.sectorCode()) != null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from reference.sectors where code = ?)",
                Boolean.class, r.sectorCode()))) {
            errors.put("sectorCode", "must be a sector in the catalogue");
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from files.file_metadata where id = ?)", Boolean.class, r.sourceFileId()))) {
            errors.put("sourceFileId", "attach the source document");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < r.values().size(); i++) {
            BenchmarkValueRequest v = r.values().get(i);
            if (!metrics.containsKey(v.metricKey())) {
                errors.put("values[" + i + "].metricKey", "must be a numeric catalogue metric");
            } else if (!seen.add(v.metricKey() + "|" + v.dataYear())) {
                errors.put("values[" + i + "].metricKey", "this metric and year is already in the dataset");
            }
            if (v.p25().compareTo(v.median()) > 0 || v.median().compareTo(v.p75()) > 0) {
                errors.put("values[" + i + "].median", "P25 ≤ median ≤ P75");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    /** A source document uploaded for the dataset (not yet tied to a record) becomes the dataset's evidence file. */
    private void linkFile(UUID fileId, UUID datasetId) {
        jdbc.update("update files.file_metadata set resource_type = 'benchmark_dataset', resource_id = ? where id = ? and resource_id is null", datasetId, fileId);
    }

    private void insertValues(UUID datasetId, List<BenchmarkValueRequest> values) {
        for (BenchmarkValueRequest v : values) {
            jdbc.update("insert into analytics.benchmark_values (dataset_id, metric_key, data_year, p25, median, p75, sample_size) values (?, ?, ?, ?, ?, ?, ?)",
                    datasetId, v.metricKey(), v.dataYear(), v.p25(), v.median(), v.p75(), v.sampleSize());
        }
    }

    private BenchmarkDataset dataset(ResultSet rs, Map<String, MetricInfo> metrics) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<BenchmarkValue> values = jdbc.query("select metric_key, data_year, p25, median, p75, sample_size from analytics.benchmark_values "
                + "where dataset_id = ? order by metric_key, data_year", (v, i) -> {
                    MetricInfo m = metrics.get(v.getString(1));
                    return new BenchmarkValue(v.getString(1), m == null ? v.getString(1) : m.name(), m == null ? null : m.unit(), v.getInt(2),
                            v.getBigDecimal(3), v.getBigDecimal(4), v.getBigDecimal(5), (Integer) v.getObject(6));
                }, id);
        return new BenchmarkDataset(id, rs.getString("name"), rs.getString("publisher"), rs.getInt("publication_year"), rs.getString("source_url"),
                rs.getString("sector_code"), rs.getString("sector_name"), rs.getString("country_code"), rs.getObject("source_file_id", UUID.class),
                rs.getString("file_name"), rs.getString("notes"), rs.getString("status"), values, rs.getLong("version"));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
