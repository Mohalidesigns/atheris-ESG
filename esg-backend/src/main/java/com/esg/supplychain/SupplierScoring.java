package com.esg.supplychain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Supplier scorecards (TRD §8.6 ScorecardEngine; D33), golden-tested in SupplierScoringTest.
 * <pre>
 *   question score  yes/no: 100 for the positive answer, 0 otherwise; scale 1–5: (answer − 1) × 25;
 *                   unanswered, or evidence required but not attached: 0; number/text and weight 0: not scored
 *   pillar score    = Σ(weight × score) ÷ Σ weight over the pillar's scored questions          (0–100, 1 decimal)
 *   total           = Σ(pillar weight × pillar score) ÷ Σ pillar weights, over pillars with scored questions
 *   risk tier       total ≥ 70 low · ≥ 40 medium · < 40 high
 * </pre>
 */
public final class SupplierScoring {
    private SupplierScoring() {}

    public record Question(String id, String pillar, String answerType, String positiveAnswer, int weight, boolean evidenceRequired) {}

    public record Answer(String choice, boolean hasEvidence) {}

    public record Gap(String questionId, String reason) {}

    public record Result(BigDecimal e, BigDecimal s, BigDecimal g, BigDecimal total, String riskLevel, List<Gap> gaps) {}

    public static Result score(List<Question> questions, Map<String, Answer> answers, int weightE, int weightS, int weightG) {
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (String p : List.of("E", "S", "G")) {
            sums.put(p, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        }
        List<Gap> gaps = new ArrayList<>();
        for (Question q : questions) {
            if (q.weight() == 0 || !("yes_no".equals(q.answerType()) || "scale".equals(q.answerType()))) {
                continue;
            }
            Answer a = answers.get(q.id());
            int s;
            if (a == null || a.choice() == null) {
                s = 0;
                gaps.add(new Gap(q.id(), "not answered"));
            } else if (q.evidenceRequired() && !a.hasEvidence()) {
                s = 0;
                gaps.add(new Gap(q.id(), "evidence required but not attached"));
            } else if ("yes_no".equals(q.answerType())) {
                s = a.choice().equals(q.positiveAnswer()) ? 100 : 0;
                if (s == 0) {
                    gaps.add(new Gap(q.id(), "answer doesn't meet the expectation"));
                }
            } else {
                s = (Integer.parseInt(a.choice()) - 1) * 25;
                if (s < 50) {
                    gaps.add(new Gap(q.id(), "rated " + a.choice() + " of 5"));
                }
            }
            BigDecimal[] acc = sums.get(q.pillar());
            acc[0] = acc[0].add(BigDecimal.valueOf((long) q.weight() * s));
            acc[1] = acc[1].add(BigDecimal.valueOf(q.weight()));
        }
        BigDecimal e = pillar(sums.get("E"));
        BigDecimal s = pillar(sums.get("S"));
        BigDecimal g = pillar(sums.get("G"));
        BigDecimal num = BigDecimal.ZERO;
        BigDecimal den = BigDecimal.ZERO;
        BigDecimal[] scores = {e, s, g};
        int[] weights = {weightE, weightS, weightG};
        for (int i = 0; i < 3; i++) {
            if (scores[i] != null && weights[i] > 0) {
                num = num.add(scores[i].multiply(BigDecimal.valueOf(weights[i])));
                den = den.add(BigDecimal.valueOf(weights[i]));
            }
        }
        BigDecimal total = den.signum() == 0 ? null : num.divide(den, 1, RoundingMode.HALF_UP);
        return new Result(e, s, g, total, tier(total), gaps);
    }

    private static BigDecimal pillar(BigDecimal[] acc) {
        return acc[1].signum() == 0 ? null : acc[0].divide(acc[1], 1, RoundingMode.HALF_UP);
    }

    public static String tier(BigDecimal total) {
        if (total == null) {
            return null;
        }
        return total.compareTo(BigDecimal.valueOf(70)) >= 0 ? "low" : total.compareTo(BigDecimal.valueOf(40)) >= 0 ? "medium" : "high";
    }
}
