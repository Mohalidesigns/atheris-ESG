package com.esg.datahub;

import com.esg.model.entity.DataPoint;
import com.esg.notifications.NotificationService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * D51: tells people when data points need them. Submitted → whoever may validate the point's organisation; validated →
 * whoever may approve it (never the submitter or the validator: segregation of duties); rejected → the submitter.
 *
 * <p>Reviewers are the holders of {@code data_points:validate} / {@code data_points:approve} through a role that covers
 * the point's organisation (unscoped, or scoped to it or an ancestor). Administrators hold every permission, so they
 * are told only when nobody else can act — a request never goes nowhere.
 *
 * <p>A bulk action ({@link #batched}) sends one notice per person and type with the number of points, not one per point.
 * Only committed changes count: nobody is told about a step that rolled back.
 */
@Component
public class DataPointNotifier {

    static final String VALIDATION_REQUESTED = "DATA_VALIDATION_REQUESTED";
    static final String APPROVAL_REQUESTED = "DATA_APPROVAL_REQUESTED";
    static final String REJECTED = "DATA_POINTS_REJECTED";
    static final Set<String> ADMIN_ROLES = Set.of("SUPER_ADMIN", "TENANT_ADMIN");

    private record Key(UUID user, String type) {}

    private static final class Tally {
        int count;
        String reason;
    }

    private static final ThreadLocal<Map<Key, Tally>> BATCH = new ThreadLocal<>();

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final String link;

    public DataPointNotifier(JdbcTemplate jdbc, NotificationService notifications,
                             @Value("${esg.app.public-url:http://localhost:5173}") String publicUrl) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.link = publicUrl.replaceAll("/+$", "") + "/data-hub";
    }

    /** Runs a bulk action, then sends one notice per person and type for everything it committed. */
    public <T> T batched(Supplier<T> work) {
        if (BATCH.get() != null) {
            return work.get();
        }
        Map<Key, Tally> batch = new LinkedHashMap<>();
        BATCH.set(batch);
        try {
            return work.get();
        } finally {
            BATCH.remove();
            batch.forEach((k, t) -> send(k.user(), k.type(), t.count, t.reason));
        }
    }

    /** Call inside the transaction that submitted the point. */
    void submitted(DataPoint dp, UUID submitter) {
        notifyAll(reviewers("validate", dp.getOrgId(), List.of(submitter)), VALIDATION_REQUESTED, null);
    }

    /** Call inside the transaction that validated the point. */
    void validated(DataPoint dp, UUID validator) {
        List<UUID> excluded = dp.getSubmittedBy() == null ? List.of(validator) : List.of(validator, dp.getSubmittedBy());
        notifyAll(reviewers("approve", dp.getOrgId(), excluded), APPROVAL_REQUESTED, null);
    }

    /** Call inside the transaction that rejected the point. */
    void rejected(DataPoint dp, UUID rejectedBy, String reason) {
        UUID to = dp.getSubmittedBy() != null ? dp.getSubmittedBy() : dp.getCreatedBy();
        if (to != null && !to.equals(rejectedBy)) {
            notifyAll(List.of(to), REJECTED, reason);
        }
    }

    /**
     * Active users holding {@code data_points:<action>} through a live role assignment that covers {@code orgId}, minus
     * {@code excluded}; administrators only if nobody else qualifies.
     */
    List<UUID> reviewers(String action, UUID orgId, Collection<UUID> excluded) {
        Set<UUID> people = new LinkedHashSet<>();
        Set<UUID> admins = new LinkedHashSet<>();
        jdbc.query("with recursive up as ("
                + " select id, parent_org_id from auth.organisations where id = ?"
                + " union select o.id, o.parent_org_id from auth.organisations o join up on o.id = up.parent_org_id)"
                + " select distinct u.id, r.name from auth.users u"
                + " join auth.user_roles ur on ur.user_id = u.id and (ur.expires_at is null or ur.expires_at > now())"
                + " join auth.roles r on r.id = ur.role_id"
                + " join auth.role_permissions rp on rp.role_id = r.id"
                + " join auth.permissions p on p.id = rp.permission_id"
                + " where u.is_active and u.deleted_at is null and p.resource = 'data_points' and p.action = ?"
                + " and (ur.org_scope_id is null or ur.org_scope_id in (select id from up))"
                + " order by 1",
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    (ADMIN_ROLES.contains(rs.getString(2)) ? admins : people).add(id);
                }, orgId, action);
        admins.removeAll(people);
        people.removeAll(excluded);
        admins.removeAll(excluded);
        return List.copyOf(people.isEmpty() ? admins : people);
    }

    private void notifyAll(List<UUID> users, String type, String reason) {
        Map<Key, Tally> batch = BATCH.get();
        if (batch == null) {
            users.forEach(u -> send(u, type, 1, reason)); // NotificationService itself waits for this transaction to commit
            return;
        }
        Runnable count = () -> users.forEach(u -> {
            Tally t = batch.computeIfAbsent(new Key(u, type), k -> new Tally());
            t.count++;
            t.reason = reason;
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    count.run();
                }
            });
        } else {
            count.run();
        }
    }

    private void send(UUID user, String type, int count, String reason) {
        notifications.notifyAfterCommit(user, type, Map.of("count", String.valueOf(count), "link", link,
                "reason", reason == null ? "" : reason));
    }
}
