-- ============================================================
-- File service (WP-15, TRD §4.6 files schema, §6.6.4): metadata, immutable versions, access log.
-- Bytes live in object storage (S3/MinIO with server-side encryption) or, for local development, the filesystem.
-- ============================================================

CREATE TABLE files.file_metadata (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  original_name   VARCHAR(255) NOT NULL,
  content_type    VARCHAR(150) NOT NULL,
  size_bytes      BIGINT NOT NULL CHECK (size_bytes >= 0),
  sha256          CHAR(64) NOT NULL,
  current_version INT NOT NULL DEFAULT 1,
  resource_type   VARCHAR(100),
  resource_id     UUID,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by      UUID DEFAULT auth.current_user_id(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by      UUID,
  version         BIGINT NOT NULL DEFAULT 0,
  deleted_at      TIMESTAMPTZ,
  UNIQUE (tenant_id, id)
);
CREATE INDEX idx_file_metadata_resource ON files.file_metadata(tenant_id, resource_type, resource_id);

CREATE TABLE files.file_versions (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  file_id      UUID NOT NULL,
  version_no   INT NOT NULL CHECK (version_no >= 1),
  storage_key  VARCHAR(500) NOT NULL,
  content_type VARCHAR(150) NOT NULL,
  size_bytes   BIGINT NOT NULL CHECK (size_bytes >= 0),
  sha256       CHAR(64) NOT NULL,
  -- not_scanned until a virus scanner is configured (WP-52); infected files are rejected before storage.
  scan_status  VARCHAR(20) NOT NULL DEFAULT 'not_scanned' CHECK (scan_status IN ('not_scanned', 'clean')),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   UUID DEFAULT auth.current_user_id(),
  UNIQUE (file_id, version_no),
  FOREIGN KEY (tenant_id, file_id) REFERENCES files.file_metadata(tenant_id, id)
);

CREATE TABLE files.file_access_log (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id  UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  file_id    UUID NOT NULL,
  version_no INT,
  user_id    UUID DEFAULT auth.current_user_id(),
  action     VARCHAR(20) NOT NULL CHECK (action IN ('upload', 'download', 'download_url', 'delete')),
  ip_address INET,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id, file_id) REFERENCES files.file_metadata(tenant_id, id)
);
CREATE INDEX idx_file_access_log_file ON files.file_access_log(tenant_id, file_id, created_at);

SELECT auth.apply_tenant_rls('files.file_metadata');
SELECT auth.apply_tenant_rls('files.file_versions');
SELECT auth.apply_tenant_rls('files.file_access_log');
SELECT auth.apply_audit_trigger('files.file_metadata');
GRANT SELECT, INSERT, UPDATE ON files.file_metadata TO ${app_role};
-- Versions and the access log are append-only for the application.
GRANT SELECT, INSERT ON files.file_versions, files.file_access_log TO ${app_role};
