package com.esg.compliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the filing-deadline reminders daily (TRD §8.3 DeadlineScheduler). Off in the test profile (tests time-travel). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "esg.compliance.reminders.enabled", havingValue = "true")
public class ComplianceScheduling {

    private static final Logger log = LoggerFactory.getLogger(ComplianceScheduling.class);

    private final DeadlineReminderService reminders;

    public ComplianceScheduling(DeadlineReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(cron = "${esg.compliance.reminders.cron}", zone = "${esg.compliance.reminders.zone}")
    void daily() {
        var run = reminders.runAll();
        log.info("Deadline reminders: {} tenants, {} reminders, {} marked overdue", run.tenants(), run.remindersSent(), run.overdueMarked());
    }
}
