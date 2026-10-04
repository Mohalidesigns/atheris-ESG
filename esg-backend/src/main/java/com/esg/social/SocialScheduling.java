package com.esg.social;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Escalates overdue grievances daily. Off in the test profile (tests drive it with a shifted clock). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "esg.social.escalation.enabled", havingValue = "true")
public class SocialScheduling {

    private static final Logger log = LoggerFactory.getLogger(SocialScheduling.class);

    private final GrievanceService grievances;
    private final Clock clock;

    public SocialScheduling(GrievanceService grievances, Clock clock) {
        this.grievances = grievances;
        this.clock = clock;
    }

    @Scheduled(cron = "${esg.social.escalation.cron}", zone = "${esg.social.escalation.zone}")
    void daily() {
        var run = grievances.runAll(clock);
        log.info("Grievance escalation: {} tenants, {} grievances escalated", run.tenants(), run.escalated());
    }
}
