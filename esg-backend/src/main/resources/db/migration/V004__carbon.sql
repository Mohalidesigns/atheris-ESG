-- ============================================================
-- carbon schema (TRD §4.4). emission_records partitioned by year like data_points (TRD §4.7.2).
-- emission_factors mixes global rows (tenant_id NULL) with tenant custom rows; its RLS policy is
-- defined in V008 (read global + own, write own only).
-- ============================================================

CREATE TABLE carbon.emission_factors (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  region       VARCHAR(100) NOT NULL,
  category     VARCHAR(100) NOT NULL,
  sub_category VARCHAR(200),
  factor_value DECIMAL(20,10) NOT NULL CHECK (factor_value >= 0),
  unit         VARCHAR(50) NOT NULL,
  source       VARCHAR(255),
  valid_from   DATE NOT NULL,
  valid_to     DATE,
  is_custom    BOOLEAN NOT NULL DEFAULT FALSE,
  tenant_id    UUID REFERENCES auth.tenants(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   UUID DEFAULT auth.current_user_id(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   UUID,
  version      BIGINT NOT NULL DEFAULT 0,
  CHECK (is_custom = (tenant_id IS NOT NULL))
);
CREATE INDEX idx_emission_factors_lookup ON carbon.emission_factors(region, category, valid_from);
CREATE INDEX idx_emission_factors_tenant ON carbon.emission_factors(tenant_id);

CREATE TABLE carbon.emission_records (
  id                     UUID NOT NULL DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  org_id                 UUID NOT NULL,
  scope                  VARCHAR(10) NOT NULL CHECK (scope IN ('scope_1', 'scope_2', 'scope_3')),
  scope3_category        INT CHECK (scope3_category BETWEEN 1 AND 15),
  source_activity        VARCHAR(200) NOT NULL,
  activity_data          DECIMAL(20,6) NOT NULL,
  activity_unit          VARCHAR(50) NOT NULL,
  emission_factor_id     UUID REFERENCES carbon.emission_factors(id),
  co2e_tonnes            DECIMAL(20,6) NOT NULL,
  co2_tonnes             DECIMAL(20,6),
  ch4_tonnes             DECIMAL(20,6),
  n2o_tonnes             DECIMAL(20,6),
  reporting_period_start DATE NOT NULL,
  reporting_period_end   DATE NOT NULL,
  calculation_method     VARCHAR(50),
  data_quality_score     DECIMAL(3,2),
  is_verified            BOOLEAN NOT NULL DEFAULT FALSE,
  verified_by            UUID,
  evidence_ids           UUID[],
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  PRIMARY KEY (id, reporting_period_start),
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id),
  CHECK (reporting_period_end >= reporting_period_start)
) PARTITION BY RANGE (reporting_period_start);

CREATE TABLE carbon.emission_records_2025 PARTITION OF carbon.emission_records FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
CREATE TABLE carbon.emission_records_2026 PARTITION OF carbon.emission_records FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE carbon.emission_records_2027 PARTITION OF carbon.emission_records FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');
CREATE TABLE carbon.emission_records_default PARTITION OF carbon.emission_records DEFAULT;
CREATE INDEX idx_emission_records_tenant_scope ON carbon.emission_records(tenant_id, scope, reporting_period_start);

CREATE TABLE carbon.gas_flaring_records (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id          UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  facility_id        UUID NOT NULL,
  volume_mcf         DECIMAL(20,6) NOT NULL CHECK (volume_mcf >= 0),
  gas_composition    JSONB,
  co2e_tonnes        DECIMAL(20,6) NOT NULL,
  flaring_reason     VARCHAR(100),
  reporting_date     DATE NOT NULL,
  satellite_verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by         UUID DEFAULT auth.current_user_id(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by         UUID,
  version            BIGINT NOT NULL DEFAULT 0,
  deleted_at         TIMESTAMPTZ,
  FOREIGN KEY (tenant_id, facility_id) REFERENCES auth.organisations(tenant_id, id)
);
CREATE INDEX idx_gas_flaring_tenant ON carbon.gas_flaring_records(tenant_id, reporting_date);

CREATE TABLE carbon.reduction_targets (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  target_name   VARCHAR(255) NOT NULL,
  target_type   VARCHAR(50),
  scope         VARCHAR(10) NOT NULL,
  base_year     INT NOT NULL,
  base_value    DECIMAL(20,6) NOT NULL,
  target_year   INT NOT NULL,
  target_value  DECIMAL(20,6) NOT NULL,
  current_value DECIMAL(20,6),
  progress_pct  DECIMAL(5,2),
  status        VARCHAR(30) NOT NULL DEFAULT 'on_track',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    UUID DEFAULT auth.current_user_id(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    UUID,
  version       BIGINT NOT NULL DEFAULT 0,
  deleted_at    TIMESTAMPTZ,
  CHECK (target_year > base_year)
);
