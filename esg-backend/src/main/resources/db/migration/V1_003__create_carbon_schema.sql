-- ============================================================
-- ESG Pro Platform - Carbon Accounting Schema
-- TRD Section 4.4: Carbon Accounting
-- ============================================================

CREATE TABLE emission_factors (
    id              VARCHAR(36) PRIMARY KEY,
    region          VARCHAR(100) NOT NULL,
    category        VARCHAR(100) NOT NULL,
    sub_category    VARCHAR(200),
    factor_value    DECIMAL(20,10) NOT NULL,
    unit            VARCHAR(50) NOT NULL,
    source          VARCHAR(255),
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    is_custom       BOOLEAN DEFAULT FALSE,
    tenant_id       VARCHAR(36),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE emission_records (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    org_id          VARCHAR(36) NOT NULL REFERENCES organisations(id),
    scope           VARCHAR(10) NOT NULL,
    scope3_category INT,
    source_activity VARCHAR(200) NOT NULL,
    activity_data   DECIMAL(20,6) NOT NULL,
    activity_unit   VARCHAR(50) NOT NULL,
    emission_factor_id VARCHAR(36) REFERENCES emission_factors(id),
    co2e_tonnes     DECIMAL(20,6) NOT NULL,
    co2_tonnes      DECIMAL(20,6),
    ch4_tonnes      DECIMAL(20,6),
    n2o_tonnes      DECIMAL(20,6),
    reporting_period_start DATE NOT NULL,
    reporting_period_end   DATE NOT NULL,
    calculation_method VARCHAR(50),
    data_quality_score DECIMAL(3,2),
    is_verified     BOOLEAN DEFAULT FALSE,
    verified_by     VARCHAR(36),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gas_flaring_records (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    facility_id     VARCHAR(36) NOT NULL,
    volume_mcf      DECIMAL(20,6) NOT NULL,
    gas_composition TEXT,
    co2e_tonnes     DECIMAL(20,6) NOT NULL,
    flaring_reason  VARCHAR(100),
    reporting_date  DATE NOT NULL,
    satellite_verified BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reduction_targets (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenants(id),
    target_name     VARCHAR(255) NOT NULL,
    target_type     VARCHAR(50),
    scope           VARCHAR(10) NOT NULL,
    base_year       INT NOT NULL,
    base_value      DECIMAL(20,6) NOT NULL,
    target_year     INT NOT NULL,
    target_value    DECIMAL(20,6) NOT NULL,
    current_value   DECIMAL(20,6),
    progress_pct    DECIMAL(5,2),
    status          VARCHAR(30) DEFAULT 'on_track',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_emission_records_tenant ON emission_records(tenant_id);
CREATE INDEX idx_emission_records_scope ON emission_records(scope);
CREATE INDEX idx_emission_records_period ON emission_records(reporting_period_start);
CREATE INDEX idx_emission_factors_region ON emission_factors(region, category);
CREATE INDEX idx_gas_flaring_tenant ON gas_flaring_records(tenant_id);
CREATE INDEX idx_reduction_targets_tenant ON reduction_targets(tenant_id);
