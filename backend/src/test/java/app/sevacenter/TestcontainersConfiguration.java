package app.sevacenter;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Hermetic test infrastructure: tests spin up their own throwaway Postgres via
 * Testcontainers, so {@code ./mvnw verify} needs only Docker — no shared/local DB,
 * no flaky external state. Flyway runs against this container exactly as in prod.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
    }
}
