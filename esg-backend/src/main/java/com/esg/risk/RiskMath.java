package com.esg.risk;

import com.esg.carbon.TargetMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * M5 calculations (TRD §8.5; D32), golden-tested in RiskMathTest. Every figure is rounded half-up to 2 decimals from the
 * stored scores, so a matrix position can be reproduced by hand from what the screen shows.
 * <pre>
 *   severity        = (scale + scope + irremediability) ÷ 3                      (1–5 each)
 *   impact          = severity                         for an actual impact
 *                   = severity × likelihood ÷ 5        for a potential impact     (inside-out)
 *   financial       = magnitude × likelihood ÷ 5                                  (outside-in)
 *   stakeholder     = Σ(weight × rating) ÷ Σ weight    over groups that rated the topic
 *   impact position = impact × (1 − blend) + stakeholder × blend                 (blend 0–0.5; 0 = no stakeholder pull)
 *   material        = impact position ≥ impact threshold, or financial ≥ financial threshold (double materiality)
 *   risk score      = likelihood × impact (1–25): low ≤ 4, medium 5–9, high 10–14, very high ≥ 15
 *   transition cost = carbon price × Scope 1+2 emissions, with no action and on the reduction target's pathway
 * </pre>
 */
public final class RiskMath {
    private RiskMath() {}

    private static final BigDecimal FIVE = BigDecimal.valueOf(5);

    public record Scores(String impactType, int scale, int scope, int irremediability, Integer impactLikelihood, int magnitude,
                         int financialLikelihood, Map<String, Integer> ratings) {}

    public record Position(BigDecimal severity, BigDecimal impact, BigDecimal financial, BigDecimal stakeholder, BigDecimal impactPosition,
                           boolean impactMaterial, boolean financialMaterial, String quadrant) {}

    public static Position position(Scores s, Map<String, BigDecimal> weights, BigDecimal blend, BigDecimal impactThreshold,
                                    BigDecimal financialThreshold) {
        int sum = s.scale() + s.scope() + s.irremediability();
        BigDecimal severity = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal impact = "potential".equals(s.impactType())
                ? BigDecimal.valueOf((long) sum * s.impactLikelihood()).divide(BigDecimal.valueOf(15), 2, RoundingMode.HALF_UP)
                : severity;
        BigDecimal financial = BigDecimal.valueOf((long) s.magnitude() * s.financialLikelihood()).divide(FIVE, 2, RoundingMode.HALF_UP);
        BigDecimal stakeholder = stakeholder(s.ratings(), weights);
        BigDecimal position = stakeholder == null || blend == null || blend.signum() == 0 ? impact
                : impact.multiply(BigDecimal.ONE.subtract(blend)).add(stakeholder.multiply(blend)).setScale(2, RoundingMode.HALF_UP);
        boolean impactMaterial = position.compareTo(impactThreshold) >= 0;
        boolean financialMaterial = financial.compareTo(financialThreshold) >= 0;
        String quadrant = impactMaterial && financialMaterial ? "double" : impactMaterial ? "impact" : financialMaterial ? "financial"
                : "not_material";
        return new Position(severity, impact, financial, stakeholder, position, impactMaterial, financialMaterial, quadrant);
    }

    /** Weighted mean rating over the groups that rated the topic and carry a weight; null when none did. */
    static BigDecimal stakeholder(Map<String, Integer> ratings, Map<String, BigDecimal> weights) {
        BigDecimal num = BigDecimal.ZERO;
        BigDecimal den = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> r : ratings.entrySet()) {
            BigDecimal w = weights.get(r.getKey());
            if (w != null && w.signum() > 0) {
                num = num.add(w.multiply(BigDecimal.valueOf(r.getValue())));
                den = den.add(w);
            }
        }
        return den.signum() == 0 ? null : num.divide(den, 2, RoundingMode.HALF_UP);
    }

    /** Band of a 1–25 likelihood × impact score. */
    public static String band(int score) {
        return score >= 15 ? "very_high" : score >= 10 ? "high" : score >= 5 ? "medium" : "low";
    }

    /** The reduction target used for the "with target" pathway (absolute, covering Scope 1 and 2). */
    public record Target(String pathway, BigDecimal baseValue, BigDecimal targetValue, int baseYear, int targetYear) {}

    public record PricePoint(int year, BigDecimal priceUsd, String source) {}

    public record ExposureRow(int year, BigDecimal priceUsd, String source, BigDecimal emissionsNoAction, BigDecimal costNoActionUsd,
                              BigDecimal emissionsWithTarget, BigDecimal costWithTargetUsd) {}

    /**
     * Transition exposure per price year. Emissions with no action stay at the baseline; on the target pathway they follow
     * the target's expected trajectory (TargetMath, held after the target year) scaled to the baseline year, so the
     * baseline year itself is unchanged. Tonnes to 3 decimals, money to 2.
     */
    public static List<ExposureRow> exposure(BigDecimal baselineTonnes, int baselineYear, Target target, List<PricePoint> prices) {
        List<ExposureRow> out = new ArrayList<>();
        BigDecimal anchor = target == null ? null : TargetMath.expected(target.pathway(), target.baseValue(), target.targetValue(),
                target.baseYear(), target.targetYear(), baselineYear);
        for (PricePoint p : prices) {
            BigDecimal noAction = baselineTonnes.setScale(3, RoundingMode.HALF_UP);
            BigDecimal withTarget = null;
            if (target != null && anchor.signum() > 0) {
                BigDecimal expected = TargetMath.expected(target.pathway(), target.baseValue(), target.targetValue(), target.baseYear(),
                        target.targetYear(), Math.max(p.year(), baselineYear));
                withTarget = baselineTonnes.multiply(expected).divide(anchor, 3, RoundingMode.HALF_UP);
            }
            out.add(new ExposureRow(p.year(), p.priceUsd(), p.source(), noAction, p.priceUsd().multiply(noAction).setScale(2, RoundingMode.HALF_UP),
                    withTarget, withTarget == null ? null : p.priceUsd().multiply(withTarget).setScale(2, RoundingMode.HALF_UP)));
        }
        return out;
    }
}
