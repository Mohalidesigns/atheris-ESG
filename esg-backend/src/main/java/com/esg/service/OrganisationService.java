package com.esg.service;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.model.dto.TenancyDtos.OrganisationRequest;
import com.esg.model.dto.TenancyDtos.OrganisationResponse;
import com.esg.security.OrgScopeService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organisation hierarchy: group → subsidiary → business unit → facility/site (WP-14, TRD §4.2.1).
 * RLS limits everything to the tenant; org-scoped users additionally work only inside their subtree.
 */
@Service
public class OrganisationService {

    private static final String COLUMNS = "id, parent_org_id, name, org_type, country_code, state_region, address, "
            + "latitude, longitude, is_active, version";
    private static final RowMapper<OrganisationResponse> MAPPER = (rs, i) -> new OrganisationResponse(
            rs.getObject("id", UUID.class), rs.getObject("parent_org_id", UUID.class), rs.getString("name"),
            rs.getString("org_type"), rs.getString("country_code"), rs.getString("state_region"), rs.getString("address"),
            rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getBoolean("is_active"), rs.getLong("version"));

    private final JdbcTemplate jdbc;
    private final OrgScopeService orgScope;

    public OrganisationService(JdbcTemplate jdbc, OrgScopeService orgScope) {
        this.jdbc = jdbc;
        this.orgScope = orgScope;
    }

    @Transactional(readOnly = true)
    public List<OrganisationResponse> list() {
        Optional<Set<UUID>> scope = orgScope.accessibleOrgIds();
        return jdbc.query("select " + COLUMNS + " from auth.organisations where deleted_at is null order by name", MAPPER)
                .stream().filter(o -> scope.isEmpty() || scope.get().contains(o.id())).toList();
    }

    @Transactional(readOnly = true)
    public OrganisationResponse get(UUID id) {
        return find(id);
    }

    @Transactional
    public OrganisationResponse create(OrganisationRequest r) {
        requireParentAccess(r.parentOrgId());
        UUID id = jdbc.queryForObject("insert into auth.organisations (parent_org_id, name, org_type, country_code, "
                        + "state_region, address, latitude, longitude) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
                UUID.class, r.parentOrgId(), r.name().trim(), r.orgType(), blankToNull(r.countryCode()),
                blankToNull(r.stateRegion()), blankToNull(r.address()), r.latitude(), r.longitude());
        return find(id);
    }

    @Transactional
    public OrganisationResponse update(UUID id, OrganisationRequest r) {
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        OrganisationResponse current = find(id);
        if (r.parentOrgId() != null && !r.parentOrgId().equals(current.parentOrgId())) {
            if (r.parentOrgId().equals(id) || descendants(id).contains(r.parentOrgId())) {
                throw FieldValidationException.of("parentOrgId", "An organisation can't be moved under itself or its own sub-organisations");
            }
            requireParentAccess(r.parentOrgId());
        } else if (r.parentOrgId() == null && current.parentOrgId() != null) {
            requireParentAccess(null);
        }
        int updated = jdbc.update("update auth.organisations set parent_org_id = ?, name = ?, org_type = ?, country_code = ?, "
                        + "state_region = ?, address = ?, latitude = ?, longitude = ?, version = version + 1 "
                        + "where id = ? and version = ? and deleted_at is null",
                r.parentOrgId(), r.name().trim(), r.orgType(), blankToNull(r.countryCode()), blankToNull(r.stateRegion()),
                blankToNull(r.address()), r.latitude(), r.longitude(), id, r.version());
        if (updated == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This organisation was changed by someone else. Reload and try again.");
        }
        return find(id);
    }

    /** Soft delete. Refused while anything still points at the organisation, so no data loses its owner. */
    @Transactional
    public void delete(UUID id) {
        find(id);
        String[][] references = {
            {"select count(*) from auth.organisations where parent_org_id = ? and deleted_at is null", "sub-organisations"},
            {"select count(*) from auth.user_roles where org_scope_id = ?", "role assignments"},
            {"select count(*) from data_hub.data_points where org_id = ?", "data points"},
            {"select count(*) from carbon.emission_records where org_id = ?", "emission records"},
        };
        for (String[] ref : references) {
            Long count = jdbc.queryForObject(ref[0], Long.class, id);
            if (count != null && count > 0) {
                throw ApiException.conflict("ESG-409-IN-USE", "This organisation still has " + count + " " + ref[1]
                        + ". Move or remove them first.");
            }
        }
        jdbc.update("update auth.organisations set deleted_at = now(), is_active = false where id = ?", id);
    }

    // ------------------------------------------------------------------ internals

    private OrganisationResponse find(UUID id) {
        List<OrganisationResponse> rows = jdbc.query(
                "select " + COLUMNS + " from auth.organisations where id = ? and deleted_at is null", MAPPER, id);
        if (rows.isEmpty() || !orgScope.canAccess(id)) {
            throw ApiException.notFound("Organisation");
        }
        return rows.get(0);
    }

    /** Root-level organisations need tenant-wide access; children need access to the parent. */
    private void requireParentAccess(UUID parentId) {
        if (parentId == null) {
            if (orgScope.accessibleOrgIds().isPresent()) {
                throw ApiException.forbidden("Only users with tenant-wide access can create top-level organisations");
            }
            return;
        }
        Long exists = jdbc.queryForObject("select count(*) from auth.organisations where id = ? and deleted_at is null",
                Long.class, parentId);
        if (exists == null || exists == 0) {
            throw FieldValidationException.of("parentOrgId", "Unknown parent organisation");
        }
        orgScope.requireAccess(parentId);
    }

    private Set<UUID> descendants(UUID id) {
        return Set.copyOf(jdbc.queryForList("with recursive tree as (select id from auth.organisations where parent_org_id = ? "
                + "union select o.id from auth.organisations o join tree t on o.parent_org_id = t.id) select id from tree",
                UUID.class, id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
