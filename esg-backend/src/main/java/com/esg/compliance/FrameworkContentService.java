package com.esg.compliance;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.reference.FrameworkCatalogService;
import com.esg.reference.ReferenceDtos.Framework;
import com.esg.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 3 (5): the requirement import pipeline. A platform administrator uploads a framework's requirements (CSV or
 * JSON) as a named version with effective dates and an SME-verified flag; a dry run shows what would be added, changed
 * and retired; applying it writes the version (FrameworkContentWriter) and an audit row. Unverified content stays
 * "pending SME verification" in every screen that shows it.
 */
@Service
public class FrameworkContentService {

    public record ImportRequest(String versionLabel, LocalDate effectiveFrom, LocalDate effectiveTo, boolean smeVerified,
                                String verifiedBy, String source, boolean dryRun) {}

    public record ImportResult(String framework, String versionLabel, boolean applied, int rows, List<String> added,
                               List<String> changed, List<String> retired, int unchanged, List<FrameworkContent.RowError> errors) {}

    public record ContentVersion(UUID id, String versionLabel, LocalDate effectiveFrom, LocalDate effectiveTo, boolean smeVerified,
                                 String verifiedBy, String source, String format, int rows, int added, int changed, int retired,
                                 String importedBy, Instant importedAt) {}

    private final JdbcTemplate jdbc;
    private final FrameworkCatalogService catalog;
    private final ObjectMapper json;

    public FrameworkContentService(JdbcTemplate jdbc, FrameworkCatalogService catalog, ObjectMapper json) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.json = json;
    }

    @Transactional
    public ImportResult importContent(String code, ImportRequest r, byte[] content, String fileName) {
        Framework framework = catalog.framework(code);
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.versionLabel() == null || r.versionLabel().isBlank() || r.versionLabel().length() > 50) {
            errors.put("versionLabel", "is required (at most 50 characters)");
        }
        if (r.effectiveFrom() == null) {
            errors.put("effectiveFrom", "is required");
        } else if (r.effectiveTo() != null && r.effectiveTo().isBefore(r.effectiveFrom())) {
            errors.put("effectiveTo", "must be on or after the effective date");
        }
        if (r.source() == null || r.source().isBlank() || r.source().length() > 500) {
            errors.put("source", "Name the instrument this content comes from (at most 500 characters)");
        }
        if (r.smeVerified() && (r.verifiedBy() == null || r.verifiedBy().isBlank())) {
            errors.put("verifiedBy", "Say who verified this content");
        }
        String format = format(fileName);
        if (format == null) {
            errors.put("file", "Upload a .csv or .json file");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        String label = r.versionLabel().trim();
        Integer exists = jdbc.queryForObject("select count(*) from compliance.framework_content_versions where framework_id = ? "
                + "and version_label = ?", Integer.class, framework.id(), label);
        if (exists != null && exists > 0) {
            throw ApiException.conflict("ESG-409-CONTENT-VERSION", framework.code() + " already has content version '" + label
                    + "'. Use a new version label.");
        }
        LocalDate latest = jdbc.query("select max(effective_from) from compliance.framework_content_versions where framework_id = ?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, framework.id());
        if (latest != null && r.effectiveFrom().isBefore(latest)) {
            throw ApiException.badRequest("ESG-400-CONTENT-DATES", "The current version of " + framework.code() + " is effective from "
                    + latest + "; a new version can't take effect earlier");
        }

        Set<String> metrics = new HashSet<>(jdbc.queryForList("select metric_key from reference.metrics", String.class));
        FrameworkContent.Parsed parsed = FrameworkContent.parse(content, format, metrics);
        FrameworkContentWriter.Meta meta = new FrameworkContentWriter.Meta(label, r.effectiveFrom(), r.effectiveTo(), r.smeVerified(),
                r.smeVerified() ? r.verifiedBy().trim() : null, r.source().trim(), format);
        if (!parsed.ok()) {
            return new ImportResult(framework.code(), label, false, parsed.rows().size(), List.of(), List.of(), List.of(), 0, parsed.errors());
        }
        FrameworkContentWriter.Diff diff = FrameworkContentWriter.diff(jdbc, framework.id(), parsed.rows(), meta);
        if (!r.dryRun()) {
            UUID version = FrameworkContentWriter.apply(jdbc, framework.id(), meta, parsed, CurrentUser.access().userId());
            audit(version, framework, meta, diff, parsed.rows().size());
        }
        return new ImportResult(framework.code(), label, !r.dryRun(), parsed.rows().size(), diff.added(), diff.changed(), diff.retired(),
                diff.unchanged(), List.of());
    }

    public List<ContentVersion> versions(String code) {
        Framework framework = catalog.framework(code);
        return jdbc.query("select v.*, u.first_name || ' ' || u.last_name as importer from compliance.framework_content_versions v "
                        + "left join auth.users u on u.id = v.imported_by where v.framework_id = ? order by v.effective_from desc, v.imported_at desc",
                (rs, i) -> new ContentVersion(rs.getObject("id", UUID.class), rs.getString("version_label"),
                        rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
                        rs.getBoolean("sme_verified"), rs.getString("verified_by"), rs.getString("source"), rs.getString("format"),
                        rs.getInt("row_count"), rs.getInt("added"), rs.getInt("changed"), rs.getInt("retired"),
                        rs.getString("importer") == null ? "Bundled content pack" : rs.getString("importer"),
                        rs.getTimestamp("imported_at").toInstant()), framework.id());
    }

    /** Framework content is platform data with no tenant, so its trigger-less import is audited here, under the importer's tenant. */
    private void audit(UUID version, Framework framework, FrameworkContentWriter.Meta meta, FrameworkContentWriter.Diff diff, int rows) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("framework", framework.code());
        value.put("version_label", meta.versionLabel());
        value.put("effective_from", meta.effectiveFrom().toString());
        value.put("sme_verified", meta.smeVerified());
        value.put("source", meta.source());
        value.put("rows", rows);
        value.put("added", diff.added().size());
        value.put("changed", diff.changed().size());
        value.put("retired", diff.retired().size());
        try {
            jdbc.update("insert into audit.audit_logs (action, resource, resource_id, new_value) values ('insert', "
                    + "'compliance.framework_content_versions', ?, ?::jsonb)", version, json.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String format(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".csv") ? "csv" : name.endsWith(".json") ? "json" : null;
    }
}
