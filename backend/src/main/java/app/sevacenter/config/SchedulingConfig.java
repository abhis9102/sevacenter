package app.sevacenter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs: the daily custom domain re-check (ADR 0036). */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
