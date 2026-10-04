package com.esg.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** S1: the MFA dev bypass is off by default, and can't be switched on in staging or prod. */
class MfaPolicyTest {

  private static UserAccess access(boolean mfaRequired) {
    return new UserAccess(UUID.randomUUID(), UUID.randomUUID(), true, Set.of("ESG_DIRECTOR"), Set.of(), true, Set.of(),
        Set.of(), mfaRequired, false);
  }

  @Test
  void enforcedByDefaultForMfaRolesOnly() {
    MfaPolicy policy = new MfaPolicy(false, new MockEnvironment());
    assertThat(policy.required(access(true))).isTrue();
    assertThat(policy.required(access(false))).isFalse();
  }

  @Test
  void devBypassSkipsEnforcementLocally() {
    MockEnvironment local = new MockEnvironment();
    local.setActiveProfiles("local");
    MfaPolicy policy = new MfaPolicy(true, local);
    assertThat(policy.devBypass()).isTrue();
    assertThat(policy.required(access(true))).isFalse();
  }

  @Test
  void devBypassRefusedInStagingAndProd() {
    for (String profile : new String[] {"prod", "staging"}) {
      MockEnvironment env = new MockEnvironment();
      env.setActiveProfiles(profile);
      assertThatThrownBy(() -> new MfaPolicy(true, env)).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("dev-bypass");
    }
  }
}
