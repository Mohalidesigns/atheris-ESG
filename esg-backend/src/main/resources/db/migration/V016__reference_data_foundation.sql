-- ============================================================
-- Reference data foundation (WP-17, TRD §4.6, §8.2; BRD §5.2.1-06/07).
--
-- Global catalogues live in the `reference` schema: read-only for the app, written only by repeatable migrations
-- (R__reference_*.sql), which are the single source of truth and keep every value with its source.
-- Emission factors gain provenance and versioning: a published value is never edited in place; a correction is a
-- new factor_version row and the old one gets valid_to, so past calculations keep pointing at what they used.
-- ============================================================

CREATE OR REPLACE FUNCTION audit.log_change() RETURNS trigger
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, audit, auth
  AS $$
DECLARE
  redacted CONSTANT TEXT[] := ARRAY['password_hash', 'mfa_secret', 'refresh_token_hash', 'connection_config'];
  noise    CONSTANT TEXT[] := ARRAY['updated_at', 'updated_by', 'version'];
  old_row  JSONB := CASE WHEN TG_OP <> 'INSERT' THEN to_jsonb(OLD) - redacted END;
  new_row  JSONB := CASE WHEN TG_OP <> 'DELETE' THEN to_jsonb(NEW) - redacted END;
  row_data JSONB := COALESCE(new_row, old_row);
  changed  TEXT[];
  tenant   UUID;
  key      TEXT;
BEGIN
  IF TG_OP = 'UPDATE' THEN
    SELECT array_agg(k) INTO changed FROM jsonb_object_keys(new_row) k
    WHERE NOT (k = ANY (noise)) AND (old_row -> k) IS DISTINCT FROM (new_row -> k);
    IF changed IS NULL THEN
      RETURN NULL;
    END IF;
    old_row := (SELECT jsonb_object_agg(k, old_row -> k) FROM unnest(changed) k);
    new_row := (SELECT jsonb_object_agg(k, new_row -> k) FROM unnest(changed) k);
  END IF;
  tenant := CASE WHEN TG_TABLE_SCHEMA = 'auth' AND TG_TABLE_NAME = 'tenants'
                 THEN (row_data ->> 'id')::uuid ELSE (row_data ->> 'tenant_id')::uuid END;
  -- Global reference rows (tenant_id NULL: platform emission factors, templates) are versioned in repeatable
  -- migrations, so git is their audit trail; tenant audit logs require a tenant (V016, D19).
  IF tenant IS NULL THEN
    RETURN NULL;
  END IF;
  INSERT INTO audit.audit_logs (tenant_id, user_id, action, resource, resource_id, old_value, new_value, ip_address, user_agent)
  VALUES (tenant, auth.current_user_id(), lower(TG_OP), TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME,
          CASE WHEN row_data ? 'id' THEN (row_data ->> 'id')::uuid END,
          old_row, new_row, auth.current_client_ip(), left(auth.current_user_agent(), 500));
  RETURN NULL;
END $$;

CREATE SCHEMA reference;
GRANT USAGE ON SCHEMA reference TO ${app_role};

CREATE TABLE reference.currencies (
  code       CHAR(3) PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'),
  name       VARCHAR(100) NOT NULL,
  symbol     VARCHAR(10),
  minor_unit SMALLINT NOT NULL CHECK (minor_unit BETWEEN 0 AND 4),
  source     VARCHAR(255) NOT NULL
);

CREATE TABLE reference.countries (
  code      CHAR(2) PRIMARY KEY CHECK (code ~ '^[A-Z]{2}$'),
  name      VARCHAR(100) NOT NULL,
  region    VARCHAR(50) NOT NULL,          -- emission-factor region used for fallback lookups
  currency  CHAR(3) REFERENCES reference.currencies(code),
  source    VARCHAR(255) NOT NULL
);

CREATE TABLE reference.sdgs (
  goal   SMALLINT PRIMARY KEY CHECK (goal BETWEEN 1 AND 17),
  title  VARCHAR(100) NOT NULL,
  source VARCHAR(255) NOT NULL
);

CREATE TABLE reference.scope3_categories (
  category  SMALLINT PRIMARY KEY CHECK (category BETWEEN 1 AND 15),
  name      VARCHAR(150) NOT NULL,
  direction VARCHAR(10) NOT NULL CHECK (direction IN ('upstream', 'downstream')),
  source    VARCHAR(255) NOT NULL
);

