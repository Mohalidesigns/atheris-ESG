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

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void pooledConnectionNeverLeaksATenant() {
    for (int i = 0; i < 5; i++) {
      TenantContext.setTenantId(TENANT_A);
      assertThat(dataPointRepository.count()).as("tenant A sees its data point").isEqualTo(1);
      assertThat(userRepository.findAll()).extracting(User::getEmail).containsExactly("user.a@fixture.test");

      TenantContext.setTenantId(TENANT_B);
      assertThat(dataPointRepository.count()).as("tenant B sees none of A's data points").isZero();
      assertThat(userRepository.findAll()).extracting(User::getEmail).containsExactly("user.b@fixture.test");

      TenantContext.clear();
      assertThat(dataPointRepository.count()).as("no tenant sees nothing").isZero();
    }
  }

  @Test
  void repositoryLookupByIdCannotCrossTenants() {
    TenantContext.setTenantId(TENANT_A);
    UUID aUser = userRepository.findAll().get(0).getId();
    TenantContext.setTenantId(TENANT_B);
    assertThat(userRepository.findById(aUser)).isEmpty();
  }

  @Test
  void loginResolvesTheTenantAndIssuesTokensForIt() {
    LoginRequest request = new LoginRequest();
    request.setEmail("user.b@fixture.test");
    request.setPassword("Fixture!2026");

    AuthResponse response = authService.login(request);

    assertThat(response.getUser().getTenantId()).isEqualTo(TENANT_B);
    assertThat(response.getAccessToken()).isNotBlank();
  }

  @Test
  void failedLoginsPersistAndLockTheAccount() throws Exception {
    LoginRequest wrong = new LoginRequest();
    wrong.setEmail("user.a@fixture.test");
    wrong.setPassword("wrong-password");
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> authService.login(wrong)).hasMessage("Invalid email or password");
      TenantContext.clear();
    }

    LoginRequest right = new LoginRequest();
    right.setEmail("user.a@fixture.test");
    right.setPassword("Fixture!2026");
    try {
      assertThatThrownBy(() -> authService.login(right)).hasMessageContaining("locked");
    } finally {
      try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
        st.execute("select set_config('app.current_tenant', '" + TENANT_A + "', false)");
        st.execute("update auth.users set failed_login_count = 0, locked_until = null where email = 'user.a@fixture.test'");
      }
    }
  }
}
