package com.esg.social;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Injury frequency rates (TRD §8.7 SafetyIncidentManager; D31). The TRD names the rates without formulas, so the
 * conventional definitions are used and golden-tested (SafetyRatesTest):
 * <ul>
 *   <li>LTIFR = lost-time injuries × 1,000,000 ÷ hours worked (per million hours; lost time includes fatalities);</li>
 *   <li>TRIR = recordable cases × 200,000 ÷ hours worked (per 100 full-time workers a year, OSHA basis).</li>
 * </ul>
 * Both are rounded half-up to 2 decimals; with no hours recorded there is no rate (null), never a zero.
 */
public final class SafetyRates {
    private SafetyRates() {}

    static final BigDecimal LTIFR_BASE = BigDecimal.valueOf(1_000_000);
    static final BigDecimal TRIR_BASE = BigDecimal.valueOf(200_000);

    public static BigDecimal ltifr(long lostTimeInjuries, BigDecimal hoursWorked) {
        return rate(lostTimeInjuries, LTIFR_BASE, hoursWorked);
    }

    public static BigDecimal trir(long recordableCases, BigDecimal hoursWorked) {
        return rate(recordableCases, TRIR_BASE, hoursWorked);
    }

    /** Outcomes counted as recordable (beyond first aid). */
    public static boolean recordable(String outcome) {
        return "medical_treatment".equals(outcome) || "restricted_work".equals(outcome) || lostTime(outcome);
    }

    /** Outcomes counted as lost-time injuries. */
    public static boolean lostTime(String outcome) {
        return "lost_time".equals(outcome) || "fatality".equals(outcome);
    }

    private static BigDecimal rate(long cases, BigDecimal base, BigDecimal hours) {
        if (hours == null || hours.signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(cases).multiply(base).divide(hours, 2, RoundingMode.HALF_UP);
    }
}
