package com.esg.support;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared PostgreSQL for integration tests (WP-11; generalised by the WP-18 harness).
 *
 * <p>Uses {@code ESG_TEST_DB_URL} when set (a dedicated database, e.g. created by
 * {@code ./scripts/dev.sh testdb}); otherwise starts a {@code postgres:17} Testcontainer. With neither
 * available the tests are skipped, not failed. CI always has Docker.
 *
 * <p>The schema is rebuilt from the Flyway migrations once per JVM and the RLS fixture is loaded. Tests
 * connect as the non-owner application role so Row-Level Security applies, exactly as in production.
 */
public final class PostgresTestDatabase {

  public static final String TENANT_A = "11111111-1111-4111-8111-111111111111";
  public static final String TENANT_B = "22222222-2222-4222-8222-222222222222";
  public static final String OWNER = "esg_owner";
  public static final String APP = "esg_app";

  private static final String[] SCHEMAS = {
    "auth", "data_hub", "carbon", "compliance", "reporting", "risk", "supply_chain", "social",
    "governance", "analytics", "investor", "carbon_market", "training", "audit", "files", "notifications", "reference"
  };

  private static String url;
  private static String ownerPassword;
  private static String appPassword;
  private static PostgreSQLContainer<?> container;
  private static RuntimeException failure;

  private PostgresTestDatabase() {}

  /** Starts (once) and returns the JDBC URL; skips the calling test if no database is available. */
  public static synchronized String url() {
    if (failure != null) {
      // Every later test class fails with the original cause instead of running against a half-built database.
      throw new IllegalStateException("Test database setup failed earlier", failure);
    }
    if (url == null) {
      try {
        start();
      } catch (org.opentest4j.TestAbortedException e) {
        url = null; // no database available: skip, and let the next class check again
        throw e;
      } catch (RuntimeException e) {
        url = null;
        failure = e;
        throw e;
      }
    }
    return url;
  }

  public static String ownerPassword() {
    url();
    return ownerPassword;
  }

  public static String appPassword() {
    url();
    return appPassword;
  }

  public static Connection ownerConnection() throws SQLException {
    return DriverManager.getConnection(url(), OWNER, ownerPassword());
  }

  public static Connection appConnection() throws SQLException {
    return DriverManager.getConnection(url(), APP, appPassword());
  }

  private static void start() {
    String externalUrl = System.getenv("ESG_TEST_DB_URL");
    if (externalUrl != null && !externalUrl.isBlank()) {
      url = externalUrl;
      ownerPassword = envOr("ESG_TEST_DB_OWNER_PASSWORD", "esg_owner_local");
      appPassword = envOr("ESG_TEST_DB_APP_PASSWORD", "esg_app_local");
    } else {
      Assumptions.assumeTrue(dockerAvailable(),
          "No ESG_TEST_DB_URL and no Docker: skipping database tests (run ./scripts/dev.sh testdb)");
      container = new PostgreSQLContainer<>("postgres:17").withDatabaseName("esg_test");
      container.start();
      ownerPassword = "owner";
      appPassword = "app";
      try (Connection su = DriverManager.getConnection(
              container.getJdbcUrl(), container.getUsername(), container.getPassword());
          Statement st = su.createStatement()) {
        st.execute("CREATE ROLE " + OWNER + " LOGIN PASSWORD '" + ownerPassword + "'");
        st.execute("CREATE ROLE " + APP + " LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '" + appPassword + "'");
        st.execute("ALTER DATABASE esg_test OWNER TO " + OWNER);
        st.execute("ALTER SCHEMA public OWNER TO " + OWNER);
      } catch (SQLException e) {
        throw new IllegalStateException("Could not prepare test container roles", e);
      }
      url = container.getJdbcUrl();
    }
    rebuildSchema();
  }

  private static void rebuildSchema() {
    try (Connection owner = ownerConnection(); Statement st = owner.createStatement()) {
      for (String schema : SCHEMAS) {
        st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
      st.execute("DROP TABLE IF EXISTS public.flyway_schema_history");
    } catch (SQLException e) {
      throw new IllegalStateException("Could not reset test schema", e);
    }
    Flyway.configure()
        .dataSource(url, OWNER, ownerPassword)
        .locations("classpath:db/migration")
        .placeholders(java.util.Map.of("app_role", APP))
        .load()
        .migrate();
    try (Connection owner = ownerConnection(); Statement st = owner.createStatement()) {
      String fixture = new ClassPathResource("rls/fixture.sql").getContentAsString(StandardCharsets.UTF_8);
      st.execute(fixture);
    } catch (Exception e) {
      throw new IllegalStateException("Could not load RLS fixture", e);
    }
  }

  private static boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable e) {
      return false;
    }
  }

  private static String envOr(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
