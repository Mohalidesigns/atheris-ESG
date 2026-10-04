package com.esg.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

/** Phase 3 (7): test-data marking is local/test only and accepts only a well-formed run name. */
class TestDataPolicyTest {

  private static MockHttpServletRequest request(String run) {
    MockHttpServletRequest r = new MockHttpServletRequest();
    if (run != null) {
      r.addHeader(TestDataPolicy.HEADER, run);
    }
    return r;
  }

  @Test
  void offByDefaultTheHeaderIsIgnored() {
    TestDataPolicy off = new TestDataPolicy(false, new MockEnvironment());
    assertThat(off.runOf(request("e2e-1"))).isNull();
  }

  @Test
  void onItAcceptsOnlyAWellFormedRunName() {
    TestDataPolicy on = new TestDataPolicy(true, new MockEnvironment());
    assertThat(on.runOf(request("e2e-1696_abc.2"))).isEqualTo("e2e-1696_abc.2");
    assertThat(on.runOf(request("bad value; drop"))).isNull();
    assertThat(on.runOf(request("x".repeat(65)))).isNull();
    assertThat(on.runOf(request(null))).isNull();
  }

  @Test
  void stagingAndProdRefuseToStartWithIt() {
    MockEnvironment prod = new MockEnvironment();
    prod.setActiveProfiles("prod");
    assertThatThrownBy(() -> new TestDataPolicy(true, prod)).isInstanceOf(IllegalStateException.class);
    MockEnvironment staging = new MockEnvironment();
    staging.setActiveProfiles("staging");
    assertThatThrownBy(() -> new TestDataPolicy(true, staging)).isInstanceOf(IllegalStateException.class);
    assertThat(new TestDataPolicy(false, prod).enabled()).isFalse();
  }
}
