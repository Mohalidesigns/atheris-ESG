package com.esg.notifications;

import com.esg.exception.ApiException;
import com.esg.notifications.NotificationDtos.InAppNotification;
import com.esg.notifications.NotificationDtos.NotificationPage;
import com.esg.notifications.NotificationDtos.Preference;
import com.esg.notifications.NotificationDtos.PreferenceUpdate;
import com.esg.security.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Notification service (TRD §6.6.3): renders templates per channel in the user's language, respects per-type
 * channel preferences, writes the in-app notification immediately and queues email/SMS in the delivery log
 * (sent by {@link NotificationDispatcher}). Defaults: in-app and email on, SMS opt-in.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");
    private static final List<String> CHANNELS = List.of("in_app", "email", "sms");

    private final JdbcTemplate jdbc;
    private final EmailSender email;
    private final SmsSender sms;

    private final TransactionTemplate requiresNew;

    public NotificationService(JdbcTemplate jdbc, EmailSender email, SmsSender sms, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.email = email;
        this.sms = sms;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Fills {{name}} placeholders; unknown names become empty text. */
    static String render(String template, Map<String, String> vars) {
        if (template == null) {
            return null;
        }
        Matcher m = VAR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), "")));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Notifies one user in the current tenant <em>after</em> the caller's transaction commits, in a transaction of its
     * own. So nobody is told about a change that rolled back, and a notification problem can never break the business
     * operation (in PostgreSQL one failed statement would abort the caller's whole transaction).
     */
    public void notifyAfterCommit(UUID userId, String type, Map<String, String> variables) {
        Runnable send = () -> {
            try {
                requiresNew.executeWithoutResult(status -> doNotify(userId, type, variables));
            } catch (RuntimeException e) {
                log.warn("Notification {} for user {} failed: {}", type, userId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void doNotify(UUID userId, String type, Map<String, String> variables) {
        List<Map<String, Object>> users = jdbc.queryForList("select u.email, u.phone, u.first_name, u.preferred_language, t.name as tenant_name "
                + "from auth.users u join auth.tenants t on t.id = u.tenant_id where u.id = ? and u.is_active", userId);
        if (users.isEmpty()) {
            return;
        }
        Map<String, Object> user = users.get(0);
        Map<String, String> vars = new HashMap<>(variables);
        vars.putIfAbsent("firstName", (String) user.get("first_name"));
        vars.putIfAbsent("tenantName", (String) user.get("tenant_name"));
        String locale = user.get("preferred_language") == null ? "en" : (String) user.get("preferred_language");
        Map<String, Boolean> prefs = preferencesFor(userId, type);

        for (String channel : CHANNELS) {
            if (!prefs.get(channel)) {
                continue;
            }
            String[] template = template(type, channel, locale);
            if (template == null) {
                continue;
            }
            String subject = render(template[0], vars);
            String body = render(template[1], vars);
            switch (channel) {
                case "in_app" -> jdbc.update("insert into notifications.notification_queue (user_id, title, message, notification_type) "
                        + "values (?, ?, ?, ?)", userId, subject == null ? type : subject, body, type);
                case "email" -> queue(userId, type, "email", (String) user.get("email"), subject, body);
                case "sms" -> {
                    if (user.get("phone") != null) {
                        queue(userId, type, "sms", (String) user.get("phone"), null, body);
                    }
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ in-app inbox (own notifications only)

    @Transactional(readOnly = true)
    public NotificationPage inbox(boolean unreadOnly, int page, int size) {
        UUID me = currentUser();
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        String filter = " where user_id = ?" + (unreadOnly ? " and not is_read" : "");
        Long total = jdbc.queryForObject("select count(*) from notifications.notification_queue" + filter, Long.class, me);
        List<InAppNotification> rows = jdbc.query("select id, notification_type, title, message, link, is_read, created_at "
                        + "from notifications.notification_queue" + filter + " order by created_at desc limit ? offset ?",
                (rs, i) -> new InAppNotification(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6), rs.getTimestamp(7).toInstant()),
                me, safeSize, (long) safePage * safeSize);
        return new NotificationPage(rows, unreadCount(), safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        Long count = jdbc.queryForObject("select count(*) from notifications.notification_queue where user_id = ? and not is_read",
                Long.class, currentUser());
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(UUID id) {
        int updated = jdbc.update("update notifications.notification_queue set is_read = true, read_at = coalesce(read_at, now()) "
                + "where id = ? and user_id = ?", id, currentUser());
        if (updated == 0) {
            throw ApiException.notFound("Notification");
        }
    }

    @Transactional
    public void markAllRead() {
        jdbc.update("update notifications.notification_queue set is_read = true, read_at = now() where user_id = ? and not is_read",
                currentUser());
    }

    // ------------------------------------------------------------------ preferences

    @Transactional(readOnly = true)
    public List<Preference> preferences() {
        UUID me = currentUser();
        List<Preference> result = new ArrayList<>();
        Map<String, List<String>> typesToChannels = new LinkedHashMap<>();
        jdbc.query("select distinct notification_type, channel from notifications.notification_templates order by 1, 2",
                rs -> { typesToChannels.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2)); });
        for (Map.Entry<String, List<String>> e : typesToChannels.entrySet()) {
            Map<String, Boolean> prefs = preferencesFor(me, e.getKey());
            for (String channel : CHANNELS) {
                if (e.getValue().contains(channel)) {
                    result.add(new Preference(e.getKey(), channel, prefs.get(channel), available(channel), "sms".equals(channel) && sms.sandbox()));
                }
            }
        }
        return result;
    }

    @Transactional
    public List<Preference> updatePreferences(List<PreferenceUpdate> updates) {
        UUID me = currentUser();
        for (PreferenceUpdate u : updates) {
            jdbc.update("insert into notifications.user_preferences (user_id, notification_type, channel, enabled) values (?, ?, ?, ?) "
                    + "on conflict (user_id, notification_type, channel) do update set enabled = excluded.enabled",
                    me, u.type(), u.channel(), u.enabled());
        }
        return preferences();
    }

    // ------------------------------------------------------------------ internals

    private boolean available(String channel) {
        return switch (channel) {
            case "email" -> email.configured();
            case "sms" -> sms.configured();
            default -> true;
        };
    }

    private Map<String, Boolean> preferencesFor(UUID userId, String type) {
        Map<String, Boolean> prefs = new HashMap<>(Map.of("in_app", true, "email", true, "sms", false));
        jdbc.query("select channel, enabled from notifications.user_preferences where user_id = ? and notification_type = ?",
                rs -> { prefs.put(rs.getString(1), rs.getBoolean(2)); }, userId, type);
        return prefs;
    }

    private String[] template(String type, String channel, String locale) {
        List<String[]> rows = jdbc.query("select subject, body from notifications.notification_templates where notification_type = ? "
                + "and channel = ? and locale in (?, 'en') order by (locale = ?) desc limit 1",
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2)}, type, channel, locale, locale);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void queue(UUID userId, String type, String channel, String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        jdbc.update("insert into notifications.delivery_log (user_id, notification_type, channel, recipient, subject, body) "
                + "values (?, ?, ?, ?, ?, ?)", userId, type, channel, recipient, subject, body);
    }

    private static UUID currentUser() {
        String id = TenantContext.getUserId();
        if (id == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return UUID.fromString(id);
    }
}
