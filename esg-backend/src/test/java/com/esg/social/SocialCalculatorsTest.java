package com.esg.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden values for the WP-33 calculators (computed independently; D31). */
class SocialCalculatorsTest {

  private static BigDecimal d(String v) {
    return new BigDecimal(v);
  }

  // ---------------------------------------------------------------- LTIFR / TRIR (TRD §8.7)

  @Test
  void ltifrIsLostTimeInjuriesPerMillionHours() {
    assertThat(SafetyRates.ltifr(3, d("1200000"))).isEqualByComparingTo("2.50");
    assertThat(SafetyRates.ltifr(1, d("2500000"))).isEqualByComparingTo("0.40");
    assertThat(SafetyRates.ltifr(2, d("333333"))).isEqualByComparingTo("6.00");
    assertThat(SafetyRates.ltifr(0, d("100000"))).isEqualByComparingTo("0.00");
  }

  @Test
  void trirIsRecordableCasesPer200000Hours() {
    assertThat(SafetyRates.trir(5, d("400000"))).isEqualByComparingTo("2.50");
    assertThat(SafetyRates.trir(7, d("1234567"))).isEqualByComparingTo("1.13");
    assertThat(SafetyRates.trir(1, d("200000"))).isEqualByComparingTo("1.00");
  }

  @Test
  void noHoursMeansNoRateNotZero() {
    assertThat(SafetyRates.ltifr(2, null)).isNull();
    assertThat(SafetyRates.trir(2, BigDecimal.ZERO)).isNull();
  }

  @Test
  void outcomesClassifyAsRecordableAndLostTime() {
    assertThat(SafetyRates.recordable("first_aid")).isFalse();
    assertThat(SafetyRates.recordable("none")).isFalse();
    assertThat(SafetyRates.recordable("medical_treatment")).isTrue();
    assertThat(SafetyRates.recordable("restricted_work")).isTrue();
    assertThat(SafetyRates.lostTime("restricted_work")).isFalse();
    assertThat(SafetyRates.lostTime("lost_time")).isTrue();
    assertThat(SafetyRates.lostTime("fatality")).isTrue();
    assertThat(SafetyRates.recordable("fatality")).isTrue();
  }

  // ---------------------------------------------------------------- DEI aggregates and pay gap

  private static final List<WorkforceMetrics.Line> LINES = List.of(
      new WorkforceMetrics.Line("executive", "female", "30_50", 2, 0, d("60000000")),
      new WorkforceMetrics.Line("executive", "male", "over_50", 4, 0, d("160000000")),
      new WorkforceMetrics.Line("middle_management", "female", "30_50", 10, 1, d("120000000")),
      new WorkforceMetrics.Line("middle_management", "male", "30_50", 12, 0, d("168000000")),
      new WorkforceMetrics.Line("non_management", "female", "under_30", 40, 2, d("160000000")),
      new WorkforceMetrics.Line("non_management", "male", "under_30", 50, 3, d("225000000")),
      new WorkforceMetrics.Line("non_management", "undisclosed", "30_50", 2, 0, null));

  @Test
  void sharesOfHeadcount() {
    WorkforceMetrics.Result r = WorkforceMetrics.compute(LINES, 12, 100);
    assertThat(r.headcount()).isEqualTo(120);
    assertThat(r.female()).isEqualTo(52);
    assertThat(r.male()).isEqualTo(66);
    assertThat(r.undisclosed()).isEqualTo(2);
    assertThat(r.femalePct()).isEqualByComparingTo("43.3");
    assertThat(r.management()).isEqualTo(28);
    assertThat(r.womenInManagementPct()).isEqualByComparingTo("42.9");
    assertThat(r.senior()).isEqualTo(6);
    assertThat(r.womenInSeniorPct()).isEqualByComparingTo("33.3");
    assertThat(r.disabledPct()).isEqualByComparingTo("5.0");
    assertThat(r.ageBands()).containsEntry("under_30", 90).containsEntry("30_50", 26).containsEntry("over_50", 4);
    assertThat(r.ageBandPct().get("30_50")).isEqualByComparingTo("21.7");
  }

  @Test
  void meanPayGapOverallAndByLevelWithSmallGroupsWithheld() {
    WorkforceMetrics.Result r = WorkforceMetrics.compute(LINES, 12, 100);
    // Women 340m ÷ 52, men 553m ÷ 66 → (8,378,787.88 − 6,538,461.54) ÷ 8,378,787.88 = 21.96%.
    assertThat(r.payGapPct()).isEqualByComparingTo("22.0");
    assertThat(r.payGapNote()).isNull();
    WorkforceMetrics.LevelRow exec = r.levels().get(0);
    assertThat(exec.level()).isEqualTo("executive");
    assertThat(exec.payGapPct()).as("2 women: one salary could be read off").isNull();
    assertThat(exec.payGapNote()).contains("fewer than 5");
    assertThat(r.levels().get(1).payGapPct()).as("middle: 12m vs 14m").isEqualByComparingTo("14.3");
    assertThat(r.levels().get(2).payGapPct()).as("non-management: 4m vs 4.5m").isEqualByComparingTo("11.1");
  }

  @Test
  void turnoverUsesAverageHeadcountWhenTheOpeningIsKnown() {
    assertThat(WorkforceMetrics.compute(LINES, 12, 100).turnoverPct()).as("12 ÷ ((100 + 120) ÷ 2)").isEqualByComparingTo("10.9");
    WorkforceMetrics.Result closingOnly = WorkforceMetrics.compute(LINES, 12, null);
    assertThat(closingOnly.turnoverPct()).as("12 ÷ 120").isEqualByComparingTo("10.0");
    assertThat(closingOnly.turnoverBasis()).contains("closing headcount");
    assertThat(WorkforceMetrics.compute(LINES, null, 100).turnoverPct()).isNull();
  }

  @Test
  void noPayDataMeansNoGap() {
    WorkforceMetrics.Result r = WorkforceMetrics.compute(List.of(new WorkforceMetrics.Line("staff", "female", "30_50", 9, 0, null)), null, null);
    assertThat(r.payGapPct()).isNull();
    assertThat(r.payGapNote()).isEqualTo("no pay data");
    assertThat(WorkforceMetrics.compute(List.of(), null, null).femalePct()).isNull();
  }

  // ---------------------------------------------------------------- evidence distance

  @Test
  void haversineDistanceBetweenLagosAndAbuja() {
    // Lagos (6.5244, 3.3792) to Abuja (9.0765, 7.3986): about 527 km great-circle.
    BigDecimal km = CommunityService.distanceKm(d("6.5244"), d("3.3792"), d("9.0765"), d("7.3986"));
    assertThat(km).isBetween(d("520"), d("535"));
    assertThat(CommunityService.distanceKm(d("4.8156"), d("7.0498"), d("4.8156"), d("7.0498"))).isEqualByComparingTo("0");
    assertThat(CommunityService.distanceKm(null, d("1"), d("1"), d("1"))).isNull();
  }
}
