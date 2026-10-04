package com.esg.carbon;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * TRD §8.2 ReductionTargetTracker: pathway, progress and status of a reduction target, and an indicative check
 * against the SBTi near-term criteria (D22) — not an SBTi validation.
 * <pre>
 *   linear:       expected(y) = base − (base − target) × (y − baseYear) ÷ (targetYear − baseYear)
 *   contraction:  expected(y) = base × (target ÷ base) ^ ((y − baseYear) ÷ (targetYear − baseYear))
 *   progress %  = (base − actual) ÷ (base − target) × 100
 * </pre>
 */
public final class TargetMath {

    /** SBTi near-term: ≥ 4.2 %/yr linear absolute reduction for Scope 1+2 (1.5 °C), ≥ 2.5 %/yr for Scope 3 (WB2C). */
    static final BigDecimal SBTI_SCOPE_1_2_RATE = new BigDecimal("0.042");
    static final BigDecimal SBTI_SCOPE_3_RATE = new BigDecimal("0.025");
    static final int SBTI_MIN_BASE_YEAR = 2015;
    static final int SBTI_MIN_YEARS_AHEAD = 5;
    static final int SBTI_MAX_YEARS_AHEAD = 10;
    private static final MathContext MC = MathContext.DECIMAL64;

    private TargetMath() {}

    public static BigDecimal expected(String pathway, BigDecimal base, BigDecimal target, int baseYear, int targetYear, int year) {
        if (year <= baseYear) {
            return base.setScale(6, RoundingMode.HALF_UP);
        }
        if (year >= targetYear) {
            return target.setScale(6, RoundingMode.HALF_UP);
        }
        double share = (double) (year - baseYear) / (targetYear - baseYear);
        if ("contraction".equals(pathway)) {
            double ratio = target.doubleValue() / base.doubleValue();
            return BigDecimal.valueOf(base.doubleValue() * Math.pow(ratio, share)).setScale(6, RoundingMode.HALF_UP);
        }
        return base.subtract(base.subtract(target).multiply(BigDecimal.valueOf(share), MC)).setScale(6, RoundingMode.HALF_UP);
    }

    public static BigDecimal progressPct(BigDecimal base, BigDecimal target, BigDecimal actual) {
        return base.subtract(actual).multiply(BigDecimal.valueOf(100))
                .divide(base.subtract(target), 2, RoundingMode.HALF_UP);
    }

    public static String status(BigDecimal target, BigDecimal expected, BigDecimal actual) {
        if (actual == null) {
            return "no_data";
        }
        if (actual.compareTo(target) <= 0) {
            return "achieved";
        }
        return actual.compareTo(expected) <= 0 ? "on_track" : "off_track";
    }

    /** Annual linear reduction rate: (1 − target ÷ base) ÷ years. */
    public static BigDecimal annualRate(BigDecimal base, BigDecimal target, int baseYear, int targetYear) {
        return BigDecimal.ONE.subtract(target.divide(base, MC))
                .divide(BigDecimal.valueOf(targetYear - baseYear), 6, RoundingMode.HALF_UP);
    }

    /** Findings against the SBTi near-term criteria; empty = consistent with them. */
    public static List<String> sbtiFindings(String type, String scope, BigDecimal base, BigDecimal target, int baseYear,
                                            int targetYear, int thisYear) {
        List<String> findings = new ArrayList<>();
        if (!"absolute".equals(type)) {
            findings.add("Intensity targets need a sector pathway (SDA); check them with the SBTi tools");
        }
        if (baseYear < SBTI_MIN_BASE_YEAR) {
            findings.add("Base year must be " + SBTI_MIN_BASE_YEAR + " or later");
        }
        int ahead = targetYear - thisYear;
        if (ahead < SBTI_MIN_YEARS_AHEAD || ahead > SBTI_MAX_YEARS_AHEAD) {
            findings.add("Near-term targets cover 5 to 10 years from today (target year " + (thisYear + SBTI_MIN_YEARS_AHEAD)
                    + "–" + (thisYear + SBTI_MAX_YEARS_AHEAD) + ")");
        }
        BigDecimal minimum = "scope_3".equals(scope) ? SBTI_SCOPE_3_RATE : SBTI_SCOPE_1_2_RATE;
        BigDecimal rate = annualRate(base, target, baseYear, targetYear);
        if ("absolute".equals(type) && rate.compareTo(minimum) < 0) {
            findings.add("Annual linear reduction is " + rate.movePointRight(2).setScale(2, RoundingMode.HALF_UP) + "%; at least "
                    + minimum.movePointRight(2).setScale(1, RoundingMode.HALF_UP) + "% is needed");
        }
        return findings;
    }
}
