package com.esg.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Audit log viewer and lineage responses (WP-15). */
public final class AuditDtos {
    private AuditDtos() {}

    public record AuditEntry(UUID id, Instant createdAt, UUID userId, String userName, String action, String resource,
                             UUID resourceId, JsonNode oldValue, JsonNode newValue, String ipAddress, String userAgent) {}

    /** An audited table for the Audit log screen's filter: {@code value} is what the filter sends. */
    public record AuditResource(String value, String label, String module) {}

    public record AuditPage(List<AuditEntry> content, int page, int size, long totalElements, int totalPages) {}

    public record LineageEdge(UUID id, String sourceType, UUID sourceId, String targetType, UUID targetId, String relation,
                              JsonNode details, Instant createdAt) {}

    public record Lineage(List<LineageEdge> upstream, List<LineageEdge> downstream) {}
}
