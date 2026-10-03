package com.esg.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** WP-52: staging/prod refuse development secrets and an unauthenticated AI service (D40 (6)). */
class ProductionSecretsPolicyTest {

  private static MockEnvironment env(String profile, Map<String, String> props) {
    MockEnvironment e = new MockEnvironment();
    e.setActiveProfiles(profile);
    props.forEach(e::setProperty);
    return e;
  }

  @Test
  void productionRefusesDevelopmentSecrets() {
    assertThatThrownBy(() -> new ProductionSecretsPolicy(env("prod", Map.of("esg.security.jwt-secret",
        "local-dev-only-signing-key-not-for-any-shared-environment-0123456789abcdef", "esg.ai.url", "http://ai:8090"))))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("esg.security.jwt-secret is a development value")
        .hasMessageContaining("esg.ai.token must be set");
    assertThatThrownBy(() -> new ProductionSecretsPolicy(env("staging", Map.of("spring.datasource.password", "esg_app_local"))))
        .hasMessageContaining("spring.datasource.password");
  }

  @Test
  void realSecretsAndLocalProfilesStart() {
    // Stand-ins for production values: long enough and not a committed development value.
    String jwt = "prod-signing-key-" + "x".repeat(40);
    String token = "prod-ai-token-" + "y".repeat(24);
    new ProductionSecretsPolicy(env("prod", Map.of("esg.security.jwt-secret", jwt, "esg.ai.url", "http://ai:8090", "esg.ai.token", token)));
    new ProductionSecretsPolicy(env("local", Map.of("esg.security.jwt-secret", "local-dev-only")));
    assertThat(ProductionSecretsPolicy.problems(name -> "")).isEmpty();
  }
}
