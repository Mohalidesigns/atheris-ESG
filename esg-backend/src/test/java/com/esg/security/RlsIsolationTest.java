package com.esg.security;

import static com.esg.support.PostgresTestDatabase.TENANT_A;
import static com.esg.support.PostgresTestDatabase.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.esg.support.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Row-Level Security proof for every tenant-owned table (WP-11, TRD §4.1, §11.2.1).
 *
 * <p>The table list comes from the PostgreSQL catalogue, not from this file, so a new table with a
 * {@code tenant_id} column is tested automatically and fails until it has RLS and a fixture row.
 * All checks run as the non-owner application role, as in production.
 */
class RlsIsolationTest {

  private static final String BUSINESS_SCHEMAS =
      "'auth','data_hub','carbon','compliance','reporting','risk','supply_chain','social',"
          + "'governance','analytics','investor','carbon_market','training','audit','files','notifications'";

  /** Tables with a tenant_id column, excluding partitions (covered through their parent) and the login lookup. */
  private static List<String> tenantTables() throws SQLException {
    String sql = "select c.oid::regclass::text from pg_class c "
        + "join pg_namespace n on n.oid = c.relnamespace "
        + "join pg_attribute a on a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped "
        + "where c.relkind in ('r','p') and not c.relispartition and n.nspname in (" + BUSINESS_SCHEMAS + ") "
        + "and c.oid::regclass::text <> 'auth.login_identities' order by 1";
    List<String> tables = new ArrayList<>();
    try (Connection owner = PostgresTestDatabase.ownerConnection();
        Statement st = owner.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      while (rs.next()) {
        tables.add(rs.getString(1));
      }
    }
    return tables;
  }

  @TestFactory
  Stream<DynamicTest> everyTenantTableIsolatesTenants() throws SQLException {
    List<String> tables = tenantTables();
    assertThat(tables).as("tenant tables found in catalogue").hasSizeGreaterThanOrEqualTo(31);
    return tables.stream().map(table -> dynamicTest(table, () -> assertIsolated(table)));
  }

  /** Tables restricted beyond the tenant (V027): readable only by investigators, so the check reads them as one. */
  private static final java.util.Set<String> INVESTIGATOR_ONLY = java.util.Set.of("governance.whistleblower_reports",
      "governance.whistleblower_case_notes");

