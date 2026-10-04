package com.esg.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the delivery dispatcher on a fixed delay. Off in the test profile, where tests call runOnce() directly. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "esg.notifications.dispatcher.enabled", havingValue = "true")
public class NotificationScheduling {

    private final NotificationDispatcher dispatcher;

    public NotificationScheduling(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${esg.notifications.dispatcher.interval-ms}")
    void dispatch() {
        dispatcher.runOnce();
    }
}
