package com.esg.datahub;

import static org.assertj.core.api.Assertions.assertThat;

import com.esg.datahub.QualityScoreCalculator.Breakdown;
import com.esg.datahub.QualityScoreCalculator.Inputs;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden values for the TRD §8.1 quality score with the D21 sub-score definitions. */
class QualityScoreCalculatorTest {

  private static final LocalDate END = LocalDate.of(2026, 3, 31);

  private static BigDecimal d(String v) {
    return new BigDecimal(v);
  }

  @Test
  void completeConsistentOnTimeAndAccurateScoresOne() {
    Breakdown b = QualityScoreCalculator.calculate(new Inputs(3, 3, d("1000"), List.of(), END, END.plusDays(10), 0, 0));
    assertThat(b.completeness()).isEqualByComparingTo("1");
    assertThat(b.consistency()).isEqualByComparingTo("1");
    assertThat(b.timeliness()).isEqualByComparingTo("1");
    assertThat(b.accuracy()).isEqualByComparingTo("1");
    assertThat(b.score()).isEqualByComparingTo("1.00");
    assertThat(b.notes()).contains("consistency: no comparable history yet");
  }

  @Test
  void workedExampleFromD21() {
    // completeness 1/3; mean 100 vs 150 → 0.5; 60 days late → 1 − 30/335 = 0.910448; 1 of 2 checks → 0.5
    // 0.3×0.333333 + 0.25×0.5 + 0.25×0.910448 + 0.2×0.5 = 0.552612 → 0.55
    Breakdown b = QualityScoreCalculator.calculate(
        new Inputs(3, 1, d("150"), List.of(d("100"), d("100")), END, END.plusDays(60), 2, 1));
    assertThat(b.completeness()).isEqualByComparingTo("0.3333");
    assertThat(b.consistency()).isEqualByComparingTo("0.5000");
    assertThat(b.timeliness()).isEqualByComparingTo("0.9104");
    assertThat(b.accuracy()).isEqualByComparingTo("0.5000");
    assertThat(b.score()).isEqualByComparingTo("0.55");
  }

  @Test
  void veryLateAndFarFromHistoryKeepsOnlyCompletenessAndAccuracy() {
    // deviation 200% → consistency 0; 400 days → timeliness 0; 0.3 + 0 + 0 + 0.2 = 0.50
    Breakdown b = QualityScoreCalculator.calculate(new Inputs(4, 4, d("300"), List.of(d("100")), END, END.plusDays(400), 0, 0));
    assertThat(b.consistency()).isEqualByComparingTo("0");
    assertThat(b.timeliness()).isEqualByComparingTo("0");
    assertThat(b.score()).isEqualByComparingTo("0.50");
  }

  @Test
  void timelinessBoundaries() {
    assertThat(timeliness(30)).isEqualByComparingTo("1");
    assertThat(timeliness(31)).isEqualByComparingTo("0.9970"); // 1 − 1/335
    assertThat(timeliness(197)).isEqualByComparingTo("0.5015"); // 1 − 167/335
    assertThat(timeliness(365)).isEqualByComparingTo("0");
    assertThat(timeliness(-20)).isEqualByComparingTo("1"); // submitted before the period ended
  }

  @Test
  void zeroHistoryMeanAndNonNumericValues() {
    assertThat(QualityScoreCalculator.calculate(new Inputs(3, 3, d("0"), List.of(d("0")), END, END, 0, 0)).consistency())
        .isEqualByComparingTo("1");
    assertThat(QualityScoreCalculator.calculate(new Inputs(3, 3, d("5"), List.of(d("0")), END, END, 0, 0)).consistency())
        .isEqualByComparingTo("0");
    Breakdown text = QualityScoreCalculator.calculate(new Inputs(3, 3, null, List.of(), END, END, 0, 0));
    assertThat(text.consistency()).isEqualByComparingTo("1");
    assertThat(text.notes()).contains("consistency: not a numeric metric");
  }

  @Test
  void scoreRoundsHalfUpToTwoDecimals() {
    // completeness 2/3, everything else 1: 0.2 + 0.25 + 0.25 + 0.2 = 0.9 exactly
    assertThat(QualityScoreCalculator.calculate(new Inputs(3, 2, d("1"), List.of(), END, END, 0, 0)).score())
        .isEqualByComparingTo("0.90");
    // completeness 3/4: 0.225 + 0.7 = 0.925 → 0.93 (half up)
    assertThat(QualityScoreCalculator.calculate(new Inputs(4, 3, d("1"), List.of(), END, END, 0, 0)).score())
        .isEqualByComparingTo("0.93");
  }

  private static BigDecimal timeliness(int daysAfterEnd) {
    return QualityScoreCalculator.calculate(new Inputs(1, 1, d("1"), List.of(), END, END.plusDays(daysAfterEnd), 0, 0)).timeliness();
  }
}
