package com.esg.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.esg.analytics.AnalyticsMath.Sensitivity;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Golden values for the WP-35 statistics (computed by hand; D34). */
class AnalyticsMathTest {

  private static final Sensitivity S = Sensitivity.DEFAULTS;   // z 3, IQR k 1.5, variance 25 %, history 4
  private static final double[] HISTORY = {10, 12, 11, 13};    // mean 11.5, sd √(5 ÷ 3) = 1.2910, Q1 10.75, Q3 12.25

  @Test
  void descriptiveStatistics() {
    assertThat(AnalyticsMath.mean(HISTORY)).isEqualTo(11.5);
    assertThat(AnalyticsMath.sd(HISTORY)).isCloseTo(1.290994, within(1e-6));
    // Type 7: h = (n − 1) p → Q1 at 0.75 between 10 and 11, Q3 at 2.25 between 12 and 13.
    assertThat(AnalyticsMath.quantile(HISTORY, 0.25)).isCloseTo(10.75, within(1e-9));
    assertThat(AnalyticsMath.quantile(HISTORY, 0.75)).isCloseTo(12.25, within(1e-9));
    assertThat(AnalyticsMath.quantile(new double[] {10, 11, 12, 13, 50}, 0.75)).isEqualTo(13);
    assertThat(AnalyticsMath.sd(new double[] {7})).isZero();
  }

  @Test
  void outliersByZScoreAndIqr() {
    // 20: z = 8.5 ÷ 1.2910 = 6.58 and above the fence 12.25 + 1.5 × 1.5 = 14.5 → both.
    var both = AnalyticsMath.outlier(20, HISTORY, S);
    assertThat(both.method()).isEqualTo("both");
    assertThat(both.z()).isCloseTo(6.584, within(1e-3));
    assertThat(both.low()).isCloseTo(8.5, within(1e-9));
    assertThat(both.high()).isCloseTo(14.5, within(1e-9));
    assertThat(both.n()).isEqualTo(4);
    // 15: z = 2.71 (under 3) but above 14.5 → IQR only. 14: inside both → not flagged.
    assertThat(AnalyticsMath.outlier(15, HISTORY, S).method()).isEqualTo("iqr");
    assertThat(AnalyticsMath.outlier(14, HISTORY, S)).isNull();
    // {0, 0, 10, 10}: mean 5, sd 5.7735, fences −15 to 25 → 24 is 3.29 sd out but inside the fences → z-score only.
    var z = AnalyticsMath.outlier(24, new double[] {0, 0, 10, 10}, S);
    assertThat(z.method()).isEqualTo("z_score");
    assertThat(z.z()).isCloseTo(3.291, within(1e-3));
    // A flat history has no z; any different value is outside the collapsed fences.
    var flat = AnalyticsMath.outlier(6, new double[] {5, 5, 5, 5}, S);
    assertThat(flat.method()).isEqualTo("iqr");
    assertThat(flat.z()).isNull();
    assertThat(AnalyticsMath.outlier(5, new double[] {5, 5, 5, 5}, S)).isNull();
    // Fewer earlier points than the minimum: no verdict.
    assertThat(AnalyticsMath.outlier(100, new double[] {1, 2, 3}, S)).isNull();
    // A stricter tenant setting (z 2) flags 14.5 → z = 2.32.
    assertThat(AnalyticsMath.outlier(14.4, HISTORY, new Sensitivity(2, 3, 25, 4)).method()).isEqualTo("z_score");
  }

  @Test
  void yearOnYearVariance() {
    assertThat(AnalyticsMath.variance(130, 100, S).changePct()).isCloseTo(30, within(1e-9));
    assertThat(AnalyticsMath.variance(75, 100, S).changePct()).isCloseTo(-25, within(1e-9));
    assertThat(AnalyticsMath.variance(120, 100, S)).isNull();
    // A negative base: the change is measured against its size.
    assertThat(AnalyticsMath.variance(-60, -100, S).changePct()).isCloseTo(40, within(1e-9));
    var fromZero = AnalyticsMath.variance(5, 0, S);
    assertThat(fromZero.changePct()).isNull();
    assertThat(fromZero.previous()).isZero();
    assertThat(AnalyticsMath.variance(0, 0, S)).isNull();
  }

