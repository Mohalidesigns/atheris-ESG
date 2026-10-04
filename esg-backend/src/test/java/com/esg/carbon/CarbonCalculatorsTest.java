package com.esg.carbon;

import static org.assertj.core.api.Assertions.assertThat;

import com.esg.reference.ReferenceDtos.EmissionFactor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Golden values for the WP-21 calculators (computed independently; see D22). */
class CarbonCalculatorsTest {

  private static final EmissionEngine.Gwp AR6 = new EmissionEngine.Gwp("AR6", new BigDecimal("29.8"), new BigDecimal("273"));
  private static final EmissionEngine.Gwp AR5 = new EmissionEngine.Gwp("AR5", new BigDecimal("28"), new BigDecimal("265"));

  private static BigDecimal d(String v) {
    return new BigDecimal(v);
  }

  private static EmissionFactor co2eFactor(String value) {
    return new EmissionFactor(null, "NG", "x", null, d(value), "kgCO2e/unit", "s", null, null, "CO2e", null, "1",
        LocalDate.of(2024, 1, 1), null, "pending_sme", false, 0, null, null, null, "activity", null);
  }

  private static EmissionFactor perGas(String co2, String ch4, String n2o) {
    return new EmissionFactor(null, "GLOBAL", "diesel_generator", null, d("2.6856"), "kgCO2e/litre", "IPCC 2006", 2006, "AR6",
        "CO2e", null, "1", LocalDate.of(2006, 1, 1), null, "pending_sme", false, 0, d(co2), d(ch4), d(n2o), "activity", null);
  }

  // ---------------------------------------------------------------- engine (TRD §8.2)

  @Test
  void co2eFactorsApplyAsPublished_nigeriaGridAndDiesel() {
    assertThat(EmissionEngine.calculate(d("10000"), co2eFactor("0.43"), AR6).co2eTonnes()).isEqualByComparingTo("4.3");
    EmissionEngine.Result diesel = EmissionEngine.calculate(d("1000"), co2eFactor("2.68"), AR6);
    assertThat(diesel.co2eTonnes()).isEqualByComparingTo("2.68");
    assertThat(diesel.perGas()).isFalse();
    assertThat(diesel.co2Tonnes()).isNull();
  }

  @Test
  void perGasFactorsAreWeightedWithTheChosenGwpSet() {
    EmissionFactor diesel = perGas("2.676492", "0.00010836", "0.000021672");
    EmissionEngine.Result ar6 = EmissionEngine.calculate(d("1000"), diesel, AR6);
    assertThat(ar6.co2eTonnes()).isEqualByComparingTo("2.685638");
    assertThat(ar6.co2Tonnes()).isEqualByComparingTo("2.676492");
    assertThat(ar6.ch4Tonnes()).isEqualByComparingTo("0.000108");
    assertThat(ar6.n2oTonnes()).isEqualByComparingTo("0.000022");
    assertThat(ar6.gwpSet()).isEqualTo("AR6");
    assertThat(EmissionEngine.calculate(d("1000"), diesel, AR5).co2eTonnes()).isEqualByComparingTo("2.685269");
  }

  // ---------------------------------------------------------------- flaring (journey 7)

  @Test
  void flaringPureMethaneOneMcf() {
    FlaringCalculator.Result r = FlaringCalculator.calculate(d("1"), Map.of("methane", d("100")), d("0.98"), d("29.8"));
    assertThat(r.co2Tonnes()).isEqualByComparingTo("0.051578");
    assertThat(r.ch4Tonnes()).isEqualByComparingTo("0.000384");
    assertThat(r.co2eTonnes()).isEqualByComparingTo("0.063012");
  }

