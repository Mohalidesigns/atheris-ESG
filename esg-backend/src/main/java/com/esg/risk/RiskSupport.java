package com.esg.risk;

import com.esg.exception.ApiException;
import com.esg.security.OrgScopeService;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** What the risk services share: organisation scope, the people who hold a permission, and Lagos dates. */
@Component
public class RiskSupport {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private static final String HOLDERS = "select distinct u.id, u.first_name || ' ' || u.last_name as name from auth.users u "
            + "join auth.user_roles ur on ur.user_id = u.id and (ur.expires_at is null or ur.expires_at > now()) "
            + "join auth.role_permissions rp on rp.role_id = ur.role_id join auth.permissions p on p.id = rp.permission_id "
            + "where u.is_active and u.deleted_at is null and p.resource = 'risk' and p.action = ? and not auth.is_platform_user(u.id)";

    private final JdbcTemplate jdbc;
    private final OrgScopeService orgScope;
    private final Clock clock;

    public RiskSupport(JdbcTemplate jdbc, OrgScopeService orgScope, Clock clock) {
        this.jdbc = jdbc;
        this.orgScope = orgScope;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(LAGOS));
    }

    Clock clock() {
        return clock;
    }

    /** Appends "and <column> in (orgs the user may see)" unless the user sees the whole tenant. */
    void scope(StringBuilder where, List<Object> args, String column) {
        OrgScopeService.QueryScope s = orgScope.queryScope();
        if (!s.all()) {
            where.append(" and ").append(column).append(" = any(?::uuid[])");
            args.add(s.orgIds().stream().map(UUID::toString).toArray(String[]::new));
        }
    }

    /** 404 for a record at an organisation outside the user's scope (it isn't theirs to know about). */
    void visible(UUID orgId, String what) {
        if (!orgScope.canAccess(orgId)) {
            throw ApiException.notFound(what);
        }
    }

    boolean canSee(UUID orgId) {
        return orgScope.canAccess(orgId);
    }

    boolean anyInScope(java.util.Collection<UUID> orgIds) {
        return orgIds.stream().anyMatch(orgScope::canAccess);
    }

    /** 403 when the user picks an organisation outside their scope (or one that doesn't exist in the tenant). */
    void requireOrg(UUID orgId) {
        Boolean exists = jdbc.queryForObject("select exists (select 1 from auth.organisations where id = ? and deleted_at is null)",
                Boolean.class, orgId);
        if (!Boolean.TRUE.equals(exists)) {
            throw com.esg.exception.FieldValidationException.of("orgId", "must be an organisation in your tenant");
        }
        orgScope.requireAccess(orgId);
    }

    /** Active users holding risk:{action} in this tenant (RLS keeps it to the tenant). */
    List<RiskDtos.Person> holders(String action) {
        return jdbc.query("select * from (" + HOLDERS + ") x order by name", (rs, i) -> new RiskDtos.Person(rs.getObject("id", UUID.class),
                rs.getString("name")), action);
    }

    boolean holds(UUID user, String action) {
        return holders(action).stream().anyMatch(p -> p.id().equals(user));
    }

    static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    static List<Integer> ints(Array a) throws SQLException {
        if (a == null) {
            return List.of();
        }
        Object[] values = (Object[]) a.getArray();
        return java.util.Arrays.stream(values).map(v -> ((Number) v).intValue()).sorted().toList();
    }
}
