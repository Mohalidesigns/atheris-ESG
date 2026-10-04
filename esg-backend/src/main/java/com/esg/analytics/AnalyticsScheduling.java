package com.esg.analytics;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Nightly anomaly scan of tenants whose data changed (D34 (3)). Off in the test profile (tests call runAll). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "esg.analytics.scan.enabled", havingValue = "true")
public class AnalyticsScheduling {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsScheduling.class);

    private final AnomalyService anomalies;
    private final Clock clock;

    public AnalyticsScheduling(AnomalyService anomalies, Clock clock) {
        this.anomalies = anomalies;
        this.clock = clock;
    }

    @Scheduled(cron = "${esg.analytics.scan.cron}", zone = "${esg.analytics.scan.zone}")
    void nightly() {
        log.info("Anomaly scan: {} new flags", anomalies.runAll(clock));
    }
}
