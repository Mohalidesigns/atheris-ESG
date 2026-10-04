package com.esg.security;

import static com.esg.support.PostgresTestDatabase.TENANT_A;
import static com.esg.support.PostgresTestDatabase.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esg.model.dto.AuthResponse;
import com.esg.model.dto.LoginRequest;
import com.esg.model.entity.User;
import com.esg.repository.DataPointRepository;
import com.esg.repository.UserRepository;
import com.esg.service.AuthService;
import com.esg.support.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end tenant context: JWT-style TenantContext → TenantAwareDataSource → RLS (WP-11).
 * The pool has a single connection, so any tenant left on a pooled connection would show up here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TenantContextIntegrationTest {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresTestDatabase::url);
    registry.add("spring.datasource.username", () -> PostgresTestDatabase.APP);
    registry.add("spring.datasource.password", PostgresTestDatabase::appPassword);
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> 1);
    registry.add("spring.flyway.enabled", () -> false); // PostgresTestDatabase already migrated as the owner
  }

  @Autowired private DataPointRepository dataPointRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AuthService authService;
  @Autowired private JwtTokenProvider tokens;

  private static final AuthService.ClientInfo CLIENT = new AuthService.ClientInfo("junit", "127.0.0.1");

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void pooledConnectionNeverLeaksATenant() {
    for (int i = 0; i < 5; i++) {
      TenantContext.setTenantId(TENANT_A);
      assertThat(dataPointRepository.count()).as("tenant A sees its data points").isPositive();
      assertThat(userRepository.findAll()).extracting(User::getEmail)
          .contains("user.a@fixture.test").doesNotContain("user.b@fixture.test");

      TenantContext.setTenantId(TENANT_B);
      assertThat(dataPointRepository.count()).as("tenant B sees none of A's data points").isZero();
      assertThat(userRepository.findAll()).extracting(User::getEmail)
          .contains("user.b@fixture.test").doesNotContain("user.a@fixture.test");

      TenantContext.clear();
      assertThat(dataPointRepository.count()).as("no tenant sees nothing").isZero();
    }
  }

  @Test
  void repositoryLookupByIdCannotCrossTenants() {
    TenantContext.setTenantId(TENANT_A);
    UUID aUser = UUID.fromString("a0000000-0000-4000-8000-0000000000a1");
    TenantContext.setTenantId(TENANT_B);
    assertThat(userRepository.findById(aUser)).isEmpty();
  }

  @Test
  void loginResolvesTheTenantAndIssuesTokensForIt() {
    LoginRequest request = new LoginRequest();
    request.setEmail("user.b@fixture.test");
    request.setPassword("Fixture!2026");

    // user.b is TENANT_ADMIN, which requires MFA: the password step yields an MFA set-up challenge for tenant B.
    AuthResponse response = authService.login(request, CLIENT).body();

    assertThat(response.getMfaSetupRequired()).isTrue();
    assertThat(tokens.parseMfaToken(response.getMfaToken()).orElseThrow().get("tenant_id", String.class))
        .isEqualTo(TENANT_B);
  }

  @Test
  void failedLoginsPersistAndLockTheAccount() throws Exception {
    LoginRequest wrong = new LoginRequest();
    wrong.setEmail("user.a@fixture.test");
    wrong.setPassword("wrong-password");
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> authService.login(wrong, CLIENT)).hasMessage("Invalid email or password");
      TenantContext.clear();
    }

    LoginRequest right = new LoginRequest();
    right.setEmail("user.a@fixture.test");
    right.setPassword("Fixture!2026");
    try {
      // Locked accounts get the same generic message as wrong passwords (no account enumeration).
      assertThatThrownBy(() -> authService.login(right, CLIENT)).hasMessage("Invalid email or password");
    } finally {
      try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
        st.execute("select set_config('app.current_tenant', '" + TENANT_A + "', false)");
        st.execute("update auth.users set failed_login_count = 0, locked_until = null where email = 'user.a@fixture.test'");
      }
    }
  }
}
