package com.esg.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final String SECRET =
      "unit-test-signing-key-0123456789abcdef0123456789abcdef0123456789";

  private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000, 120_000);

  @Test
  void accessTokenRoundTripsClaims() {
    String token = provider.generateAccessToken(
        "u-1", "t-1", "ESG_ANALYST", List.of("data_points:read"));

    Claims claims = provider.parseToken(token);

    assertThat(claims.getSubject()).isEqualTo("u-1");
    assertThat(claims.get("tenant_id", String.class)).isEqualTo("t-1");
    assertThat(claims.get("role", String.class)).isEqualTo("ESG_ANALYST");
    assertThat(claims.get("permissions", List.class)).containsExactly("data_points:read");
  }

  @Test
  void tokenSignedWithAnotherKeyIsRejected() {
    JwtTokenProvider other = new JwtTokenProvider(
        "another-signing-key-0123456789abcdef0123456789abcdef0123456789ab", 60_000, 120_000);
    String foreign = other.generateAccessToken("u-1", "t-1", "AUDITOR", List.of());

    assertThat(provider.validateToken(foreign)).isFalse();
  }

  @Test
  void missingSecretFailsFast() {
    assertThatThrownBy(() -> new JwtTokenProvider(null, 60_000, 120_000))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void shortSecretFailsFast() {
    assertThatThrownBy(() -> new JwtTokenProvider("too-short", 60_000, 120_000))
        .isInstanceOf(IllegalStateException.class);
  }
}
