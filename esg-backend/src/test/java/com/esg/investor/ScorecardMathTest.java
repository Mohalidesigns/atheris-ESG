package com.esg.investor;

import static org.assertj.core.api.Assertions.assertThat;

import com.esg.investor.ScorecardMath.Component;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden values for the WP-40 investor scorecard (computed by hand; D35 (2)). */
class ScorecardMathTest {

  private static Component c(String v) {
    return new Component("k", "label", v == null ? null : new BigDecimal(v), "basis");
  }

  @Test
  void aPillarIsTheMeanOfTheComponentsThatHaveAValue() {
    // (60 + 80 + 100) ÷ 3 = 80.0; the component with nothing to judge is left out.
    assertThat(ScorecardMath.pillar(List.of(c("60"), c(null), c("80"), c("100")))).isEqualByComparingTo("80.0");
    assertThat(ScorecardMath.pillar(List.of(c("33.3"), c("50")))).isEqualByComparingTo("41.7");   // 41.65 → 41.7
    assertThat(ScorecardMath.pillar(List.of(c(null), c(null)))).isNull();
  }

  @Test
  void theTotalIsTheWeightedMeanOfThePillarsWithAScore() {
    // (80 × 40 + 50 × 30 + 70 × 30) ÷ 100 = 68.0 → B.
    BigDecimal t = ScorecardMath.total(new BigDecimal("80.0"), new BigDecimal("50.0"), new BigDecimal("70.0"), 40, 30, 30);
    assertThat(t).isEqualByComparingTo("68.0");
    assertThat(ScorecardMath.grade(t)).isEqualTo("B");
    // No S score: (80 × 40 + 70 × 30) ÷ 70 = 75.71 → 75.7 → B+.
    BigDecimal noS = ScorecardMath.total(new BigDecimal("80.0"), null, new BigDecimal("70.0"), 40, 30, 30);
    assertThat(noS).isEqualByComparingTo("75.7");
    assertThat(ScorecardMath.grade(noS)).isEqualTo("B+");
    // A pillar weighted 0 doesn't count.
    assertThat(ScorecardMath.total(new BigDecimal("90"), new BigDecimal("10"), new BigDecimal("10"), 100, 0, 0)).isEqualByComparingTo("90.0");
    assertThat(ScorecardMath.total(null, null, null, 40, 30, 30)).isEqualByComparingTo("0.0");
  }

  @Test
  void gradeBands() {
    String[][] cases = {{"90", "A+"}, {"89.9", "A"}, {"80", "A"}, {"79.9", "B+"}, {"70", "B+"}, {"60", "B"}, {"50", "C+"}, {"40", "C"},
        {"30", "D"}, {"29.9", "F"}, {"0", "F"}};
    for (String[] x : cases) {
      assertThat(ScorecardMath.grade(new BigDecimal(x[0]))).as(x[0]).isEqualTo(x[1]);
    }
  }

  @Test
  void sharesAndMaturity() {
    assertThat(ScorecardMath.pct(1, 3)).isEqualByComparingTo("33.3");
    assertThat(ScorecardMath.pct(0, 0)).isNull();
    assertThat(ScorecardMath.fromMaturity(1)).isEqualByComparingTo("0");
    assertThat(ScorecardMath.fromMaturity(3)).isEqualByComparingTo("50");
    assertThat(ScorecardMath.fromMaturity(5)).isEqualByComparingTo("100");
  }
}
