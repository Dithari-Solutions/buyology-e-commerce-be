package com.buyology.ecommerce.infrastructure.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Forces Flyway's "baseline on migrate" behaviour in code so it is guaranteed
 * regardless of how properties are layered at runtime (packaged profile files,
 * externalised config, or environment variables).
 *
 * <p>Hybrid adoption: the application's existing databases were created by
 * Hibernate {@code ddl-auto=update}, so the {@code public} schema is non-empty
 * and has no Flyway history table. Without baseline-on-migrate, Flyway aborts
 * startup ("Found non-empty schema(s) but no schema history table"). With it,
 * Flyway stamps the existing schema at the baseline version and proceeds.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer flywayBaselineCustomizer() {
        return configuration -> configuration
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .baselineDescription("existing schema (managed by hibernate ddl-auto)");
    }
}
