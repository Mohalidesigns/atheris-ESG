package com.esg.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phase 3 (5): a framework's requirement content as a file — CSV with the header below, or JSON
 * {@code {"requirements": [{...same fields in camelCase...}]}} — parsed and checked before anything is written.
 * <pre>requirement_code,title,category,data_type,parent_code,source_ref,metric_keys,description</pre>
 * Used by the platform import API (FrameworkContentService) and by the bundled content packs (Flyway, R__ref_12).
 */
public final class FrameworkContent {

    private FrameworkContent() {}

    public static final List<String> HEADER = List.of("requirement_code", "title", "category", "data_type", "parent_code",
            "source_ref", "metric_keys", "description");
    public static final int MAX_ROWS = 2000;
    // no-mock-gate: config — values the compliance schema accepts (V005 framework_requirements, GapAnalysisEngine)
    static final Set<String> CATEGORIES = Set.of("E", "S", "G", "GEN");
    static final Set<String> DATA_TYPES = Set.of("quantitative", "narrative");
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ._()/:-]{0,99}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Row(int line, String code, String title, String category, String dataType, String parentCode, String sourceRef,
                      List<String> metricKeys, String description) {}

    /** A problem with one line of the file (line 1 = the CSV header; JSON entries count from 1). */
    public record RowError(int line, String field, String message) {}

    public record Parsed(List<Row> rows, List<RowError> errors, String checksum) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** @param knownMetrics metric keys in the catalogue (reference.metrics); others are errors */
    public static Parsed parse(byte[] content, String format, Set<String> knownMetrics) {
        List<RowError> errors = new ArrayList<>();
        List<Row> rows = "json".equals(format) ? readJson(content, errors) : readCsv(content, errors);
        if (errors.isEmpty()) {
            check(rows, knownMetrics, errors);
        }
        return new Parsed(rows, errors, sha256(content));
    }

    private static List<Row> readCsv(byte[] content, List<RowError> errors) {
        List<String[]> lines;
        try (CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            lines = reader.readAll();
        } catch (IOException | CsvException e) {
            errors.add(new RowError(0, null, "The file isn't valid CSV: " + e.getMessage()));
            return List.of();
        }
        if (lines.isEmpty()) {
            errors.add(new RowError(1, null, "The file is empty"));
            return List.of();
        }
        List<String> header = Arrays.stream(lines.get(0)).map(h -> h.replace("﻿", "").trim().toLowerCase(Locale.ROOT)).toList();
        if (!header.equals(HEADER)) {
            errors.add(new RowError(1, null, "The header must be exactly: " + String.join(",", HEADER)));
            return List.of();
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i);
            if (Arrays.stream(cells).allMatch(c -> c == null || c.isBlank())) {
                continue;
            }
            if (cells.length != HEADER.size()) {
                errors.add(new RowError(i + 1, null, "Has " + cells.length + " columns; expected " + HEADER.size()));
                continue;
            }
            rows.add(row(i + 1, cells[0], cells[1], cells[2], cells[3], cells[4], cells[5], cells[6], cells[7]));
        }
        return rows;
    }

    private static List<Row> readJson(byte[] content, List<RowError> errors) {
        JsonNode root;
        try {
            root = JSON.readTree(content);
        } catch (IOException e) {
            errors.add(new RowError(0, null, "The file isn't valid JSON: " + e.getMessage()));
            return List.of();
        }
        JsonNode list = root == null ? null : root.get("requirements");
        if (list == null || !list.isArray()) {
            errors.add(new RowError(0, null, "Expected {\"requirements\": [ … ]}"));
            return List.of();
        }
        List<Row> rows = new ArrayList<>();
        int i = 0;
        for (JsonNode r : list) {
            i++;
            JsonNode keys = r.get("metricKeys");
            String metricKeys = keys == null || keys.isNull() ? "" : keys.isArray()
                    ? String.join(";", java.util.stream.StreamSupport.stream(keys.spliterator(), false).map(JsonNode::asText).toList())
                    : keys.asText();
            rows.add(row(i, text(r, "requirementCode"), text(r, "title"), text(r, "category"), text(r, "dataType"),
                    text(r, "parentCode"), text(r, "sourceRef"), metricKeys, text(r, "description")));
        }
        return rows;
    }

    private static Row row(int line, String code, String title, String category, String dataType, String parent, String sourceRef,
                           String metricKeys, String description) {
        List<String> keys = metricKeys == null || metricKeys.isBlank() ? List.of()
                : Arrays.stream(metricKeys.split(";")).map(String::trim).filter(k -> !k.isEmpty()).distinct().toList();
        return new Row(line, trim(code), trim(title), trim(category) == null ? null : trim(category).toUpperCase(Locale.ROOT),
                trim(dataType) == null ? null : trim(dataType).toLowerCase(Locale.ROOT), trim(parent), trim(sourceRef), keys, trim(description));
    }

    private static void check(List<Row> rows, Set<String> knownMetrics, List<RowError> errors) {
        if (rows.isEmpty()) {
            errors.add(new RowError(0, null, "The file has no requirements"));
            return;
        }
        if (rows.size() > MAX_ROWS) {
            errors.add(new RowError(0, null, "At most " + MAX_ROWS + " requirements per file"));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Row r : rows) {
            if (r.code() == null) {
                errors.add(new RowError(r.line(), "requirement_code", "is required"));
            } else if (!CODE.matcher(r.code()).matches()) {
                errors.add(new RowError(r.line(), "requirement_code", "letters, digits, spaces and . _ ( ) / : - only; at most 100"));
            } else if (!seen.add(r.code())) {
                errors.add(new RowError(r.line(), "requirement_code", "'" + r.code() + "' appears twice"));
            }
            if (r.title() == null) {
                errors.add(new RowError(r.line(), "title", "is required"));
            } else if (r.title().length() > 500) {
                errors.add(new RowError(r.line(), "title", "at most 500 characters"));
            }
            if (r.category() == null || !CATEGORIES.contains(r.category())) {
                errors.add(new RowError(r.line(), "category", "must be E, S, G or GEN"));
            }
            if (r.dataType() == null || !DATA_TYPES.contains(r.dataType())) {
                errors.add(new RowError(r.line(), "data_type", "must be quantitative or narrative"));
            }
            if (r.parentCode() != null && (r.parentCode().equals(r.code()) || !seen.contains(r.parentCode()))) {
                errors.add(new RowError(r.line(), "parent_code", "'" + r.parentCode() + "' must be a requirement listed above this one"));
            }
            if (r.sourceRef() != null && r.sourceRef().length() > 500) {
                errors.add(new RowError(r.line(), "source_ref", "at most 500 characters"));
            }
            for (String key : r.metricKeys()) {
                if (!knownMetrics.contains(key)) {
                    errors.add(new RowError(r.line(), "metric_keys", "'" + key + "' isn't in the metric catalogue"));
                }
            }
        }
    }

    /** The rows as a map by code, in file order. */
    public static Map<String, Row> byCode(List<Row> rows) {
        Map<String, Row> map = new LinkedHashMap<>();
        rows.forEach(r -> map.put(r.code(), r));
        return map;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String trim(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
