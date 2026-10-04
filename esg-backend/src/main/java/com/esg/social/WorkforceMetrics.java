package com.esg.social;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DEI aggregates (TRD §8.7 DEIMetricsAggregator; GRI 405-1, 405-2, 401-1; D31). Input is headcount lines by level ×
 * gender × age band — never individuals. Golden-tested in WorkforceMetricsTest.
 * <ul>
 *   <li>Shares are of total headcount, 1 decimal. Management = executive + senior + middle management; senior =
 *       executive + senior management.</li>
 *   <li>Pay gap (unadjusted, mean) = (mean male pay − mean female pay) ÷ mean male pay × 100, over lines that carry
 *       pay. It is withheld when fewer than {@link #MIN_GROUP} women or men have pay data, so one person's salary
 *       can't be read off a small group.</li>
 *   <li>Turnover = leavers ÷ average headcount × 100, average of the opening (the previous approved period's closing
 *       headcount) and closing headcount; closing alone when there is no previous period.</li>
 * </ul>
 */
public final class WorkforceMetrics {
    private WorkforceMetrics() {}

    /** Smallest group (women or men with pay data) for which a pay gap is shown. */
    static final int MIN_GROUP = 5;
    static final List<String> LEVELS = List.of("executive", "senior_management", "middle_management", "non_management");
    static final List<String> AGE_BANDS = List.of("under_30", "30_50", "over_50");

    public record Line(String level, String gender, String ageBand, int headcount, int disabled, BigDecimal totalPay) {}

    public record PayGap(BigDecimal pct, String note) {}

    public record LevelRow(String level, int headcount, int female, BigDecimal femalePct, BigDecimal payGapPct, String payGapNote) {}

    public record Result(int headcount, int female, int male, int undisclosed, BigDecimal femalePct, int management,
                         BigDecimal womenInManagementPct, int senior, BigDecimal womenInSeniorPct, int disabled, BigDecimal disabledPct,
                         Map<String, Integer> ageBands, Map<String, BigDecimal> ageBandPct, List<LevelRow> levels,
                         BigDecimal payGapPct, String payGapNote, BigDecimal turnoverPct, String turnoverBasis) {}

    public static Result compute(List<Line> lines, Integer leavers, Integer openingHeadcount) {
        int total = sum(lines, null, null);
        int female = sum(lines, null, "female");
        int male = sum(lines, null, "male");
        List<String> mgmt = List.of("executive", "senior_management", "middle_management");
        List<String> senior = List.of("executive", "senior_management");
        int management = lines.stream().filter(l -> mgmt.contains(l.level())).mapToInt(Line::headcount).sum();
        int managementFemale = lines.stream().filter(l -> mgmt.contains(l.level()) && "female".equals(l.gender())).mapToInt(Line::headcount).sum();
        int seniorTotal = lines.stream().filter(l -> senior.contains(l.level())).mapToInt(Line::headcount).sum();
        int seniorFemale = lines.stream().filter(l -> senior.contains(l.level()) && "female".equals(l.gender())).mapToInt(Line::headcount).sum();
        int disabled = lines.stream().mapToInt(Line::disabled).sum();
        Map<String, Integer> bands = new LinkedHashMap<>();
        Map<String, BigDecimal> bandPct = new LinkedHashMap<>();
        for (String b : AGE_BANDS) {
            int n = lines.stream().filter(l -> b.equals(l.ageBand())).mapToInt(Line::headcount).sum();
            bands.put(b, n);
            bandPct.put(b, pct(n, total));
        }
        List<LevelRow> levels = new ArrayList<>();
        for (String level : LEVELS) {
            List<Line> at = lines.stream().filter(l -> level.equals(l.level())).toList();
            if (at.isEmpty()) {
                continue;
            }
            int n = sum(at, null, null);
            int f = sum(at, null, "female");
            PayGap gap = payGap(at);
            levels.add(new LevelRow(level, n, f, pct(f, n), gap.pct(), gap.note()));
        }
        PayGap overall = payGap(lines);
        BigDecimal turnover = null;
        String basis = null;
        if (leavers != null && total > 0) {
            BigDecimal average = openingHeadcount == null ? BigDecimal.valueOf(total)
                    : BigDecimal.valueOf(openingHeadcount + total).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            basis = openingHeadcount == null ? "leavers ÷ closing headcount (no previous approved period)"
                    : "leavers ÷ average of opening and closing headcount";
            turnover = average.signum() == 0 ? null
                    : BigDecimal.valueOf(100L * leavers).divide(average, 1, RoundingMode.HALF_UP);
        }
        return new Result(total, female, male, total - female - male, pct(female, total), management, pct(managementFemale, management),
                seniorTotal, pct(seniorFemale, seniorTotal), disabled, pct(disabled, total), bands, bandPct, levels, overall.pct(),
                overall.note(), turnover, basis);
    }

    /** Unadjusted mean pay gap over lines with pay; withheld for small groups. */
    static PayGap payGap(List<Line> lines) {
        List<Line> paid = lines.stream().filter(l -> l.totalPay() != null && l.headcount() > 0).toList();
        if (paid.isEmpty()) {
            return new PayGap(null, "no pay data");
        }
        int women = sum(paid, null, "female");
        int men = sum(paid, null, "male");
        if (women < MIN_GROUP || men < MIN_GROUP) {
            return new PayGap(null, "withheld: fewer than " + MIN_GROUP + " women or men with pay data");
        }
        BigDecimal womenPay = pay(paid, "female");
        BigDecimal menPay = pay(paid, "male");
        BigDecimal meanWomen = womenPay.divide(BigDecimal.valueOf(women), 10, RoundingMode.HALF_UP);
        BigDecimal meanMen = menPay.divide(BigDecimal.valueOf(men), 10, RoundingMode.HALF_UP);
        if (meanMen.signum() == 0) {
            return new PayGap(null, "no pay recorded for men");
        }
        return new PayGap(meanMen.subtract(meanWomen).multiply(BigDecimal.valueOf(100)).divide(meanMen, 1, RoundingMode.HALF_UP), null);
    }

    private static BigDecimal pay(List<Line> lines, String gender) {
        return lines.stream().filter(l -> gender.equals(l.gender())).map(Line::totalPay).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int sum(List<Line> lines, String level, String gender) {
        return lines.stream().filter(l -> (level == null || level.equals(l.level())) && (gender == null || gender.equals(l.gender())))
                .mapToInt(Line::headcount).sum();
    }

    static BigDecimal pct(int n, int of) {
        return of == 0 ? null : BigDecimal.valueOf(100L * n).divide(BigDecimal.valueOf(of), 1, RoundingMode.HALF_UP);
    }
}
