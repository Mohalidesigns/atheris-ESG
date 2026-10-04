package com.esg.reporting;

import java.util.UUID;

/** Reporting events (WP-46: outbound webhooks). Published inside the transaction that made the change. */
public final class ReportEvents {
    private ReportEvents() {}

    /** The last approval step passed: the report is approved. */
    public record Approved(UUID reportId) {}
}
