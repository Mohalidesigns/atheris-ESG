package com.esg.datahub;

import java.util.UUID;

/** Data Hub workflow events (TRD §6.5.1: carbon consumes approved activity data). Published inside the transaction. */
public final class DataPointEvents {
    private DataPointEvents() {}

    public record Approved(UUID dataPointId) {}

    public record Reopened(UUID dataPointId) {}
}
