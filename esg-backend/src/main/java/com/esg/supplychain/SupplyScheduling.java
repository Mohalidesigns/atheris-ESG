package com.esg.supplychain;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Reminds corrective-action owners daily. Off in the test profile (tests drive it with a shifted clock). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "esg.supply-chain.reminders.enabled", havingValue = "true")
public class SupplyScheduling {

    private static final Logger log = LoggerFactory.getLogger(SupplyScheduling.class);

    private final CorrectiveActionService actions;
    private final Clock clock;

    public SupplyScheduling(CorrectiveActionService actions, Clock clock) {
        this.actions = actions;
        this.clock = clock;
    }

    @Scheduled(cron = "${esg.supply-chain.reminders.cron}", zone = "${esg.supply-chain.reminders.zone}")
    void daily() {
        var run = actions.runAll(clock);
        log.info("Corrective action reminders: {} tenants, {} reminders", run.tenants(), run.reminders());
    }
}
