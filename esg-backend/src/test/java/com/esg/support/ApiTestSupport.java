package com.esg.support;

import com.esg.security.JwtTokenProvider;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for HTTP-level tests: full Spring context with MockMvc against the RLS test database
 * (PostgresTestDatabase), connected as the non-owner application role.
 *
 * <p>Tokens are minted directly for fixture users; the JWT filter reloads permissions from the database on
 * every request, so the token's own role/permission claims don't matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

  public static final String ORG_HQ = "a0000000-0000-4000-8000-00000000000a";
  public static final String ORG_SITE_1 = "a0000000-0000-4000-8000-0000000001a1";
  public static final String ORG_SITE_2 = "a0000000-0000-4000-8000-0000000002a2";
  public static final String HQ_DATA_POINT = "a0000000-0000-4000-8000-0000000000dd";
  public static final String USER_A = "a0000000-0000-4000-8000-0000000000a1";
  public static final String USER_B_ADMIN = "b0000000-0000-4000-8000-0000000000b1";

  /** Fixture role users in tenant A (fixture.sql): role name → user id. */
  public static final List<String> ROLES = List.of("SUPER_ADMIN", "TENANT_ADMIN", "ESG_DIRECTOR", "ESG_MANAGER",
      "ESG_ANALYST", "DATA_COLLECTOR", "AUDITOR", "INVESTOR_VIEWER", "CONSULTANT", "BOARD_MEMBER",
      "COMPLIANCE_OFFICER", "TRAINING_ADMIN");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresTestDatabase::url);
    registry.add("spring.datasource.username", () -> PostgresTestDatabase.APP);
    registry.add("spring.datasource.password", PostgresTestDatabase::appPassword);
    registry.add("spring.flyway.enabled", () -> false);
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected JwtTokenProvider tokens;

  public static String roleUserId(String role) {
    return String.format("a1000000-0000-4000-8000-0000000000%02d", ROLES.indexOf(role) + 1);
  }

  protected String bearerForRole(String role) {
    return bearer(roleUserId(role), PostgresTestDatabase.TENANT_A);
  }

  /** A token from an MFA-verified session (users with MFA-required roles need one; S1). */
  protected String bearer(String userId, String tenantId) {
    return "Bearer " + tokens.generateAccessToken(userId, tenantId, List.of(), List.of(), true);
  }

  /** A token from a password-only session (no TOTP step). */
  protected String bearerWithoutMfa(String userId, String tenantId) {
    return "Bearer " + tokens.generateAccessToken(userId, tenantId, List.of(), List.of(), false);
  }

  /** Runs SQL as the schema owner inside tenant A (for test setup and clean-up). */
  protected static void asOwnerInTenantA(String... statements) throws Exception {
    try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement st = owner.createStatement()) {
      st.execute("select set_config('app.current_tenant', '" + PostgresTestDatabase.TENANT_A + "', false)");
      for (String sql : statements) {
        st.execute(sql);
      }
    }
  }
}
