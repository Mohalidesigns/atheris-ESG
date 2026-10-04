package com.esg.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * The statistics behind WP-35 (D34), kept free of the database so the golden values can be tested on their own
 * (AnalyticsMathTest): z-score and IQR outliers, year-on-year variance, quartile bands and the SDG contribution score.
 */
public final class AnalyticsMath {
    private AnalyticsMath() {}

    /** Detection settings (analytics.settings). */
    public record Sensitivity(double zThreshold, double iqrK, double variancePct, int minHistory) {
        public static final Sensitivity DEFAULTS = new Sensitivity(3.0, 1.5, 25.0, 4);
    }

    /** An outlier verdict against the history: method is z_score, iqr or both. */
    public record Outlier(String method, double mean, double sd, Double z, double q1, double q3, double low, double high, int n) {}

    /** A year-on-year change beyond the threshold; changePct is null when last year's value was zero. */
    public record Variance(double previous, Double changePct) {}

    // ---------------------------------------------------------------- descriptive statistics

    public static double mean(double[] xs) {
        return Arrays.stream(xs).sum() / xs.length;
    }

    /** Sample standard deviation (n − 1); 0 for fewer than two values. */
    public static double sd(double[] xs) {
        if (xs.length < 2) {
            return 0;
        }
        double m = mean(xs);
        double ss = Arrays.stream(xs).map(x -> (x - m) * (x - m)).sum();
        return Math.sqrt(ss / (xs.length - 1));
    }

    /** Quantile by linear interpolation between order statistics (Hyndman & Fan type 7, the spreadsheet default). */
    public static double quantile(double[] xs, double p) {
        double[] s = xs.clone();
        Arrays.sort(s);
        double h = (s.length - 1) * p;
        int lo = (int) Math.floor(h);
        int hi = (int) Math.ceil(h);
        return s[lo] + (h - lo) * (s[hi] - s[lo]);
    }

    // ---------------------------------------------------------------- anomaly tests

    /**
     * Whether x is out of line with its history: |z| ≥ the threshold (only when the history varies) or outside the fences
     * Q1 − k·IQR, Q3 + k·IQR. Null when it isn't, or when the history is shorter than the minimum.
     */
    public static Outlier outlier(double x, double[] history, Sensitivity s) {
        if (history.length < s.minHistory()) {
            return null;
        }
        double m = mean(history);
        double sd = sd(history);
        Double z = sd == 0 ? null : (x - m) / sd;
        double q1 = quantile(history, 0.25);
        double q3 = quantile(history, 0.75);
        double iqr = q3 - q1;
        double low = q1 - s.iqrK() * iqr;
        double high = q3 + s.iqrK() * iqr;
        boolean byZ = z != null && Math.abs(z) >= s.zThreshold();
        boolean byIqr = x < low || x > high;
        if (!byZ && !byIqr) {
            return null;
        }
        return new Outlier(byZ && byIqr ? "both" : byZ ? "z_score" : "iqr", m, sd, z, q1, q3, low, high, history.length);
    }

    /** A change of at least the threshold against the same period last year (any change from zero counts). */
    public static Variance variance(double x, double previous, Sensitivity s) {
        if (previous == 0) {
            return x == 0 ? null : new Variance(previous, null);
        }
        double pct = (x - previous) / Math.abs(previous) * 100;
        return Math.abs(pct) >= s.variancePct() ? new Variance(previous, pct) : null;
    }

    /** Period length class: month, quarter, half or year; null for any other length (not compared). */
    public static String lengthClass(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days >= 28 && days <= 31) {
            return "month";
        }
        if (days >= 89 && days <= 92) {
            return "quarter";
        }
        if (days >= 181 && days <= 184) {
            return "half";
        }
        if (days == 365 || days == 366) {
            return "year";
        }
        return null;
    }

    // ---------------------------------------------------------------- benchmarks

    /**
     * Quartile band of a value against P25 / median / P75, 1 (best quarter) to 4 (worst), using the metric's direction.
     * Neutral metrics have no better side: 1 is simply the highest quarter. A value on a boundary takes the better band.
     */
    public static int band(double value, double p25, double median, double p75, String direction) {
        int fromTop = value >= p75 ? 1 : value >= median ? 2 : value >= p25 ? 3 : 4;
        if (!"lower_better".equals(direction)) {
            return fromTop;
        }
        return value <= p25 ? 1 : value <= median ? 2 : value <= p75 ? 3 : 4;
    }

    // ---------------------------------------------------------------- SDG contribution (D34 (7))

    /** 50 × evidenced share + 50 × improving share, rounded to a whole number; improving share 0 when nothing compares. */
    public static int sdgScore(int linked, int evidenced, int comparable, int improving) {
        if (linked == 0) {
            return 0;
        }
        double e = (double) evidenced / linked;
        double i = comparable == 0 ? 0 : (double) improving / comparable;
        return (int) Math.round(50 * e + 50 * i);
    }

    /** Whether a change from last year to this year moves in the better direction (neutral never improves). */
    public static boolean improving(double previous, double current, String direction) {
        return "lower_better".equals(direction) ? current < previous : "higher_better".equals(direction) && current > previous;
    }

    // ---------------------------------------------------------------- KPI targets

    /** Whether a value meets the target in the KPI's direction. */
    public static boolean meets(double value, double target, String direction) {
        return "lower_better".equals(direction) ? value <= target : value >= target;
    }

    /**
     * On track when the target is already met, or when the latest value is at least where a straight line from the baseline
     * to the target would be by the latest year. Null (unknown) without a baseline value to draw the line from.
     */
    public static Boolean onTrack(Integer baselineYear, Double baselineValue, int latestYear, double latest, int targetYear, double target,
                                  String direction) {
        if (meets(latest, target, direction)) {
            return true;
        }
        if (baselineYear == null || baselineValue == null || targetYear <= baselineYear) {
            return null;
        }
        double share = Math.min(1, Math.max(0, (double) (latestYear - baselineYear) / (targetYear - baselineYear)));
        double expected = baselineValue + (target - baselineValue) * share;
        return meets(latest, expected, direction);
    }

    static BigDecimal round(Double v, int scale) {
        return v == null || v.isNaN() || v.isInfinite() ? null : BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }

    static double[] doubles(List<BigDecimal> values) {
        return values.stream().mapToDouble(BigDecimal::doubleValue).toArray();
    }
}
