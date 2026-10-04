package com.esg.carbon;

import com.esg.carbon.CarbonDtos.PathwayPoint;
import com.esg.carbon.CarbonDtos.TargetRequest;
import com.esg.carbon.CarbonDtos.TargetResponse;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reduction targets (BRD-5.2.2-06/07, TRD §8.2 ReductionTargetTracker, journey 9). Progress is measured against the
 * tenant's actual emissions for each year (location-based Scope 2), or their intensity for intensity targets; the
 * stored current value, progress and status are refreshed every time targets are read.
 */
@Service
public class TargetService {

    private static final BigDecimal PCT_MIN = new BigDecimal("-999.99");
    private static final BigDecimal PCT_MAX = new BigDecimal("999.99");
    private static final BigDecimal BASELINE_TOLERANCE = new BigDecimal("0.10");

    private final JdbcTemplate jdbc;
    private final EmissionService emissions;

    public TargetService(JdbcTemplate jdbc, EmissionService emissions) {
        this.jdbc = jdbc;
        this.emissions = emissions;
    }

    @Transactional
    public List<TargetResponse> list() {
        return jdbc.queryForList("select id from carbon.reduction_targets where deleted_at is null order by target_year, target_name",
                UUID.class).stream().map(this::get).toList();
    }

    @Transactional
    public TargetResponse get(UUID id) {
        Map<String, Object> row = jdbc.queryForList("select * from carbon.reduction_targets where id = ? and deleted_at is null", id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Reduction target"));
        return evaluate(row);
    }

    @Transactional
    public TargetResponse create(TargetRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into carbon.reduction_targets (id, target_name, target_type, scope, base_year, base_value, target_year, "
                        + "target_value, pathway, sbti_aligned, intensity_denominator) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, r.targetName().trim(), r.targetType(), r.scope(), r.baseYear(), r.baseValue(), r.targetYear(), r.targetValue(),
                pathway(r), Boolean.TRUE.equals(r.sbtiAligned()), denominator(r));
        return get(id);
    }

    @Transactional
    public TargetResponse update(UUID id, TargetRequest r) {
        get(id);
        check(r);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        int updated = jdbc.update("update carbon.reduction_targets set target_name = ?, target_type = ?, scope = ?, base_year = ?, "
                        + "base_value = ?, target_year = ?, target_value = ?, pathway = ?, sbti_aligned = ?, intensity_denominator = ?, "
                        + "version = version + 1 where id = ? and version = ? and deleted_at is null",
                r.targetName().trim(), r.targetType(), r.scope(), r.baseYear(), r.baseValue(), r.targetYear(), r.targetValue(),
                pathway(r), Boolean.TRUE.equals(r.sbtiAligned()), denominator(r), id, r.version());
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This target was changed by someone else. Reload and try again.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id, Long version) {
        get(id);
        if (jdbc.update("update carbon.reduction_targets set deleted_at = now(), version = version + 1 where id = ? and version = ?",
                id, version) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This target was changed by someone else. Reload and try again.");
        }
    }

    /** Actual tonnes (or intensity) for one year in the target's scope; null without data. */
    BigDecimal actual(String scope, String type, String denominator, int year) {
        List<String> scopes = switch (scope) {
            case "scope_1_2" -> List.of("scope_1", "scope_2");
            case "scope_1_2_3" -> List.of("scope_1", "scope_2", "scope_3");
            default -> List.of(scope);
        };
        // F6: the same totals as the Carbon Overview (EmissionService.scopeTotals), tenant-wide.
        Map<String, BigDecimal> totals = emissions.scopeTotals(year, false);
        if (scopes.stream().noneMatch(totals::containsKey)) {
            return null;
        }
        BigDecimal tonnes = scopes.stream().map(s -> totals.getOrDefault(s, BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!"intensity".equals(type)) {
            return tonnes.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal d = emissions.denominators(year).get(denominator);
        if (d == null || d.signum() == 0) {
            return null;
        }
        if ("revenue".equals(denominator)) {
            d = d.divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);
        }
        return tonnes.divide(d, 6, RoundingMode.HALF_UP);
    }

    private TargetResponse evaluate(Map<String, Object> t) {
        UUID id = (UUID) t.get("id");
        String type = (String) t.get("target_type");
        String scope = (String) t.get("scope");
        String pathway = (String) t.get("pathway");
        String denominator = (String) t.get("intensity_denominator");
        int baseYear = (Integer) t.get("base_year");
        int targetYear = (Integer) t.get("target_year");
        BigDecimal base = (BigDecimal) t.get("base_value");
        BigDecimal target = (BigDecimal) t.get("target_value");
        int thisYear = LocalDate.now().getYear();

        List<PathwayPoint> points = new ArrayList<>();
        Integer progressYear = null;
        BigDecimal current = null;
        for (int y = baseYear; y <= targetYear; y++) {
            BigDecimal a = actual(scope, type, denominator, y);
            points.add(new PathwayPoint(y, TargetMath.expected(pathway, base, target, baseYear, targetYear, y), a));
            // F6: the base year counts too, so a target set this year shows this year's actual against its pathway.
            if (a != null) {
                progressYear = y;
                current = a;
            }
        }
        BigDecimal expected = progressYear == null ? null : TargetMath.expected(pathway, base, target, baseYear, targetYear, progressYear);
        // progress_pct is NUMERIC(5,2): a baseline far from what was measured can give e.g. −4,060%; the formula stays
        // as specified, the stored and shown figure is held to the column's range and the mismatch is reported below.
        BigDecimal progress = current == null ? null
                : TargetMath.progressPct(base, target, current).max(PCT_MIN).min(PCT_MAX);
        BigDecimal baseActual = points.isEmpty() ? null : points.get(0).actual();
        String baselineWarning = baseActual == null || base.signum() == 0
                || baseActual.subtract(base).abs().compareTo(base.multiply(BASELINE_TOLERANCE)) <= 0 ? null
                : "Emissions measured for " + baseYear + " (" + baseActual.setScale(1, RoundingMode.HALF_UP).toPlainString()
                        + ") differ from the base value entered (" + base.stripTrailingZeros().toPlainString()
                        + ") by more than 10%. Check the base value and scope.";
        String status = TargetMath.status(target, expected, current);
        jdbc.update("update carbon.reduction_targets set current_value = ?, progress_pct = ?, status = ?, progress_year = ? where id = ? "
                + "and (current_value is distinct from ? or progress_pct is distinct from ? or status <> ? or progress_year is distinct from ?)",
                current, progress, status, progressYear, id, current, progress, status, progressYear);
        return new TargetResponse(id, (String) t.get("target_name"), type, scope, baseYear, base, targetYear, target, pathway,
                (Boolean) t.get("sbti_aligned"), denominator, progressYear, current, expected, progress, status,
                TargetMath.annualRate(base, target, baseYear, targetYear).movePointRight(2).setScale(2, RoundingMode.HALF_UP),
                Boolean.TRUE.equals(t.get("sbti_aligned"))
                        ? TargetMath.sbtiFindings(type, scope, base, target, baseYear, targetYear, thisYear) : List.of(),
                points, ((Number) t.get("version")).longValue(), baselineWarning);
    }

    private static void check(TargetRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.targetYear() <= r.baseYear()) {
            errors.put("targetYear", "must be after the base year");
        }
        if (r.targetValue().compareTo(r.baseValue()) >= 0) {
            errors.put("targetValue", "must be below the base value");
        }
        boolean intensity = "intensity".equals(r.targetType());
        if (intensity && (r.intensityDenominator() == null || r.intensityDenominator().isBlank())) {
            errors.put("intensityDenominator", "is required for an intensity target");
        }
        if (!intensity && r.intensityDenominator() != null && !r.intensityDenominator().isBlank()) {
            errors.put("intensityDenominator", "only applies to intensity targets");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static String pathway(TargetRequest r) {
        return r.pathway() == null || r.pathway().isBlank() ? "linear" : r.pathway();
    }

    private static String denominator(TargetRequest r) {
        return "intensity".equals(r.targetType()) ? r.intensityDenominator() : null;
    }
}
