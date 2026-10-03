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
    new ProductionSecretsPolicy(env("prod", Map.of("esg.security.jwt-secret", "q7Zr0n8w5mKfX2pL9sVb3cYt6hJd1gNe4aUo", "esg.ai.url", "http://ai:8090",
        "esg.ai.token", "f3d9a7c1e5b2084d6a9c3e7b1f5d2a8c")));
    new ProductionSecretsPolicy(env("local", Map.of("esg.security.jwt-secret", "local-dev-only")));
    assertThat(ProductionSecretsPolicy.problems(name -> "")).isEmpty();
  }
}
