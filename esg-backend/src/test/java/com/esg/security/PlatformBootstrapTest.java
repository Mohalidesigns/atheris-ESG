package com.esg.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** WP-54 (D42): the first platform administrator of a new installation, and never a second one this way. */
class PlatformBootstrapTest extends ApiTestSupport {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordEncoder encoder;
  @Autowired private PasswordPolicy policy;

  private PlatformBootstrap bootstrap(String email, String password) {
    return new PlatformBootstrap(jdbc, encoder, policy, email, password, "Ada", "Operator", "Atheris Operations", "NG", "NGN");
  }

  @Test
  void doesNothingWhenNotConfiguredOrWhenAPlatformAdministratorExists() {
    assertThat(bootstrap("", "").run()).isNull();
    // The fixture already has a SUPER_ADMIN, so a configured bootstrap is a no-op.
    assertThat(bootstrap("ops@example.org", "Corr3ct-Horse-Battery!").run()).isNull();
    assertThatThrownBy(() -> bootstrap("ops@example.org", "short").run()).isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("ESG_BOOTSTRAP_ADMIN_PASSWORD needs");
  }

  @Test
  void onAFreshInstallationItCreatesTheOperatorTenantAndOneSuperAdmin() throws Exception {
    try (Connection c = PostgresTestDatabase.ownerConnection(); Statement st = c.createStatement()) {
      c.setAutoCommit(false);
      try {
        // Simulate a fresh installation: no platform administrator in any tenant (rolled back below).
        for (String tenant : new String[] {PostgresTestDatabase.TENANT_A, PostgresTestDatabase.TENANT_B}) {
          st.execute("select set_config('app.current_tenant', '" + tenant + "', true)");
          st.execute("delete from auth.user_roles ur using auth.roles r where r.id = ur.role_id and r.name = 'SUPER_ADMIN'");
        }
        ResultSet r = st.executeQuery("select auth.bootstrap_platform('Atheris Operations', 'NG', 'NGN', 'Ops@Example.org', 'Ada', 'Operator', '$2a$10$hash')");
        r.next();
        String user = r.getString(1);
        assertThat(user).isNotNull();
        ResultSet t = st.executeQuery("select t.id, t.slug from auth.tenants t join auth.login_identities li on li.tenant_id = t.id "
            + "where li.email = 'ops@example.org'");
        t.next();
        assertThat(t.getString(2)).isEqualTo("platform-operator");
        String tenant = t.getString(1);
        st.execute("select set_config('app.current_tenant', '" + tenant + "', true)");
        ResultSet role = st.executeQuery("select r.name, auth.is_platform_user(ur.user_id) from auth.user_roles ur join auth.roles r on r.id = ur.role_id "
            + "where ur.user_id = '" + user + "'");
        role.next();
        assertThat(role.getString(1)).isEqualTo("SUPER_ADMIN");
        assertThat(role.getBoolean(2)).isTrue();
        // A second call finds the administrator and does nothing.
        ResultSet again = st.executeQuery("select auth.bootstrap_platform('X', 'NG', 'NGN', 'other@example.org', 'B', 'C', 'h')");
        again.next();
        assertThat(again.getString(1)).isNull();
      } finally {
        c.rollback();
      }
    }
  }
}
