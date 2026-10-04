package com.esg.config;

import com.esg.security.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Copies the request's tenant, user and client (IP, user agent) into the PostgreSQL session every time a connection is
 * checked out of the pool, so the Row-Level Security policies (V008) see the right tenant.
 *
 * <p>The values are set on <em>every</em> checkout, including to an empty string when there is no
 * tenant, so a pooled connection can never carry a previous request's tenant. That makes the setting
 * effectively transaction-scoped (one checkout per transaction) and also covers non-transactional
 * reads, which {@code SET LOCAL} alone would not. An empty tenant matches no rows.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String SET_CONTEXT =
            "select set_config('app.current_tenant', ?, false), set_config('app.current_user', ?, false), "
                    + "set_config('app.client_ip', ?, false), set_config('app.user_agent', ?, false), set_config('app.test_run', ?, false)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyContext(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyContext(super.getConnection(username, password));
    }

    private Connection applyContext(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_CONTEXT)) {
            statement.setString(1, valueOrEmpty(TenantContext.getTenantId()));
            statement.setString(2, valueOrEmpty(TenantContext.getUserId()));
            statement.setString(3, valueOrEmpty(TenantContext.getClientIp()));
            statement.setString(4, valueOrEmpty(TenantContext.getUserAgent()));
            statement.setString(5, valueOrEmpty(TenantContext.getTestRun()));
            statement.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