  @Test
  void flaringByCompositionIncludingInertCo2() {
    Map<String, BigDecimal> gas = Map.of("methane", d("85"), "ethane", d("7"), "propane", d("3"), "butane", d("1"),
        "pentane_plus", d("0.5"), "co2", d("2"), "nitrogen", d("1.5"));
    FlaringCalculator.Result ar6 = FlaringCalculator.calculate(d("1000"), gas, d("0.98"), d("29.8"));
    assertThat(ar6.co2Tonnes()).isEqualByComparingTo("60.108847");
    assertThat(ar6.ch4Tonnes()).isEqualByComparingTo("0.326150");
    assertThat(ar6.co2eTonnes()).isEqualByComparingTo("69.828118");
    assertThat(FlaringCalculator.calculate(d("1000"), gas, d("0.98"), d("28")).co2eTonnes()).isEqualByComparingTo("69.241047");
  }

  // ---------------------------------------------------------------- PCAF (BRD-5.2.2-08)

  @Test
  void pcafAttributionAndFinancedEmissions() {
    BigDecimal af = PcafCalculator.attribution(d("25000000"), d("100000000"));
    assertThat(af).isEqualByComparingTo("0.25");
    assertThat(PcafCalculator.financed(af, d("40000"))).isEqualByComparingTo("10000");
    assertThat(PcafCalculator.attribution(d("1"), d("3"))).isEqualByComparingTo("0.3333333333");
  }

  // ---------------------------------------------------------------- targets (journey 9)

  @Test
  void pathwaysProgressAndStatus() {
    BigDecimal base = d("1000");
    BigDecimal target = d("580");
    assertThat(TargetMath.expected("linear", base, target, 2020, 2030, 2025)).isEqualByComparingTo("790");
    assertThat(TargetMath.expected("contraction", base, target, 2020, 2030, 2025)).isEqualByComparingTo("761.577311");
    assertThat(TargetMath.expected("linear", base, target, 2020, 2030, 2019)).isEqualByComparingTo("1000");
    assertThat(TargetMath.expected("linear", base, target, 2020, 2030, 2031)).isEqualByComparingTo("580");
    assertThat(TargetMath.progressPct(base, target, d("790"))).isEqualByComparingTo("50.00");
    assertThat(TargetMath.status(target, d("790"), d("780"))).isEqualTo("on_track");
    assertThat(TargetMath.status(target, d("790"), d("800"))).isEqualTo("off_track");
    assertThat(TargetMath.status(target, d("790"), d("570"))).isEqualTo("achieved");
    assertThat(TargetMath.status(target, null, null)).isEqualTo("no_data");
    assertThat(TargetMath.annualRate(base, target, 2020, 2030)).isEqualByComparingTo("0.042");
  }

  @Test
  void sbtiNearTermCheck() {
    // 42% over 10 years = 4.2 %/yr: meets the 1.5 °C scope 1+2 minimum.
    assertThat(TargetMath.sbtiFindings("absolute", "scope_1_2", d("1000"), d("580"), 2026, 2036, 2026)).isEmpty();
    // 4.1 %/yr falls short.
    assertThat(TargetMath.sbtiFindings("absolute", "scope_1_2", d("1000"), d("590"), 2026, 2036, 2026))
        .containsExactly("Annual linear reduction is 4.10%; at least 4.2% is needed");
  }

  @Test
  void sbtiNearTermCheckFlagsWeakAndOldTargets() {
    assertThat(TargetMath.sbtiFindings("absolute", "scope_1_2", d("1000"), d("900"), 2010, 2045, 2026))
        .anyMatch(f -> f.startsWith("Base year must be 2015"))
        .anyMatch(f -> f.startsWith("Near-term targets cover"))
        .anyMatch(f -> f.startsWith("Annual linear reduction is 0.29%; at least 4.2%"));
    assertThat(TargetMath.sbtiFindings("absolute", "scope_3", d("1000"), d("750"), 2025, 2035, 2027)).isEmpty();
    assertThat(TargetMath.sbtiFindings("intensity", "scope_1", d("10"), d("5"), 2025, 2032, 2026))
        .anyMatch(f -> f.contains("sector pathway"));
  }
}
