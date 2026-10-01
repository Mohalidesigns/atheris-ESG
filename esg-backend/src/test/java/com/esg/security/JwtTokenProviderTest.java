package com.esg.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final String SECRET =
      "unit-test-signing-key-0123456789abcdef0123456789abcdef0123456789";

  private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);

  @Test
  void accessTokenRoundTripsClaims() {
    String token = provider.generateAccessToken("u-1", "t-1", List.of("ESG_ANALYST"), List.of("data_points:read"));

    Claims claims = provider.parseAccessToken(token).orElseThrow();

    assertThat(claims.getSubject()).isEqualTo("u-1");
    assertThat(claims.get("tenant_id", String.class)).isEqualTo("t-1");
    assertThat(claims.get("roles", List.class)).containsExactly("ESG_ANALYST");
    assertThat(claims.get("permissions", List.class)).containsExactly("data_points:read");
  }

  @Test
  void mfaTokenIsNotAnAccessTokenAndViceVersa() {
    String mfa = provider.generateMfaToken("u-1", "t-1");
    String access = provider.generateAccessToken("u-1", "t-1", List.of(), List.of());

    assertThat(provider.parseAccessToken(mfa)).isEmpty();
    assertThat(provider.parseMfaToken(access)).isEmpty();
    assertThat(provider.parseMfaToken(mfa)).isPresent();
  }

  @Test
  void tokenSignedWithAnotherKeyIsRejected() {
    JwtTokenProvider other = new JwtTokenProvider(
        "another-signing-key-0123456789abcdef0123456789abcdef0123456789ab", 60_000);
    String foreign = other.generateAccessToken("u-1", "t-1", List.of(), List.of());

    assertThat(provider.parseAccessToken(foreign)).isEmpty();
  }

  @Test
  void garbageIsRejected() {
    assertThat(provider.parseAccessToken("not-a-jwt")).isEmpty();
    assertThat(provider.parseAccessToken("11111111-1111-4111-8111-111111111111.opaque-refresh-token")).isEmpty();
  }

  @Test
  void missingSecretFailsFast() {
    assertThatThrownBy(() -> new JwtTokenProvider(null, 60_000))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void shortSecretFailsFast() {
    assertThatThrownBy(() -> new JwtTokenProvider("too-short", 60_000))
        .isInstanceOf(IllegalStateException.class);
  }
}
