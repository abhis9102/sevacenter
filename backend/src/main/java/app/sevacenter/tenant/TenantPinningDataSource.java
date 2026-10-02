package app.sevacenter.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Pins the Postgres RLS tenant on every connection checkout: {@code app.tenant_id} is set from
 * {@link TenantContext}, or to {@code ''} (= no tenant, policies match nothing) when none is set.
 *
 * <p>Every borrow overwrites whatever the previous user of the pooled connection left behind,
 * so a stale tenant can never carry over. It covers every access path (JPA, Spring Data
 * repositories, JdbcTemplate), unlike the former AOP aspect, which only fired for our own
 * {@code @Transactional} classes and silently skipped repository calls (found by
 * TenantIsolationTest).
 *
 * <p>The tenant must be in {@link TenantContext} before the connection is borrowed, which is
 * the start of the transaction. {@link TenantResolutionFilter} sets it at the start of the request.
 */
public class TenantPinningDataSource extends DelegatingDataSource {

    private static final String PIN = "select set_config('app.tenant_id', ?, false)";

    public TenantPinningDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return pin(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return pin(super.getConnection(username, password));
    }

    private static Connection pin(Connection connection) throws SQLException {
        Long tenantId = TenantContext.get();
        try (PreparedStatement statement = connection.prepareStatement(PIN)) {
            statement.setString(1, tenantId == null ? "" : tenantId.toString());
            statement.execute();
            return connection;
        } catch (SQLException | RuntimeException e) {
            connection.close(); // never hand out a connection in an unknown tenant state
            throw e;
        }
    }
}
