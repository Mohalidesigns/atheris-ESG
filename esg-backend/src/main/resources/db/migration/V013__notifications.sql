-- ============================================================
-- Notification service (WP-15, TRD §4.6 notifications schema, §6.6.3): templates, user preferences,
-- in-app queue (notification_queue, V007) and a delivery log that doubles as the email/SMS outbox.
-- The dispatcher claims deliveries across tenants through SECURITY DEFINER functions.
-- ============================================================

-- Global templates (no tenant). {{variable}} placeholders; one row per type × channel × locale.
CREATE TABLE notifications.notification_templates (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  notification_type VARCHAR(100) NOT NULL,
  channel           VARCHAR(20) NOT NULL CHECK (channel IN ('in_app', 'email', 'sms')),
  locale            VARCHAR(5) NOT NULL DEFAULT 'en',
  subject           VARCHAR(255),
  body              TEXT NOT NULL,
  UNIQUE (notification_type, channel, locale)
);

CREATE TABLE notifications.user_preferences (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  user_id           UUID NOT NULL,
  notification_type VARCHAR(100) NOT NULL,
  channel           VARCHAR(20) NOT NULL CHECK (channel IN ('in_app', 'email', 'sms')),
  enabled           BOOLEAN NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by        UUID DEFAULT auth.current_user_id(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by        UUID,
  version           BIGINT NOT NULL DEFAULT 0,
  UNIQUE (user_id, notification_type, channel),
  FOREIGN KEY (tenant_id, user_id) REFERENCES auth.users(tenant_id, id) ON DELETE CASCADE
);

ALTER TABLE notifications.notification_queue ADD CONSTRAINT notification_queue_tenant_id_unique UNIQUE (tenant_id, id);

CREATE TABLE notifications.delivery_log (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  user_id           UUID NOT NULL,
  notification_type VARCHAR(100) NOT NULL,
  channel           VARCHAR(20) NOT NULL CHECK (channel IN ('email', 'sms')),
  recipient         VARCHAR(255) NOT NULL,
  subject           VARCHAR(255),
  body              TEXT NOT NULL,
  -- pending → sending → sent | failed (after retries) | skipped (channel not configured: never faked as sent)
  status            VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'sending', 'sent', 'failed', 'skipped')),
  attempts          INT NOT NULL DEFAULT 0,
  last_error        VARCHAR(500),
  next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at           TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_delivery_log_due ON notifications.delivery_log(status, next_attempt_at);

SELECT auth.apply_tenant_rls('notifications.user_preferences');
SELECT auth.apply_tenant_rls('notifications.delivery_log');
SELECT auth.apply_audit_trigger('notifications.user_preferences');
CREATE POLICY owner_dispatch ON notifications.delivery_log FOR ALL TO CURRENT_USER USING (true) WITH CHECK (true);

GRANT SELECT ON notifications.notification_templates TO ${app_role};
GRANT SELECT, INSERT, UPDATE, DELETE ON notifications.user_preferences TO ${app_role};
GRANT SELECT, INSERT ON notifications.delivery_log TO ${app_role};

-- Dispatcher: claim due deliveries across tenants (skipping rows another node holds) and record outcomes.
CREATE FUNCTION notifications.claim_deliveries(p_limit INT)
  RETURNS TABLE (id UUID, tenant_id UUID, channel VARCHAR, recipient VARCHAR, subject VARCHAR, body TEXT, attempts INT)
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, notifications
  AS $$
BEGIN
  RETURN QUERY
    UPDATE notifications.delivery_log d SET status = 'sending', attempts = d.attempts + 1
    WHERE d.id IN (SELECT x.id FROM notifications.delivery_log x
                   WHERE x.status = 'pending' AND x.next_attempt_at <= now()
                   ORDER BY x.next_attempt_at LIMIT p_limit FOR UPDATE SKIP LOCKED)
    RETURNING d.id, d.tenant_id, d.channel, d.recipient, d.subject, d.body, d.attempts;
END $$;

CREATE FUNCTION notifications.complete_delivery(p_id UUID, p_status VARCHAR, p_error VARCHAR, p_retry_at TIMESTAMPTZ)
  RETURNS void
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, notifications
  AS $$
    UPDATE notifications.delivery_log
    SET status = p_status, last_error = left(p_error, 500),
        sent_at = CASE WHEN p_status = 'sent' THEN now() END,
        next_attempt_at = COALESCE(p_retry_at, next_attempt_at)
    WHERE id = p_id AND status = 'sending'
  $$;

REVOKE ALL ON FUNCTION notifications.claim_deliveries(INT), notifications.complete_delivery(UUID, VARCHAR, VARCHAR, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION notifications.claim_deliveries(INT), notifications.complete_delivery(UUID, VARCHAR, VARCHAR, TIMESTAMPTZ) TO ${app_role};
