package com.esg.datahub;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * TRD §8.1 data quality score:
 * <pre>Quality = 0.30 × completeness + 0.25 × consistency + 0.25 × timeliness + 0.20 × accuracy</pre>
 * The TRD names the sub-scores; their exact definitions are D21 (proposed, for ESG SME sign-off):
 * <ul>
 *   <li><b>completeness</b> = expected attributes present ÷ expected (value, evidence, data source; plus the
 *       original currency for currency metrics);</li>
 *   <li><b>consistency</b> = max(0, 1 − |value − mean| ÷ |mean|) against the same organisation's validated or
 *       approved values for the metric over like-length periods in the previous 24 months; 1 with no history;
 *       mean 0 → 1 if the value is 0, else 0; 1 for non-numeric values;</li>
 *   <li><b>timeliness</b> = 1 up to 30 days after the period ends, falling linearly to 0 at 365 days;</li>
 *   <li><b>accuracy</b> = cross-checks passed ÷ cross-checks that apply (warning rules: plausibility ranges and
 *       cross-metric identities with a value to compare against); 1 when none apply.</li>
 * </ul>
 * Sub-scores are kept to 4 decimals; the score is rounded half-up to 2 decimals (column DECIMAL(3,2)).
 */
public final class QualityScoreCalculator {

    static final BigDecimal W_COMPLETENESS = new BigDecimal("0.30");
    static final BigDecimal W_CONSISTENCY = new BigDecimal("0.25");
    static final BigDecimal W_TIMELINESS = new BigDecimal("0.25");
    static final BigDecimal W_ACCURACY = new BigDecimal("0.20");
    static final int ON_TIME_DAYS = 30;
    static final int STALE_DAYS = 365;
    private static final MathContext MC = MathContext.DECIMAL64;

    private QualityScoreCalculator() {}

    /**
     * @param value      the numeric value, or null for text/boolean metrics
     * @param history    comparable earlier values (same org, metric, like-length period)
     * @param assessedOn the day the score is computed (submission day)
     */
    public record Inputs(int expectedAttributes, int presentAttributes, BigDecimal value, List<BigDecimal> history,
                         LocalDate periodEnd, LocalDate assessedOn, int checksApplicable, int checksPassed) {}

    public record Breakdown(BigDecimal completeness, BigDecimal consistency, BigDecimal timeliness, BigDecimal accuracy,
                            BigDecimal score, List<String> notes) {}

    public static Breakdown calculate(Inputs in) {
        List<String> notes = new ArrayList<>();

        BigDecimal completeness = in.expectedAttributes() <= 0 ? BigDecimal.ONE
                : BigDecimal.valueOf(Math.min(in.presentAttributes(), in.expectedAttributes()))
                        .divide(BigDecimal.valueOf(in.expectedAttributes()), MC);

        BigDecimal consistency;
        if (in.value() == null) {
            consistency = BigDecimal.ONE;
            notes.add("consistency: not a numeric metric");
        } else if (in.history() == null || in.history().isEmpty()) {
            consistency = BigDecimal.ONE;
            notes.add("consistency: no comparable history yet");
        } else {
            BigDecimal mean = in.history().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(in.history().size()), MC);
            if (mean.signum() == 0) {
                consistency = in.value().signum() == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            } else {
                BigDecimal deviation = in.value().subtract(mean).abs().divide(mean.abs(), MC);
                consistency = BigDecimal.ONE.subtract(deviation).max(BigDecimal.ZERO);
            }
            notes.add("consistency: compared with the mean of " + in.history().size() + " earlier value(s)");
        }

        long days = ChronoUnit.DAYS.between(in.periodEnd(), in.assessedOn());
        BigDecimal timeliness;
        if (days <= ON_TIME_DAYS) {
            timeliness = BigDecimal.ONE;
        } else if (days >= STALE_DAYS) {
            timeliness = BigDecimal.ZERO;
        } else {
            timeliness = BigDecimal.ONE.subtract(BigDecimal.valueOf(days - ON_TIME_DAYS)
                    .divide(BigDecimal.valueOf(STALE_DAYS - ON_TIME_DAYS), MC));
        }
        if (days > ON_TIME_DAYS) {
            notes.add("timeliness: submitted " + days + " days after the period ended");
        }

        BigDecimal accuracy = in.checksApplicable() <= 0 ? BigDecimal.ONE
                : BigDecimal.valueOf(in.checksPassed()).divide(BigDecimal.valueOf(in.checksApplicable()), MC);
        if (in.checksApplicable() > in.checksPassed()) {
            notes.add("accuracy: " + (in.checksApplicable() - in.checksPassed()) + " of " + in.checksApplicable()
                    + " cross-check(s) failed");
        }

        BigDecimal score = W_COMPLETENESS.multiply(completeness).add(W_CONSISTENCY.multiply(consistency))
                .add(W_TIMELINESS.multiply(timeliness)).add(W_ACCURACY.multiply(accuracy))
                .setScale(2, RoundingMode.HALF_UP);
        return new Breakdown(round4(completeness), round4(consistency), round4(timeliness), round4(accuracy), score, notes);
    }

    private static BigDecimal round4(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP);
    }
}
