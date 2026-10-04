package com.esg.integrations;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Sends due webhook deliveries every few seconds (D44). Off in the test profile (tests call the dispatcher). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "esg.webhooks.dispatch.enabled", havingValue = "true")
public class WebhookScheduling {

    private final WebhookDispatcher dispatcher;

    public WebhookScheduling(WebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${esg.webhooks.dispatch.interval-ms:10000}", initialDelay = 30_000)
    void dispatch() {
        dispatcher.dispatchAll();
    }
}
