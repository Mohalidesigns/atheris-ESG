package com.esg.offline;

import com.esg.datahub.DataHubDtos.DataPointResponse;
import com.esg.model.dto.DataPointDto;
import com.esg.social.SocialDtos.IncidentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** WP-43 offline capture (D43): what a device sends to POST /v1/data/sync and what it gets back. */
public final class OfflineDtos {

    private OfflineDtos() {}

    /** TRD §11.2.4: a device catching up after days offline sends up to 500 records at once. */
    public static final int MAX_OPERATIONS = 500;
    public static final String KIND_REGEX = "^(data_point|safety_incident)$";
    public static final String RESOLUTION_REGEX = "^(use_mine|keep_server)$";

    public record SyncRequest(
            @NotBlank @Size(max = 100) String deviceId,
            @NotNull @Size(min = 1, max = MAX_OPERATIONS, message = "between 1 and " + MAX_OPERATIONS + " operations per request")
            List<@NotNull @Valid SyncOperation> operations) {}

    /**
     * One save made on the device. {@code opId} identifies this save (resending it is harmless); {@code clientId}
     * identifies the record on the device. {@code recordId}/{@code baseVersion}: the server record and version the device
     * last saw, for an offline edit of something already synced. The record itself is in {@code dataPoint} or
     * {@code incident}, validated per operation so one bad record doesn't refuse the others.
     */
    public record SyncOperation(
            @NotNull UUID opId,
            @NotNull UUID clientId,
            @NotBlank @Pattern(regexp = KIND_REGEX, message = "must be data_point or safety_incident") String kind,
            @NotNull Instant capturedAt,
            UUID recordId,
            Long baseVersion,
            DataPointDto dataPoint,
            IncidentRequest incident) {}

    /** outcome: applied | conflict | rejected. {@code replayed}: this operation had already been received. */
    public record SyncResult(UUID opId, UUID clientId, String kind, String outcome, UUID recordId, Long recordVersion,
                             String message, Map<String, String> errors, boolean replayed, UUID syncId) {}

    public record SyncResponse(int received, int applied, int conflicts, int rejected, List<SyncResult> results) {}

    /** A conflict waiting for the person who captured the value: their value next to the server's current one. */
    public record Conflict(UUID id, UUID opId, UUID clientId, String deviceId, Instant capturedAt, Instant receivedAt,
                           String reason, DataPointDto mine, DataPointResponse server) {}

    /** {@code version}: the server record's version the person decided against (required for use_mine). */
    public record ResolveRequest(
            @NotBlank @Pattern(regexp = RESOLUTION_REGEX, message = "must be use_mine or keep_server") String resolution,
            Long version) {}
}
