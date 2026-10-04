package com.esg.security;

import com.esg.exception.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Organisation scoping for org-scoped roles (ESG_MANAGER, ESG_ANALYST, DATA_COLLECTOR; TRD §5.2, §5.3.2).
 * A scoped assignment grants its organisation and everything below it in the org tree.
 */
@Service
public class OrgScopeService {

    private final JdbcTemplate jdbc;

    public OrgScopeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Organisation ids the current user may access, or empty Optional meaning "all organisations in the tenant". */
    public Optional<Set<UUID>> accessibleOrgIds() {
        UserAccess access = CurrentUser.access();
        if (access.tenantWide()) {
            return Optional.empty();
        }
        if (access.orgScopeRoots().isEmpty()) {
            return Optional.of(Set.of());
        }
        UUID[] roots = access.orgScopeRoots().toArray(new UUID[0]);
        List<UUID> ids = jdbc.query(con -> {
            var ps = con.prepareStatement("with recursive tree as ("
                    + " select id from auth.organisations where id = any(?)"
                    + " union select o.id from auth.organisations o join tree t on o.parent_org_id = t.id)"
                    + " select id from tree");
            ps.setArray(1, con.createArrayOf("uuid", roots));
            return ps;
        }, (rs, i) -> rs.getObject(1, UUID.class));
        return Optional.of(Set.copyOf(ids));
    }

    /** Scope for repository queries: {@code all} or the explicit org ids (never an empty IN list). */
    public record QueryScope(boolean all, Set<UUID> orgIds) {}

    public QueryScope queryScope() {
        Optional<Set<UUID>> scope = accessibleOrgIds();
        if (scope.isEmpty()) {
            return new QueryScope(true, Set.of(NO_ORG));
        }
        return new QueryScope(false, scope.get().isEmpty() ? Set.of(NO_ORG) : scope.get());
    }

    /** True if the current user may see {@code orgId}. */
    public boolean canAccess(UUID orgId) {
        Optional<Set<UUID>> scope = accessibleOrgIds();
        return scope.isEmpty() || scope.get().contains(orgId);
    }

    /** Placeholder id that matches no organisation (keeps JPQL IN lists non-empty). */
    private static final UUID NO_ORG = new UUID(0L, 0L);

    /** Throws 403 unless the current user may act on {@code orgId}. */
    public void requireAccess(UUID orgId) {
        Optional<Set<UUID>> scope = accessibleOrgIds();
        if (scope.isPresent() && !scope.get().contains(orgId)) {
            throw ApiException.forbidden("You do not have access to this organisation");
        }
    }
}
