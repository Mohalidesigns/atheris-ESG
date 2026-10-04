package com.esg.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Golden values for the WP-32 calculations (computed by hand; D32). */
class RiskMathTest {

  private static BigDecimal d(String v) {
    return new BigDecimal(v);
  }

  private static final Map<String, BigDecimal> WEIGHTS = Map.of("investors", d("40"), "communities", d("60"));

  private static RiskMath.Position pos(RiskMath.Scores s, String blend) {
    return RiskMath.position(s, WEIGHTS, d(blend), d("3.00"), d("3.00"));
  }

  @Test
  void actualImpactIsTheMeanSeverity() {
    // (4 + 3 + 5) ÷ 3 = 4.00; financial 4 × 4 ÷ 5 = 3.20 → both above 3.00: double material.
    RiskMath.Position p = pos(new RiskMath.Scores("actual", 4, 3, 5, null, 4, 4, Map.of()), "0");
    assertThat(p.severity()).isEqualByComparingTo("4.00");
    assertThat(p.impact()).isEqualByComparingTo("4.00");
    assertThat(p.financial()).isEqualByComparingTo("3.20");
    assertThat(p.stakeholder()).isNull();
    assertThat(p.impactPosition()).isEqualByComparingTo("4.00");
    assertThat(p.quadrant()).isEqualTo("double");
  }

  @Test
  void potentialImpactIsScaledByLikelihood() {
    // 12 × 3 ÷ 15 = 2.40 → below the impact threshold; financial 3.20 → financial only.
    RiskMath.Position p = pos(new RiskMath.Scores("potential", 4, 3, 5, 3, 4, 4, Map.of()), "0");
    assertThat(p.impact()).isEqualByComparingTo("2.40");
    assertThat(p.quadrant()).isEqualTo("financial");
    // (2 + 2 + 3) ÷ 3 = 2.33; × 2 ÷ 5 → 0.93; financial 1 × 1 ÷ 5 = 0.20: not material.
    RiskMath.Position low = pos(new RiskMath.Scores("potential", 2, 2, 3, 2, 1, 1, Map.of()), "0");
    assertThat(low.severity()).isEqualByComparingTo("2.33");
    assertThat(low.impact()).isEqualByComparingTo("0.93");
    assertThat(low.financial()).isEqualByComparingTo("0.20");
    assertThat(low.quadrant()).isEqualTo("not_material");
  }

  @Test
  void stakeholderRatingsPullTheImpactPositionByTheBlend() {
    // (40 × 5 + 60 × 3) ÷ 100 = 3.80; position 4.00 × 0.75 + 3.80 × 0.25 = 3.95.
    RiskMath.Scores s = new RiskMath.Scores("actual", 4, 3, 5, null, 4, 4, Map.of("investors", 5, "communities", 3));
    RiskMath.Position p = pos(s, "0.25");
    assertThat(p.stakeholder()).isEqualByComparingTo("3.80");
    assertThat(p.impactPosition()).isEqualByComparingTo("3.95");
    // A group without a weight doesn't count; blend 0 leaves the position at the impact score.
    RiskMath.Scores unweighted = new RiskMath.Scores("actual", 4, 3, 5, null, 4, 4, Map.of("investors", 5, "regulators", 1));
    assertThat(pos(unweighted, "0.5").stakeholder()).isEqualByComparingTo("5.00");
    assertThat(pos(s, "0").impactPosition()).isEqualByComparingTo("4.00");
    // An impact pulled below the threshold by stakeholders becomes financial-only.
    RiskMath.Scores pulled = new RiskMath.Scores("actual", 3, 3, 3, null, 4, 4, Map.of("investors", 1, "communities", 1));
    assertThat(pos(pulled, "0.5").impactPosition()).isEqualByComparingTo("2.00");
    assertThat(pos(pulled, "0.5").quadrant()).isEqualTo("financial");
  }

  @Test
  void riskBandsOnTheFiveByFiveGrid() {
    assertThat(RiskMath.band(4)).isEqualTo("low");
    assertThat(RiskMath.band(5)).isEqualTo("medium");
    assertThat(RiskMath.band(9)).isEqualTo("medium");
    assertThat(RiskMath.band(10)).isEqualTo("high");
    assertThat(RiskMath.band(12)).isEqualTo("high");
    assertThat(RiskMath.band(15)).isEqualTo("very_high");
    assertThat(RiskMath.band(25)).isEqualTo("very_high");
  }

  @Test
  void transitionExposureIsPriceTimesEmissionsWithAndWithoutTheTarget() {
    // Baseline 10,000 t in 2025. Target: 12,000 (2020) → 6,000 (2030), linear: expected(2025) = 9,000,
    // expected(2030 and after) = 6,000 → with target 10,000 × 6,000 ÷ 9,000 = 6,666.667 t.
    RiskMath.Target target = new RiskMath.Target("linear", d("12000"), d("6000"), 2020, 2030);
    List<RiskMath.ExposureRow> rows = RiskMath.exposure(d("10000"), 2025, target, List.of(
        new RiskMath.PricePoint(2025, d("50"), "s"), new RiskMath.PricePoint(2030, d("100"), "s"), new RiskMath.PricePoint(2040, d("200"), "s")));
    assertThat(rows.get(0).costNoActionUsd()).isEqualByComparingTo("500000.00");
    assertThat(rows.get(0).emissionsWithTarget()).isEqualByComparingTo("10000.000");
    assertThat(rows.get(1).costNoActionUsd()).isEqualByComparingTo("1000000.00");
    assertThat(rows.get(1).emissionsWithTarget()).isEqualByComparingTo("6666.667");
    assertThat(rows.get(1).costWithTargetUsd()).isEqualByComparingTo("666666.70");
    assertThat(rows.get(2).emissionsWithTarget()).as("held after the target year").isEqualByComparingTo("6666.667");
    assertThat(rows.get(2).costWithTargetUsd()).isEqualByComparingTo("1333333.40");
    // Without a target there is no "with target" series.
    assertThat(RiskMath.exposure(d("10000"), 2025, null, List.of(new RiskMath.PricePoint(2030, d("100"), "s"))).get(0).costWithTargetUsd()).isNull();
  }
}
