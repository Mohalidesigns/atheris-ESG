package com.esg.privacy;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Applies each tenant's retention settings nightly (D40 (5)). Off in the test profile (tests call runAll). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "esg.privacy.retention.enabled", havingValue = "true")
public class PrivacyScheduling {

    private static final Logger log = LoggerFactory.getLogger(PrivacyScheduling.class);

    private final RetentionService retention;
    private final Clock clock;

    public PrivacyScheduling(RetentionService retention, Clock clock) {
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "${esg.privacy.retention.cron}", zone = "Africa/Lagos")
    void nightly() {
        var r = retention.runAll(clock);
        log.info("Retention: {} tenants; removed {} notifications, {} message logs, {} sessions; cleared {} access-log IPs",
                r.tenants(), r.notifications(), r.deliveries(), r.sessions(), r.accessLogIps());
    }
}
