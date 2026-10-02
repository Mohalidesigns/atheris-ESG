package com.esg.datahub;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bulk import bodies (WP-20, BRD-5.2.1-08, journey 4). */
public final class ImportDtos {
    private ImportDtos() {}

    /** Target fields a column can map to. */
    public static final List<String> FIELDS = List.of("organisation", "metric", "value", "currency", "periodStart",
            "periodEnd", "subCategory", "notes");

    public record ImportJobResponse(UUID id, String fileName, String fileFormat, String status, List<String> columns,
                                    JsonNode sampleRows, Map<String, String> suggestedMapping, JsonNode mapping,
                                    JsonNode defaults, UUID sourceId, int totalRows, int processedRows,
                                    int succeededRows, int failedRows, JsonNode rowErrors, String failure,
                                    Instant startedAt, Instant finishedAt, Instant createdAt, Long version) {}

    /**
     * Column for each target field (header text). Organisation and period can instead come from defaults that apply
     * to every row.
     */
    public record ImportStartRequest(@NotNull Map<String, String> mapping, UUID defaultOrgId, LocalDate defaultPeriodStart,
                                     LocalDate defaultPeriodEnd, UUID sourceId, @NotNull Long version) {}

    public record RowError(int row, String column, String field, String message) {}
}
