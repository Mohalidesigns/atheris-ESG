package com.esg.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Unit tests for TOTP (RFC 6238 vectors), secret encryption and the password policy. */
class SecurityPrimitivesTest {

  private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  @Test
  void totpMatchesRfc6238TestVectors() {
    TotpService totp = new TotpService();
    // RFC 6238 Appendix B (SHA1), last 6 of the 8-digit values.
    assertThat(totp.codeAt(RFC_SECRET, 59 / 30)).isEqualTo("287082");
    assertThat(totp.codeAt(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
    assertThat(totp.codeAt(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
    assertThat(totp.codeAt(RFC_SECRET, 2000000000L / 30)).isEqualTo("279037");
  }

  @Test
  void totpVerifyAcceptsOneStepOfDriftOnly() {
    String secret = TotpService.base32Encode(RFC_SECRET);
    TotpService at = new TotpService(Clock.fixed(Instant.ofEpochSecond(1111111109L), ZoneOffset.UTC));
    assertThat(at.verify(secret, "081804")).isTrue();
    TotpService oneStepLater = new TotpService(Clock.fixed(Instant.ofEpochSecond(1111111109L + 30), ZoneOffset.UTC));
    assertThat(oneStepLater.verify(secret, "081804")).isTrue();
    TotpService twoStepsLater = new TotpService(Clock.fixed(Instant.ofEpochSecond(1111111109L + 61), ZoneOffset.UTC));
    assertThat(twoStepsLater.verify(secret, "081804")).isFalse();
    assertThat(at.verify(secret, "12345")).isFalse();
    assertThat(at.verify(secret, "abcdef")).isFalse();
  }

  @Test
  void base32RoundTrips() {
    String encoded = TotpService.base32Encode(RFC_SECRET);
    assertThat(encoded).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    assertThat(TotpService.base32Decode(encoded)).isEqualTo(RFC_SECRET);
  }

  @Test
  void secretEncryptorRoundTripsAndUsesRandomIv() {
    SecretEncryptor encryptor = new SecretEncryptor(Base64.getEncoder().encodeToString(new byte[32]));
    String a = encryptor.encrypt("JBSWY3DPEHPK3PXP");
    String b = encryptor.encrypt("JBSWY3DPEHPK3PXP");
    assertThat(a).isNotEqualTo(b).doesNotContain("JBSWY3DPEHPK3PXP");
    assertThat(encryptor.decrypt(a)).isEqualTo("JBSWY3DPEHPK3PXP");
  }

  @Test
  void secretEncryptorRejectsWrongKeySizeAndTampering() {
    assertThatThrownBy(() -> new SecretEncryptor(Base64.getEncoder().encodeToString(new byte[16])))
        .isInstanceOf(IllegalStateException.class);
    SecretEncryptor encryptor = new SecretEncryptor(Base64.getEncoder().encodeToString(new byte[32]));
    byte[] raw = Base64.getDecoder().decode(encryptor.encrypt("secret"));
    raw[raw.length - 1] ^= 1;
    assertThatThrownBy(() -> encryptor.decrypt(Base64.getEncoder().encodeToString(raw)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void passwordPolicy() {
    PasswordPolicy policy = new PasswordPolicy();
    assertThat(policy.violations("Str0ng!Passw0rd", "ada@example.com")).isEmpty();
    assertThat(policy.violations("Short1!", "ada@example.com")).contains("at least 12 characters");
    assertThat(policy.violations("alllowercase1!", "x@y.z")).contains("an upper-case letter");
    assertThat(policy.violations("ALLUPPERCASE1!", "x@y.z")).contains("a lower-case letter");
    assertThat(policy.violations("NoDigitsHere!!", "x@y.z")).contains("a digit");
    assertThat(policy.violations("NoSymbols12345", "x@y.z")).contains("a symbol");
    assertThat(policy.violations("Adaeze!2026Strong", "adaeze@example.com")).contains("must not contain your email name");
  }
}
