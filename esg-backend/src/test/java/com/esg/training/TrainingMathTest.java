package com.esg.training;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Golden values for the training rules (WP-42, D37; TRD §8.12 AssessmentEngine, CompetencyTracker). */
class TrainingMathTest {

  @Test
  void quizScoreIsTheShareCorrectRoundedHalfUp() {
    assertThat(TrainingMath.score(List.of(0, 1, 2), List.of(0, 1, 2))).isEqualTo(100);
    assertThat(TrainingMath.score(List.of(0, 0, 0), List.of(0, 1, 2))).isEqualTo(33);
    assertThat(TrainingMath.score(List.of(0, 1, 0), List.of(0, 1, 2))).isEqualTo(67);
    assertThat(TrainingMath.score(Arrays.asList(null, 1), List.of(0, 1))).isEqualTo(50);
    assertThat(TrainingMath.score(List.of(1, 0, 0, 0, 0, 0, 0, 0), List.of(1, 1, 1, 1, 1, 1, 1, 1))).isEqualTo(13); // 12.5 → 13
    assertThat(TrainingMath.score(List.of(), List.of())).isEqualTo(100);
  }

  @Test
  void passingIsAtOrAboveThePassMark() {
    assertThat(TrainingMath.passed(70, 70)).isTrue();
    assertThat(TrainingMath.passed(69, 70)).isFalse();
    assertThat(TrainingMath.passed(0, 0)).isTrue();
  }

  @Test
  void hoursPerEmployeeAndCompletionRate() {
    assertThat(TrainingMath.hoursPerEmployee(new BigDecimal("1250"), 48)).isEqualByComparingTo("26.0"); // 26.04
    assertThat(TrainingMath.hoursPerEmployee(new BigDecimal("10"), 4)).isEqualByComparingTo("2.5");
    assertThat(TrainingMath.hoursPerEmployee(new BigDecimal("10"), 0)).isNull();
    assertThat(TrainingMath.completionRate(2, 3)).isEqualByComparingTo("66.7");
    assertThat(TrainingMath.completionRate(1, 8)).isEqualByComparingTo("12.5");
    assertThat(TrainingMath.completionRate(0, 0)).isNull();
  }

  @Test
  void certificatesExpireAfterTheirValidityAndCanBeRevoked() {
    LocalDate issued = LocalDate.of(2026, 1, 31);
    assertThat(TrainingMath.expiry(issued, 12)).isEqualTo(LocalDate.of(2027, 1, 31));
    assertThat(TrainingMath.expiry(issued, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
    assertThat(TrainingMath.expiry(issued, null)).isNull();
    assertThat(TrainingMath.status(LocalDate.of(2027, 1, 31), false, LocalDate.of(2027, 1, 30))).isEqualTo("valid");
    assertThat(TrainingMath.status(LocalDate.of(2027, 1, 31), false, LocalDate.of(2027, 1, 31))).isEqualTo("expired");
    assertThat(TrainingMath.status(null, false, LocalDate.of(2099, 1, 1))).isEqualTo("valid");
    assertThat(TrainingMath.status(null, true, LocalDate.of(2026, 1, 1))).isEqualTo("revoked");
  }

  @Test
  void codesAreTenUnambiguousCharacters() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 2000; i++) {
      String c = TrainingMath.newCode();
      assertThat(c).matches("^[A-HJ-NP-Z2-9]{10}$");
      seen.add(c);
    }
    assertThat(seen).hasSize(2000);
  }
}
