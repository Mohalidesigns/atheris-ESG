package db.migration;

import com.esg.compliance.FrameworkContent;
import com.esg.compliance.FrameworkContentWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Phase 3 (5): loads the bundled framework content packs (src/main/resources/framework-content, listed in manifest.json)
 * through the same parser and writer as the platform import API. Every pack is "pending SME verification"; its sources
 * are in framework-content/SOURCES.md. Repeatable: it runs again when a pack changes (checksum over all files). A pack
 * whose version label is already loaded with the same content is skipped; changed content needs a new version label,
 * so loaded versions are never rewritten.
 */
public class R__ref_12_framework_content extends BaseJavaMigration {

    private static final String DIR = "framework-content/";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public Integer getChecksum() {
        CRC32 crc = new CRC32();
        byte[] manifest = read(DIR + "manifest.json");
        if (manifest == null) {
            return 0;
        }
        crc.update(manifest);
        for (JsonNode pack : packs(manifest)) {
            crc.update(read(DIR + pack.get("file").asText()));
        }
        return (int) crc.getValue();
    }

    @Override
    public void migrate(Context context) throws Exception {
        byte[] manifest = read(DIR + "manifest.json");
        if (manifest == null) {
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
        Set<String> metrics = new HashSet<>(jdbc.queryForList("select metric_key from reference.metrics", String.class));
        for (JsonNode pack : packs(manifest)) {
            String code = pack.get("framework").asText();
            String file = pack.get("file").asText();
            List<UUID> ids = jdbc.queryForList("select id from compliance.regulatory_frameworks where code = ?", UUID.class, code);
            if (ids.isEmpty()) {
                throw new IllegalStateException("framework-content/" + file + ": unknown framework " + code);
            }
            byte[] content = read(DIR + file);
            FrameworkContent.Parsed parsed = FrameworkContent.parse(content, file.endsWith(".json") ? "json" : "csv", metrics);
            if (!parsed.ok()) {
                throw new IllegalStateException("framework-content/" + file + " has errors: " + parsed.errors());
            }
            String label = pack.get("versionLabel").asText();
            List<String> loaded = jdbc.queryForList("select checksum from compliance.framework_content_versions "
                    + "where framework_id = ? and version_label = ?", String.class, ids.get(0), label);
            if (!loaded.isEmpty()) {
                if (loaded.get(0).equals(parsed.checksum())) {
                    continue;
                }
                throw new IllegalStateException("framework-content/" + file + " changed but its version label '" + label
                        + "' is already loaded; give the changed content a new versionLabel in manifest.json");
            }
            JsonNode to = pack.get("effectiveTo");
            FrameworkContentWriter.apply(jdbc, ids.get(0), new FrameworkContentWriter.Meta(label,
                    LocalDate.parse(pack.get("effectiveFrom").asText()), to == null || to.isNull() ? null : LocalDate.parse(to.asText()),
                    false, null, pack.get("source").asText(), file.endsWith(".json") ? "json" : "csv"), parsed, null);
        }
    }

    private static List<JsonNode> packs(byte[] manifest) {
        try {
            JsonNode root = JSON.readTree(manifest);
            return root.path("packs").isArray() ? java.util.stream.StreamSupport.stream(root.get("packs").spliterator(), false).toList()
                    : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException("framework-content/manifest.json isn't valid JSON", e);
        }
    }

    private static byte[] read(String path) {
        try (InputStream in = R__ref_12_framework_content.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