  @Test
  void periodLengthClasses() {
    assertThat(AnalyticsMath.lengthClass(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))).isEqualTo("month");
    assertThat(AnalyticsMath.lengthClass(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))).isEqualTo("quarter");
    assertThat(AnalyticsMath.lengthClass(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))).isEqualTo("half");
    assertThat(AnalyticsMath.lengthClass(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))).isEqualTo("year");
    assertThat(AnalyticsMath.lengthClass(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10))).isNull();
  }

  @Test
  void quartileBandsFollowTheDirection() {
    // P25 10, median 20, P75 30.
    assertThat(AnalyticsMath.band(8, 10, 20, 30, "lower_better")).isEqualTo(1);
    assertThat(AnalyticsMath.band(10, 10, 20, 30, "lower_better")).isEqualTo(1);
    assertThat(AnalyticsMath.band(15, 10, 20, 30, "lower_better")).isEqualTo(2);
    assertThat(AnalyticsMath.band(25, 10, 20, 30, "lower_better")).isEqualTo(3);
    assertThat(AnalyticsMath.band(31, 10, 20, 30, "lower_better")).isEqualTo(4);
    assertThat(AnalyticsMath.band(35, 10, 20, 30, "higher_better")).isEqualTo(1);
    assertThat(AnalyticsMath.band(30, 10, 20, 30, "higher_better")).isEqualTo(1);
    assertThat(AnalyticsMath.band(25, 10, 20, 30, "higher_better")).isEqualTo(2);
    assertThat(AnalyticsMath.band(15, 10, 20, 30, "higher_better")).isEqualTo(3);
    assertThat(AnalyticsMath.band(5, 10, 20, 30, "neutral")).isEqualTo(4);
  }

  @Test
  void sdgContributionScore() {
    // 3 of 4 linked metrics evidenced, 1 of 2 comparable improving: 50 × 0.75 + 50 × 0.5 = 62.5 → 63.
    assertThat(AnalyticsMath.sdgScore(4, 3, 2, 1)).isEqualTo(63);
    assertThat(AnalyticsMath.sdgScore(5, 5, 0, 0)).isEqualTo(50);
    assertThat(AnalyticsMath.sdgScore(0, 0, 0, 0)).isZero();
    assertThat(AnalyticsMath.improving(100, 90, "lower_better")).isTrue();
    assertThat(AnalyticsMath.improving(100, 90, "higher_better")).isFalse();
    assertThat(AnalyticsMath.improving(100, 120, "neutral")).isFalse();
  }

  @Test
  void kpiTargetTracking() {
    // Lower is better: 100 in 2020 → 50 by 2030; halfway (2025) the line is at 75.
    assertThat(AnalyticsMath.onTrack(2020, 100.0, 2025, 70, 2030, 50, "lower_better")).isTrue();
    assertThat(AnalyticsMath.onTrack(2020, 100.0, 2025, 80, 2030, 50, "lower_better")).isFalse();
    assertThat(AnalyticsMath.onTrack(null, null, 2025, 45, 2030, 50, "lower_better")).isTrue();     // already met
    assertThat(AnalyticsMath.onTrack(null, null, 2025, 60, 2030, 50, "lower_better")).isNull();     // no line to judge
    // Higher is better: 10 in 2020 → 30 by 2030; in 2025 the line is at 20.
    assertThat(AnalyticsMath.onTrack(2020, 10.0, 2025, 18, 2030, 30, "higher_better")).isFalse();
    assertThat(AnalyticsMath.onTrack(2020, 10.0, 2025, 21, 2030, 30, "higher_better")).isTrue();
  }
}
