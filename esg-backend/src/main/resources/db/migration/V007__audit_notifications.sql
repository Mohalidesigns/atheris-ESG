-- ============================================================
-- audit.audit_logs is append-only: the application role gets SELECT and INSERT only (V008), so
-- UPDATE/DELETE fail at the database level (WP-15 builds the writer and viewer).
-- notifications -> notifications.notification_queue (TRD §4.6 name).
-- ============================================================

CREATE TABLE audit.audit_logs (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  user_id     UUID DEFAULT auth.current_user_id(),
  action      VARCHAR(50) NOT NULL,
  resource    VARCHAR(100) NOT NULL,
  resource_id UUID,
  old_value   JSONB,
  new_value   JSONB,
  ip_address  INET,
  user_agent  VARCHAR(500),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_tenant_created ON audit.audit_logs(tenant_id, created_at);
CREATE INDEX idx_audit_logs_resource ON audit.audit_logs(tenant_id, resource, resource_id);

CREATE TABLE notifications.notification_queue (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  user_id           UUID NOT NULL,
  title             VARCHAR(255) NOT NULL,
  message           TEXT,
  notification_type VARCHAR(50),
  is_read           BOOLEAN NOT NULL DEFAULT FALSE,
  link              VARCHAR(500),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  read_at           TIMESTAMPTZ,
  FOREIGN KEY (tenant_id, user_id) REFERENCES auth.users(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_notification_queue_user ON notifications.notification_queue(tenant_id, user_id, is_read);
