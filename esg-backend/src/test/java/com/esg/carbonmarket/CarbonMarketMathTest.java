package com.esg.carbonmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.esg.carbonmarket.CarbonMarketMath.Range;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden values for the WP-41 carbon market arithmetic (computed by hand; D36). */
class CarbonMarketMathTest {

  private static BigDecimal d(String v) {
    return new BigDecimal(v);
  }

  @Test
  void theNextFreeSerialsAreTheFirstGapLargeEnough() {
    List<Range> used = List.of(new Range(201, 300), new Range(1, 100));    // order doesn't matter
    assertThat(CarbonMarketMath.nextFree(1, 1000, used, 100)).isEqualTo(new Range(101, 200));
    // 101 don't fit between 100 and 201, so the block starts after 300.
    assertThat(CarbonMarketMath.nextFree(1, 1000, used, 101)).isEqualTo(new Range(301, 401));
    assertThat(CarbonMarketMath.nextFree(1, 1000, used, 701)).isNull();      // 700 left after 300
    assertThat(CarbonMarketMath.nextFree(1, 100, List.of(new Range(1, 100)), 1)).isNull();
    assertThat(CarbonMarketMath.nextFree(5001, 5100, List.of(), 100)).isEqualTo(new Range(5001, 5100));
    assertThat(CarbonMarketMath.free(1, 1000, used)).isEqualTo(800);
  }

  @Test
  void shareRatioAndNet() {
    assertThat(CarbonMarketMath.share(d("250"), d("1000"))).isEqualByComparingTo("25.0");
    assertThat(CarbonMarketMath.share(d("1"), d("3"))).isEqualByComparingTo("33.3");
    assertThat(CarbonMarketMath.share(d("250"), d("0"))).isNull();
    assertThat(CarbonMarketMath.offsetToReduction(d("250"), d("500"))).isEqualByComparingTo("0.50");
    assertThat(CarbonMarketMath.offsetToReduction(d("250"), d("0"))).isNull();
    assertThat(CarbonMarketMath.offsetToReduction(d("250"), d("-10"))).isNull();
    assertThat(CarbonMarketMath.net(d("1000.000"), d("250"))).isEqualByComparingTo("750");
    assertThat(CarbonMarketMath.net(d("100.000"), d("250"))).isEqualByComparingTo("0");
  }

  @Test
  void overRelianceWhenCreditsOutweighReductionsOrPassTheCeiling() {
    assertThat(CarbonMarketMath.overReliance(d("50"), d("500"), d("5.0"), d("10"))).isFalse();
    assertThat(CarbonMarketMath.overReliance(d("250"), d("500"), d("25.0"), d("10"))).as("25% > 10% ceiling").isTrue();
    assertThat(CarbonMarketMath.overReliance(d("600"), d("500"), d("6.0"), d("10"))).as("more offset than reduced").isTrue();
    assertThat(CarbonMarketMath.overReliance(d("10"), null, d("1.0"), d("10"))).as("no reductions at all").isTrue();
    assertThat(CarbonMarketMath.overReliance(d("10"), d("-5"), d("1.0"), d("10"))).as("emissions rose").isTrue();
    assertThat(CarbonMarketMath.overReliance(d("0"), null, null, d("10"))).isFalse();
  }
}
