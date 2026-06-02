package com.buyology.ecommerce.revenue.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops the auto-generated CHECK constraints on {@code revenue_exports} at startup.
 *
 * Hibernate generates a CHECK constraint for each @Enumerated(STRING) column from
 * the enum's values at table-creation time. The table was first created when
 * RevenueExportFormat only had CSV/XLSX, so its {@code format} column still carries
 * CHECK (format IN ('CSV','XLSX')). ddl-auto=update never alters existing check
 * constraints, so inserting a PDF export row fails with a constraint violation
 * (surfaced as HTTP 409). Enum values are validated in Java, so the DB-level check
 * is redundant — removing it lets any current/future export format be stored.
 *
 * Idempotent: once the constraints are gone this is a no-op. Postgres-specific.
 */
@Component
public class RevenueExportSchemaFixup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RevenueExportSchemaFixup.class);

    private final JdbcTemplate jdbcTemplate;

    public RevenueExportSchemaFixup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    DO $$
                    DECLARE c record;
                    BEGIN
                        IF to_regclass('public.revenue_exports') IS NULL THEN
                            RETURN;
                        END IF;
                        FOR c IN
                            SELECT conname FROM pg_constraint
                            WHERE conrelid = 'public.revenue_exports'::regclass
                              AND contype = 'c'
                        LOOP
                            EXECUTE 'ALTER TABLE revenue_exports DROP CONSTRAINT ' || quote_ident(c.conname);
                        END LOOP;
                    END $$;
                    """);
            log.info("revenue_exports CHECK constraints normalized — all export formats allowed");
        } catch (Exception e) {
            log.warn("Could not normalize revenue_exports CHECK constraints: {}", e.getMessage());
        }
    }
}
