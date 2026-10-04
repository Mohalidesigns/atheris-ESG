package com.esg.datahub;

import com.esg.audit.DeniedActionAuditor;
import com.esg.datahub.DataHubDtos.BulkActionRequest;
import com.esg.datahub.DataHubDtos.BulkActionResponse;
import com.esg.datahub.DataHubDtos.BulkItem;
import com.esg.datahub.DataHubDtos.BulkResult;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.CurrentUser;
import com.esg.security.OrgScopeService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 3 (2): bulk submit / validate / approve / reject. Each point goes through exactly the single-item step in its
 * own transaction — the same state, version, period-lock and segregation-of-duties checks — so one refusal never
 * blocks or undoes the others. Every refusal is reported per row and, when it is a denial (403, locked period…),
 * written to the audit trail like the single-item call would be.
 */
@Service
public class DataPointBulkService {

    private static final Logger log = LoggerFactory.getLogger(DataPointBulkService.class);
    // no-mock-gate: config — statuses each step applies to (DataPointService.requireStatus) and the permission it needs
    private static final Map<String, List<String>> FROM = Map.of(
            "submit", List.of(DataPointService.DRAFT, DataPointService.REJECTED),
            "validate", List.of(DataPointService.SUBMITTED),
            "approve", List.of(DataPointService.VALIDATED),
            "reject", List.of(DataPointService.SUBMITTED, DataPointService.VALIDATED));

    private final DataPointService points;
    private final DeniedActionAuditor denied;
    private final OrgScopeService orgScope;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate perRow;

    public DataPointBulkService(DataPointService points, DeniedActionAuditor denied, OrgScopeService orgScope, JdbcTemplate jdbc,
                                PlatformTransactionManager txManager) {
        this.points = points;
        this.denied = denied;
        this.orgScope = orgScope;
        this.jdbc = jdbc;
        this.perRow = new TransactionTemplate(txManager);
        this.perRow.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public BulkActionResponse run(BulkActionRequest r) {
        boolean byItems = r.items() != null && !r.items().isEmpty();
        if (byItems == (r.importJobId() != null)) {
            throw FieldValidationException.of("items", "Select points, or name one import — not both");
        }
        requirePermission(r.action());
        if ("reject".equals(r.action()) && (r.reason() == null || r.reason().isBlank())) {
            throw FieldValidationException.of("reason", "is required");
        }
        List<BulkItem> items = byItems ? r.items().stream().distinct().toList() : fromImport(r.importJobId(), r.action());

        List<BulkResult> results = new ArrayList<>(items.size());
        for (BulkItem item : items) {
            results.add(one(r.action(), item, r.reason()));
        }
        int ok = (int) results.stream().filter(BulkResult::ok).count();
        return new BulkActionResponse(r.action(), results.size(), ok, results.size() - ok, results);
    }

    private BulkResult one(String action, BulkItem item, String reason) {
        try {
            perRow.executeWithoutResult(s -> {
                switch (action) {
                    case "submit" -> points.submitPoint(item.id(), item.version());
                    case "validate" -> points.validatePoint(item.id(), item.version());
                    case "approve" -> points.approvePoint(item.id(), item.version());
                    default -> points.rejectPoint(item.id(), item.version(), reason);
                }
            });
            String status = jdbc.queryForObject("select validation_status from data_hub.data_points where id = ?", String.class, item.id());
            return new BulkResult(item.id(), true, status, null, null);
        } catch (FieldValidationException e) {
            String message = e.getErrors().entrySet().stream().map(x -> x.getKey() + ": " + x.getValue()).collect(Collectors.joining("; "));
            return refused(action, item, e.getStatus(), e.getCode(), message);
        } catch (ApiException e) {
            return refused(action, item, e.getStatus(), e.getCode(), e.getMessage());
        } catch (DataAccessException e) {
            String state = sqlState(e);
            if ("P0L01".equals(state) || "P0L02".equals(state)) {
                return refused(action, item, HttpStatus.CONFLICT, "ESG-409-LOCKED", "The reporting period is locked");
            }
            if ("42501".equals(state)) {
                return refused(action, item, HttpStatus.FORBIDDEN, "ESG-403", "You do not have permission to perform this action");
            }
            log.warn("Bulk {} failed for data point {}", action, item.id(), e);
            return new BulkResult(item.id(), false, null, "ESG-500", "Couldn't process this point");
        }
    }

    private BulkResult refused(String action, BulkItem item, HttpStatus status, String code, String message) {
        denied.recordAction("PUT", "/v1/data/points/{id}/" + action, item.id(), status, code, message);
        return new BulkResult(item.id(), false, null, code, message);
    }

    /** The points of one import the step applies to now, with their current versions (in the caller's org scope). */
    private List<BulkItem> fromImport(UUID importJobId, String action) {
        List<BulkItem> items = new ArrayList<>();
        OrgScopeService.QueryScope scope = orgScope.queryScope();
        jdbc.query(con -> {
            var ps = con.prepareStatement("select id, version, org_id from data_hub.data_points where import_job_id = ? "
                    + "and deleted_at is null and validation_status = any(?) order by created_at, id limit ?");
            ps.setObject(1, importJobId);
            ps.setArray(2, con.createArrayOf("varchar", FROM.get(action).toArray()));
            ps.setInt(3, DataHubDtos.BULK_MAX_IMPORT);
            return ps;
        }, rs -> {
            UUID org = rs.getObject(3, UUID.class);
            if (scope.all() || scope.orgIds().contains(org)) {
                items.add(new BulkItem(rs.getObject(1, UUID.class), rs.getLong(2)));
            }
        });
        if (items.isEmpty()) {
            throw ApiException.conflict("ESG-409-STATE", "No points of this import can be " + past(action) + " now");
        }
        return items;
    }

    private static void requirePermission(String action) {
        boolean allowed = switch (action) {
            case "submit" -> CurrentUser.hasPermission("data_points:create") || CurrentUser.hasPermission("data_points:update");
            case "validate" -> CurrentUser.hasPermission("data_points:validate");
            case "approve" -> CurrentUser.hasPermission("data_points:approve");
            default -> CurrentUser.hasPermission("data_points:validate") || CurrentUser.hasPermission("data_points:approve");
        };
        if (!allowed) {
            throw ApiException.forbidden("You can't " + action + " data points");
        }
    }

    private static String past(String action) {
        return switch (action) {
            case "submit" -> "submitted";
            case "validate" -> "validated";
            case "approve" -> "approved";
            default -> "rejected";
        };
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && s.getSQLState() != null) {
                return s.getSQLState();
            }
        }
        return "";
    }
}
