package com.esg.integrations;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.integrations.IntegrationDtos.ApiKey;
import com.esg.integrations.IntegrationDtos.ApiKeyRequest;
import com.esg.integrations.IntegrationDtos.CreatedApiKey;
import com.esg.integrations.IntegrationDtos.Scope;
import com.esg.security.AccessService;
import com.esg.security.CurrentUser;
import com.esg.security.TenantContext;
import com.esg.security.UserAccess;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WP-46 (D44): API keys for a tenant's own systems — IoT gateways and smart meters pushing readings, scripts, BI tools.
 * <ul>
 *   <li>A key is {@code atk_<prefix>_<secret>}; only its SHA-256 is stored (the secret has 256 bits, so a fast hash is
 *       enough) and it is shown once.</li>
 *   <li>It carries a few permissions from {@link #SCOPES} (no administration), each one its creator holds; on every
 *       request it gets those scopes <em>intersected with the creator's current permissions and org scope</em>, so a
 *       key never outlives or outranks the person who made it.</li>
 *   <li>Keys expire (default one year), can be revoked, and record when they were last used. Changes they make are
 *       audited as their creator, with the key named as the client.</li>
 * </ul>
 */
@Service
public class ApiKeyService {

    /** What a key may be allowed to do (TRD §6.4 machine access): reading data and submitting it as drafts. */
    // no-mock-gate: config — the permissions an API key can carry, with their descriptions
    public static final Map<String, String> SCOPES = scopes();
    static final long DEFAULT_DAYS = 365;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final AccessService access;

    public ApiKeyService(JdbcTemplate jdbc, AccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    private static Map<String, String> scopes() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("data_points:create", "Send data points (saved as drafts for review)");
        s.put("data_points:read", "Read data points");
        s.put("reference_data:read", "Read the metric catalogue and reference data");
        s.put("org_structure:read", "Read the organisation structure");
        s.put("reporting_periods:read", "Read reporting periods");
        s.put("data_sources:read", "Read data sources");
        s.put("carbon_data:create", "Send activity data for emission calculation");
        s.put("carbon_data:read", "Read emissions");
        s.put("social:create", "Report safety incidents and send workforce data");
        s.put("social:read", "Read social data");
        s.put("reports:read", "Read reports");
        s.put("compliance:read", "Read compliance status");
        s.put("analytics:read", "Read analytics");
        return java.util.Collections.unmodifiableMap(s);
    }

    // ---------------------------------------------------------------- management

    public List<Scope> scopeChoices() {
        Set<String> mine = CurrentUser.access().permissions();
        return SCOPES.entrySet().stream().map(e -> new Scope(e.getKey(), e.getValue(), mine.contains(e.getKey()))).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list() {
        return jdbc.query(SELECT + " order by k.revoked_at nulls first, k.created_at desc", (rs, i) -> key(rs));
    }

    @Transactional
    public CreatedApiKey create(ApiKeyRequest r) {
        Set<String> mine = CurrentUser.access().permissions();
        List<String> scopes = r.scopes().stream().distinct().toList();
        List<String> unknown = scopes.stream().filter(s -> !SCOPES.containsKey(s)).toList();
        if (!unknown.isEmpty()) {
            throw FieldValidationException.of("scopes", "an API key can't carry " + String.join(", ", unknown));
        }
        List<String> notHeld = scopes.stream().filter(s -> !mine.contains(s)).toList();
        if (!notHeld.isEmpty()) {
            throw FieldValidationException.of("scopes", "you can only give a key permissions you hold yourself: " + String.join(", ", notHeld));
        }
        Instant expires = r.expiresAt() != null ? r.expiresAt() : Instant.now().plus(DEFAULT_DAYS, ChronoUnit.DAYS);
        if (expires.isAfter(Instant.now().plus(2 * DEFAULT_DAYS, ChronoUnit.DAYS))) {
            throw FieldValidationException.of("expiresAt", "at most two years from today");
        }
        String prefix = random(6);
        String secret = random(32);
        String key = "atk_" + prefix + "_" + secret;
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into integrations.api_keys (id, name, key_prefix, key_hash, scopes, expires_at) "
                    + "values (?, ?, ?, ?, ?, ?)");
            ps.setObject(1, id);
            ps.setString(2, r.name().trim());
            ps.setString(3, prefix);
            ps.setString(4, sha256(key));
            ps.setArray(5, con.createArrayOf("text", scopes.toArray()));
            ps.setTimestamp(6, Timestamp.from(expires));
            return ps;
        });
        return new CreatedApiKey(get(id), key);
    }

    @Transactional
    public ApiKey revoke(UUID id) {
        ApiKey k = get(id);
        if (k.revokedAt() != null) {
            throw ApiException.conflict("ESG-409-STATE", "This key is already revoked");
        }
        jdbc.update("update integrations.api_keys set revoked_at = now(), revoked_by = ?, version = version + 1 where id = ?",
                CurrentUser.access().userId(), id);
        return get(id);
    }

    @Transactional(readOnly = true)
    public ApiKey get(UUID id) {
        return jdbc.query(SELECT + " where k.id = ?", (rs, i) -> key(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("API key"));
    }

    // ---------------------------------------------------------------- authentication (ApiKeyAuthenticationFilter)

    /** A valid key's effective access, or empty. Sets the tenant (and creator) in {@link TenantContext} on success. */
    public Optional<Authenticated> authenticate(String raw) {
        if (raw == null || !raw.startsWith("atk_")) {
            return Optional.empty();
        }
        String[] parts = raw.split("_", 3);
        if (parts.length != 3 || parts[1].isEmpty()) {
            return Optional.empty();
        }
        List<Map<String, Object>> identity = jdbc.queryForList("select key_id, tenant_id from integrations.find_api_key(?)", parts[1]);
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        TenantContext.setTenantId(identity.get(0).get("tenant_id").toString());
        List<Map<String, Object>> rows = jdbc.queryForList("select id, name, key_hash, scopes, created_by, expires_at, revoked_at, last_used_at "
                + "from integrations.api_keys where id = ?", identity.get(0).get("key_id"));
        if (rows.isEmpty()) {
            TenantContext.setTenantId(null);
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        boolean hashMatches = MessageDigest.isEqual(sha256(raw).getBytes(StandardCharsets.US_ASCII),
                ((String) row.get("key_hash")).getBytes(StandardCharsets.US_ASCII));
        Timestamp expires = (Timestamp) row.get("expires_at");
        if (!hashMatches || row.get("revoked_at") != null || (expires != null && expires.toInstant().isBefore(Instant.now()))) {
            TenantContext.setTenantId(null);
            return Optional.empty();
        }
        UUID creator = (UUID) row.get("created_by");
        TenantContext.setUserId(creator.toString());
        UserAccess owner = access.load(creator);
        if (!owner.active()) {
            TenantContext.setTenantId(null);
            TenantContext.setUserId(null);
            return Optional.empty();
        }
        Set<String> scopes = new HashSet<>(Arrays.asList(toArray(row.get("scopes"))));
        scopes.retainAll(owner.permissions());
        UserAccess limited = new UserAccess(owner.userId(), owner.tenantId(), true, Set.of(), Set.copyOf(scopes), owner.tenantWide(),
                owner.orgScopeRoots(), owner.modules(), false, false);
        Timestamp lastUsed = (Timestamp) row.get("last_used_at");
        if (lastUsed == null || lastUsed.toInstant().isBefore(Instant.now().minus(1, ChronoUnit.HOURS))) {
            jdbc.update("update integrations.api_keys set last_used_at = now() where id = ?", row.get("id"));
        }
        return Optional.of(new Authenticated((UUID) row.get("id"), (String) row.get("name"), parts[1], limited));
    }

    public record Authenticated(UUID keyId, String name, String prefix, UserAccess access) {}

    // ---------------------------------------------------------------- helpers

    private static final String SELECT = "select k.id, k.name, k.key_prefix, k.scopes, k.expires_at, k.last_used_at, k.revoked_at, k.created_at, "
            + "u.first_name || ' ' || u.last_name as created_by_name, k.version from integrations.api_keys k left join auth.users u on u.id = k.created_by";

    private static ApiKey key(ResultSet rs) throws SQLException {
        Instant expires = instant(rs.getTimestamp("expires_at"));
        Instant revoked = instant(rs.getTimestamp("revoked_at"));
        String status = revoked != null ? "revoked" : expires != null && expires.isBefore(Instant.now()) ? "expired" : "active";
        Array scopes = rs.getArray("scopes");
        return new ApiKey(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("key_prefix"),
                scopes == null ? List.of() : Arrays.asList((String[]) scopes.getArray()), expires, instant(rs.getTimestamp("last_used_at")),
                revoked, instant(rs.getTimestamp("created_at")), rs.getString("created_by_name"), status, rs.getLong("version"));
    }

    private static String[] toArray(Object value) {
        try {
            return value instanceof Array a ? (String[]) a.getArray() : (String[]) value;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    static String random(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        // URL-safe, and no '_' (it separates the parts of a key).
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b).replace('_', 'x').replace('-', 'y');
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
