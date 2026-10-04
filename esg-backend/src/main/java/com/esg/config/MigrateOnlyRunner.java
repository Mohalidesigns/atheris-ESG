package com.esg.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Phase 3 (7): {@code --esg.migrate-only=true} starts the application just far enough for Flyway to migrate and seed
 * the database, then exits. Used by {@code make reset-dev} (scripts/dev.sh reset-dev) to rebuild the local database
 * without serving requests.
 */
@Component
@ConditionalOnProperty(name = "esg.migrate-only", havingValue = "true")
public class MigrateOnlyRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrateOnlyRunner.class);
    private final ConfigurableApplicationContext context;

    public MigrateOnlyRunner(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Database migrated and seeded (esg.migrate-only); exiting");
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