CREATE TABLE reference.sectors (
  code           VARCHAR(10) PRIMARY KEY,
  name           VARCHAR(200) NOT NULL,
  classification VARCHAR(30) NOT NULL,
  source         VARCHAR(255) NOT NULL
);

-- Metric catalogue: drives Data Hub forms (metric_key, unit, data type) and framework coverage.
CREATE TABLE reference.metrics (
  metric_key  VARCHAR(100) PRIMARY KEY CHECK (metric_key ~ '^[a-z][a-z0-9_]*$'),
  pillar      CHAR(1) NOT NULL CHECK (pillar IN ('E', 'S', 'G')),
  category    VARCHAR(100) NOT NULL,
  name        VARCHAR(200) NOT NULL,
  description TEXT,
  unit        VARCHAR(30) NOT NULL,
  data_type   VARCHAR(20) NOT NULL CHECK (data_type IN ('decimal', 'integer', 'percentage', 'currency', 'boolean', 'text')),
  source      VARCHAR(255) NOT NULL,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order  INT NOT NULL DEFAULT 0
);

-- Frameworks and requirements gain provenance; requirement rows are upserted by (framework, code) and never deleted,
-- because tenant compliance status rows reference them.
ALTER TABLE compliance.regulatory_frameworks
  ADD COLUMN content_status VARCHAR(20) NOT NULL DEFAULT 'pending_sme'
    CHECK (content_status IN ('pending_sme', 'seeded', 'sme_verified')),
  ADD COLUMN source VARCHAR(500);
ALTER TABLE compliance.framework_requirements
  ADD COLUMN source VARCHAR(500),
  ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'pending_sme'
    CHECK (verification_status IN ('pending_sme', 'sme_verified')),
  ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE reference.metric_requirements (
  metric_key     VARCHAR(100) NOT NULL REFERENCES reference.metrics(metric_key),
  requirement_id UUID NOT NULL REFERENCES compliance.framework_requirements(id),
  PRIMARY KEY (metric_key, requirement_id)
);

CREATE TABLE compliance.requirement_mappings (
  source_requirement_id UUID NOT NULL REFERENCES compliance.framework_requirements(id),
  target_requirement_id UUID NOT NULL REFERENCES compliance.framework_requirements(id),
  relation              VARCHAR(20) NOT NULL CHECK (relation IN ('equivalent', 'partial', 'related')),
  notes                 VARCHAR(500),
  source                VARCHAR(500) NOT NULL,
  PRIMARY KEY (source_requirement_id, target_requirement_id),
  CHECK (source_requirement_id <> target_requirement_id)
);

-- Emission factor provenance and versioning (TRD §8.2 EmissionFactorManager).
ALTER TABLE carbon.emission_factors
  ADD COLUMN factor_version      VARCHAR(20) NOT NULL DEFAULT '1',
  ADD COLUMN source_year         INT CHECK (source_year BETWEEN 1990 AND 2100),
  ADD COLUMN gwp_set             VARCHAR(10) CHECK (gwp_set IN ('AR4', 'AR5', 'AR6')),
  ADD COLUMN ghg_basis           VARCHAR(10) NOT NULL DEFAULT 'CO2e' CHECK (ghg_basis IN ('CO2', 'CO2e')),
  ADD COLUMN derivation          TEXT,
  ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'pending_sme'
    CHECK (verification_status IN ('pending_sme', 'sme_verified', 'tenant_provided')),
  ADD CONSTRAINT emission_factors_valid_range CHECK (valid_to IS NULL OR valid_to >= valid_from);
-- Every platform value has a source; tenant custom factors must name theirs too.
UPDATE carbon.emission_factors SET source = 'Unspecified (legacy seed)' WHERE source IS NULL;
ALTER TABLE carbon.emission_factors ALTER COLUMN source SET NOT NULL;
UPDATE carbon.emission_factors SET verification_status = 'tenant_provided' WHERE tenant_id IS NOT NULL;
ALTER TABLE carbon.emission_factors ADD CONSTRAINT emission_factors_custom_status
  CHECK ((tenant_id IS NULL) = (verification_status <> 'tenant_provided'));
CREATE UNIQUE INDEX uq_emission_factors_global_version
  ON carbon.emission_factors (region, category, COALESCE(sub_category, ''), valid_from, factor_version)
  WHERE tenant_id IS NULL;

GRANT SELECT ON ALL TABLES IN SCHEMA reference TO ${app_role};
GRANT SELECT ON compliance.requirement_mappings TO ${app_role};
