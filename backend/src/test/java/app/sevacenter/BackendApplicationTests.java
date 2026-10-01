package app.sevacenter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Smoke test: the full application context starts against a real (containerized)
 * Postgres with Flyway migrations applied. If this passes, wiring + schema are sound.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
