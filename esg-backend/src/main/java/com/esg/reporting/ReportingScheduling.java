package com.esg.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the report approval SLA check daily. Off in the test profile (tests drive it with a fixed clock). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "esg.reporting.sla.enabled", havingValue = "true")
public class ReportingScheduling {

    private static final Logger log = LoggerFactory.getLogger(ReportingScheduling.class);

    private final ApprovalSlaService sla;

    public ReportingScheduling(ApprovalSlaService sla) {
        this.sla = sla;
    }

    @Scheduled(cron = "${esg.reporting.sla.cron}", zone = "${esg.reporting.sla.zone}")
    void daily() {
        var run = sla.runAll();
        log.info("Report approval SLA: {} tenants, {} overdue steps reminded", run.tenants(), run.reminded());
    }
}