  private void assertIsolated(String table) throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection()) {
      if (INVESTIGATOR_ONLY.contains(table)) {
        try (PreparedStatement ps = app.prepareStatement("select set_config('app.current_user', ?, false)")) {
          ps.setString(1, com.esg.support.ApiTestSupport.roleUserId("ETHICS_OFFICER"));
          ps.execute();
        }
      }
      setTenant(app, TENANT_A);
      assertThat(count(app, "select count(*) from " + table + " where tenant_id = '" + TENANT_A + "'"))
          .as("%s needs a tenant A row in src/test/resources/rls/fixture.sql", table)
          .isPositive();

      setTenant(app, TENANT_B);
      assertThat(count(app, "select count(*) from " + table + " where tenant_id = '" + TENANT_A + "'"))
          .as("tenant B must not SELECT tenant A rows in %s", table)
          .isZero();

      if (hasPrivilege(app, table, "UPDATE")) {
        assertThat(update(app, "update " + table + " set tenant_id = tenant_id where tenant_id = '" + TENANT_A + "'"))
            .as("tenant B must not UPDATE tenant A rows in %s", table)
            .isZero();

        // WITH CHECK: tenant A can't move its own row into tenant B.
        setTenant(app, TENANT_A);
        app.setAutoCommit(false);
        try {
          assertThatThrownBy(() -> update(app,
              "update " + table + " set tenant_id = '" + TENANT_B + "' where tenant_id = '" + TENANT_A + "'"))
              .as("moving a row to another tenant must be rejected in %s", table)
              .isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isIn("42501", "23503"));
        } finally {
          app.rollback();
          app.setAutoCommit(true);
        }
        setTenant(app, TENANT_B);
      }

      if (hasPrivilege(app, table, "DELETE")) {
        assertThat(update(app, "delete from " + table + " where tenant_id = '" + TENANT_A + "'"))
            .as("tenant B must not DELETE tenant A rows in %s", table)
            .isZero();
      }
    }
  }

  @Test
  void rlsIsEnabledAndForcedWithAPolicyOnEveryTenantTableAndPartition() throws SQLException {
    String sql = "select c.oid::regclass::text, c.relrowsecurity, c.relforcerowsecurity, "
        + "(select count(*) from pg_policy p where p.polrelid = c.oid) "
        + "from pg_class c join pg_namespace n on n.oid = c.relnamespace "
        + "join pg_attribute a on a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped "
        + "where c.relkind in ('r','p') and n.nspname in (" + BUSINESS_SCHEMAS + ") "
        + "and c.oid::regclass::text <> 'auth.login_identities'";
    List<String> problems = new ArrayList<>();
    try (Connection owner = PostgresTestDatabase.ownerConnection();
        Statement st = owner.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      while (rs.next()) {
        if (!rs.getBoolean(2) || !rs.getBoolean(3) || rs.getLong(4) == 0) {
          problems.add(rs.getString(1));
        }
      }
    }
    assertThat(problems).as("tables missing ENABLE/FORCE RLS or a policy").isEmpty();
  }

  @Test
  void applicationRoleCannotBypassRls() throws SQLException {
    try (Connection owner = PostgresTestDatabase.ownerConnection();
        Statement st = owner.createStatement()) {
      ResultSet role = st.executeQuery("select rolsuper, rolbypassrls from pg_roles where rolname = 'esg_app'");
      assertThat(role.next()).isTrue();
      assertThat(role.getBoolean(1)).as("esg_app is superuser").isFalse();
      assertThat(role.getBoolean(2)).as("esg_app has BYPASSRLS").isFalse();
      ResultSet owned = st.executeQuery("select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace "
          + "where pg_get_userbyid(c.relowner) = 'esg_app' and n.nspname in (" + BUSINESS_SCHEMAS + ")");
      owned.next();
      assertThat(owned.getLong(1)).as("tables owned by esg_app").isZero();
    }
  }

  @Test
  void withoutTenantContextNothingIsVisible() throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection()) {
      setTenant(app, "");
      assertThat(count(app, "select count(*) from auth.users")).isZero();
      assertThat(count(app, "select count(*) from data_hub.data_points")).isZero();
      assertThat(count(app, "select count(*) from auth.tenants")).isZero();
    }
  }

  @Test
  void tenantSeesOnlyItsOwnTenantRow() throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection()) {
      setTenant(app, TENANT_B);
      assertThat(count(app, "select count(*) from auth.tenants")).isEqualTo(1);
      assertThat(count(app, "select count(*) from auth.tenants where id = '" + TENANT_A + "'")).isZero();
    }
  }

  @Test
  void auditLogIsAppendOnly() throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection()) {
      setTenant(app, TENANT_A);
      assertThat(update(app, "insert into audit.audit_logs (action, resource) values ('test', 'rls')")).isEqualTo(1);
      assertThatThrownBy(() -> update(app, "update audit.audit_logs set action = 'tampered'"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
      assertThatThrownBy(() -> update(app, "delete from audit.audit_logs"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
    }
  }

  @Test
  void globalReferenceDataIsReadOnlyForTheApplication() throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection()) {
      setTenant(app, TENANT_A);
      assertThat(count(app, "select count(*) from auth.permissions")).isPositive();
      assertThat(count(app, "select count(*) from compliance.regulatory_frameworks")).isEqualTo(12);
      assertThat(count(app, "select count(*) from reference.metrics")).isPositive();
      for (String write : new String[] {
          "insert into auth.permissions (resource, action) values ('x', 'y')",
          "update reference.metrics set unit = 'x'",
          "delete from reference.currencies",
          "update compliance.framework_requirements set title = 'x'",
          "delete from compliance.requirement_mappings"}) {
        assertThatThrownBy(() -> update(app, write)).as(write)
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
      }
    }
  }

  @Test
  void emissionFactorsShowGlobalPlusOwnCustomOnly() throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection()) {
      setTenant(app, TENANT_B);
      long global = count(app, "select count(*) from carbon.emission_factors where tenant_id is null");
      assertThat(global).as("platform factors are loaded").isGreaterThanOrEqualTo(15);
      assertThat(count(app, "select count(*) from carbon.emission_factors where tenant_id is not null"))
          .as("tenant B has no custom factors and sees no one else's").isZero();
      setTenant(app, TENANT_A);
      assertThat(count(app, "select count(*) from carbon.emission_factors where tenant_id is null")).isEqualTo(global);
      assertThat(count(app, "select count(*) from carbon.emission_factors where tenant_id is not null"))
          .as("own custom factors only")
          .isPositive()
          .isEqualTo(count(app, "select count(*) from carbon.emission_factors where tenant_id = '" + TENANT_A + "'"));
      assertThatThrownBy(() -> update(app, "insert into carbon.emission_factors (region, category, factor_value, unit, source, valid_from) "
          + "values ('NG', 'fake_global', 1, 'kg', 'test', '2026-01-01')"))
          .as("the application must not create global factors")
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
    }
  }

  @Test
  void loginLookupWorksWithoutTenantButTableIsNotReadable() throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection()) {
      setTenant(app, "");
      try (PreparedStatement ps = app.prepareStatement("select tenant_id from auth.find_login_identity(?)")) {
        ps.setString(1, "USER.A@fixture.test");
        ResultSet rs = ps.executeQuery();
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo(TENANT_A);
      }
      assertThatThrownBy(() -> count(app, "select count(*) from auth.login_identities"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
    }
  }

  @Test
  void rowCannotReferenceAnotherTenantsOrganisation() throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection()) {
      setTenant(app, TENANT_B);
      // Tenant A's organisation id, used from tenant B: blocked by the composite (tenant_id, org_id) FK
      // even though PostgreSQL FK checks ignore RLS.
      assertThatThrownBy(() -> update(app, "insert into social.safety_incidents (org_id, incident_type, severity, occurred_at) "
          + "values ('a0000000-0000-4000-8000-00000000000a', 'slip', 'minor', now())"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23503"));
    }
  }

  @Test
  void organisationCannotHaveAnotherTenantsParent() throws SQLException {
    try (Connection app = PostgresTestDatabase.appConnection()) {
      setTenant(app, TENANT_B);
      assertThatThrownBy(() -> update(app, "insert into auth.organisations (name, parent_org_id) "
          + "values ('B child of A', 'a0000000-0000-4000-8000-00000000000a')"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23503"));
    }
  }

  // ---------------------------------------------------------------- helpers

  private static void setTenant(Connection app, String tenantId) throws SQLException {
    try (PreparedStatement ps = app.prepareStatement("select set_config('app.current_tenant', ?, false)")) {
      ps.setString(1, tenantId);
      ps.execute();
    }
  }

  private static long count(Connection app, String sql) throws SQLException {
    try (Statement st = app.createStatement(); ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static int update(Connection app, String sql) throws SQLException {
    try (Statement st = app.createStatement()) {
      return st.executeUpdate(sql);
    }
  }

  private static boolean hasPrivilege(Connection app, String table, String privilege) throws SQLException {
    try (PreparedStatement ps = app.prepareStatement("select has_table_privilege(?, ?)")) {
      ps.setString(1, table);
      ps.setString(2, privilege);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getBoolean(1);
    }
  }
}
