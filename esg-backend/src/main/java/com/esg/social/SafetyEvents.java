package com.esg.social;

import java.util.UUID;

/** Health and safety events (WP-46: outbound webhooks). Published inside the transaction that made the change. */
public final class SafetyEvents {
    private SafetyEvents() {}

    public record IncidentRecorded(UUID incidentId) {}
}
