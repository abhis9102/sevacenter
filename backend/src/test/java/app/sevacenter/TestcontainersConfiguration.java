package app.sevacenter;

import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Hermetic test infrastructure: tests spin up their own throwaway Postgres via
 * Testcontainers, so {@code ./mvnw verify} needs only Docker — no shared/local DB,
 * no flaky external state.
 *
 * <p>Same two-role setup as production: Flyway migrates as the owner (the container's
 * superuser), the application connects as the least-privilege {@code sevacenter_app} role.
 * The container's default user is a superuser, and superusers bypass Row-Level Security, so
 * connecting the app as that user would make every RLS test meaningless.
 *
 * <p>Connection-details beans rather than dynamic properties: with a real web server the
 * tenant filter (and so the DataSource) is created while Tomcat starts, before dynamic
 * properties apply, and the context silently fell back to application.yml's defaults.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String APP_ROLE = "sevacenter_app";
    static final String APP_ROLE_PASSWORD = "app-role-test-only";

    @Bean
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
                .withInitScript("testcontainers/app-role.sql");
    }

    /** The application's DataSource: least-privilege role, subject to RLS. */
    @Bean
    JdbcConnectionDetails appRoleConnection(PostgreSQLContainer postgres) {
        return new JdbcConnectionDetails() {
            @Override
            public String getJdbcUrl() {
                return postgres.getJdbcUrl();
            }

            @Override
            public String getUsername() {
                return APP_ROLE;
            }

            @Override
            public String getPassword() {
                return APP_ROLE_PASSWORD;
            }
        };
    }

    /** Flyway: the owner, which creates tables and grants CRUD to the app role. */
    @Bean
    FlywayConnectionDetails ownerConnection(PostgreSQLContainer postgres) {
        return new FlywayConnectionDetails() {
            @Override
            public String getJdbcUrl() {
                return postgres.getJdbcUrl();
            }

            @Override
            public String getUsername() {
                return postgres.getUsername();
            }

            @Override
            public String getPassword() {
                return postgres.getPassword();
            }
        };
    }
}
