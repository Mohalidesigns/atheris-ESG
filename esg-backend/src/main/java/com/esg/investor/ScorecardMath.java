package com.esg.investor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * ESG scorecard arithmetic (TRD §8.10 ScorecardGenerator; D35 (2)): each pillar is the mean of its available components
 * (0–100), the total the weighted mean of the pillars that have a score, the grade from the total. Golden values:
 * ScorecardMathTest. Grade bands are Atheris defaults pending the ESG SME.
 */
public final class ScorecardMath {
    private ScorecardMath() {}

    /** One input to a pillar: a 0–100 value, or null when there is nothing to judge (then it is left out). */
    public record Component(String key, String label, BigDecimal value, String basis) {}

    /** Mean of the components that have a value, to one decimal; null when none has. */
    public static BigDecimal pillar(List<Component> components) {
        List<BigDecimal> values = components.stream().map(Component::value).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    /** Weighted mean of the pillars with a score (their weights re-scaled to 100), to one decimal; 0 when none has. */
    public static BigDecimal total(BigDecimal e, BigDecimal s, BigDecimal g, int we, int ws, int wg) {
        BigDecimal sum = BigDecimal.ZERO;
        int weights = 0;
        BigDecimal[] scores = {e, s, g};
        int[] w = {we, ws, wg};
        for (int i = 0; i < 3; i++) {
            if (scores[i] != null && w[i] > 0) {
                sum = sum.add(scores[i].multiply(BigDecimal.valueOf(w[i])));
                weights += w[i];
            }
        }
        return weights == 0 ? BigDecimal.ZERO.setScale(1) : sum.divide(BigDecimal.valueOf(weights), 1, RoundingMode.HALF_UP);
    }

    public static String grade(BigDecimal total) {
        double t = total.doubleValue();
        return t >= 90 ? "A+" : t >= 80 ? "A" : t >= 70 ? "B+" : t >= 60 ? "B" : t >= 50 ? "C+" : t >= 40 ? "C" : t >= 30 ? "D" : "F";
    }

    /** A share as 0–100 to one decimal; null when the denominator is zero. */
    public static BigDecimal pct(long part, long whole) {
        return whole == 0 ? null : BigDecimal.valueOf(100.0 * part / whole).setScale(1, RoundingMode.HALF_UP);
    }

    /** A maturity dimension (1–5) as 0–100: (score − 1) × 25. */
    public static BigDecimal fromMaturity(int score) {
        return BigDecimal.valueOf((score - 1) * 25L).setScale(1);
    }
}
