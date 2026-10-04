-- ============================================================
-- WP-21: M2 Carbon Accounting to L5 (TRD §4.4, §8.2; BRD §5.2.2; design D22).
--   * GWP-100 values per IPCC assessment (reference.gwp_values, loaded by R__ref_07)
--   * per-gas emission factors (kg CO2 / CH4 / N2O per unit) and spend-based factors (kgCO2e per currency unit)
--   * every emission record keeps the factor id, version, value, unit, lookup level and GWP set it was calculated with
--   * records can come from an approved Data Hub point, a flaring record, or manual entry; recalculation on demand
--   * gas flaring by composition, energy attribute instruments (RECs/PPAs) for market-based Scope 2,
--     reduction targets with pathways, PCAF financed emissions positions
-- ============================================================

CREATE TABLE reference.gwp_values (
  gas      VARCHAR(10) NOT NULL CHECK (gas IN ('CO2', 'CH4', 'N2O')),
  gwp_set  VARCHAR(10) NOT NULL CHECK (gwp_set IN ('AR5', 'AR6')),
  value    DECIMAL(10,3) NOT NULL CHECK (value > 0),
  source   VARCHAR(255) NOT NULL,
  PRIMARY KEY (gas, gwp_set)
);
GRANT SELECT ON reference.gwp_values TO ${app_role};

-- Data Hub metric → carbon activity: an approved point of the metric becomes an emission record (TRD §6.5.1).
CREATE TABLE reference.metric_activities (
  metric_key      VARCHAR(100) PRIMARY KEY REFERENCES reference.metrics(metric_key),
  source_activity VARCHAR(100) NOT NULL,
  scope           VARCHAR(10) NOT NULL CHECK (scope IN ('scope_1', 'scope_2', 'scope_3')),
  scope3_category INT CHECK (scope3_category BETWEEN 1 AND 15),
  activity_unit   VARCHAR(50) NOT NULL
);
GRANT SELECT ON reference.metric_activities TO ${app_role};

-- ------------------------------------------------------------ factors
ALTER TABLE carbon.emission_factors
  ADD COLUMN co2_factor     DECIMAL(20,10) CHECK (co2_factor >= 0),
  ADD COLUMN ch4_factor     DECIMAL(20,10) CHECK (ch4_factor >= 0),
  ADD COLUMN n2o_factor     DECIMAL(20,10) CHECK (n2o_factor >= 0),
  ADD COLUMN factor_method  VARCHAR(20) NOT NULL DEFAULT 'activity' CHECK (factor_method IN ('activity', 'spend')),
  ADD COLUMN spend_currency VARCHAR(3) REFERENCES reference.currencies(code),
  ADD CONSTRAINT emission_factors_spend_currency CHECK ((factor_method = 'spend') = (spend_currency IS NOT NULL)),
  ADD CONSTRAINT emission_factors_gases_complete CHECK (
    (co2_factor IS NULL AND ch4_factor IS NULL AND n2o_factor IS NULL)
    OR (co2_factor IS NOT NULL AND ch4_factor IS NOT NULL AND n2o_factor IS NOT NULL));

-- ------------------------------------------------------------ emission records
ALTER TABLE carbon.emission_records
  ADD COLUMN factor_version     VARCHAR(20),
  ADD COLUMN factor_value       DECIMAL(20,10),
  ADD COLUMN factor_unit        VARCHAR(50),
  ADD COLUMN factor_level       VARCHAR(20) CHECK (factor_level IN ('custom', 'country', 'region', 'global', 'selected')),
  ADD COLUMN gwp_set            VARCHAR(10),
  ADD COLUMN record_source      VARCHAR(20) NOT NULL DEFAULT 'manual' CHECK (record_source IN ('manual', 'data_hub', 'flaring')),
  ADD COLUMN data_point_id      UUID,
  ADD COLUMN flaring_record_id  UUID,
  ADD COLUMN spend_amount       DECIMAL(20,6),
  ADD COLUMN spend_currency     VARCHAR(3) REFERENCES reference.currencies(code),
  ADD COLUMN fx_rate            DECIMAL(20,10),
  ADD COLUMN notes              VARCHAR(500),
  ADD COLUMN calculated_at      TIMESTAMPTZ;
-- Old free-text methods → the WP-21 set; client-supplied CO2e ('direct_measurement') is no longer accepted.
ALTER TABLE carbon.emission_records DISABLE TRIGGER assert_periods_open;
ALTER TABLE carbon.emission_records DISABLE TRIGGER audit_log;
DO $$
DECLARE t record;
BEGIN
  FOR t IN SELECT id FROM auth.tenants LOOP
    PERFORM set_config('app.current_tenant', t.id::text, true);
    UPDATE carbon.emission_records SET calculation_method = CASE
        WHEN calculation_method = 'spend_based' THEN 'spend_based'
        WHEN calculation_method IS NULL OR calculation_method NOT IN ('spend_based') THEN 'activity_based' END;
  END LOOP;
  PERFORM set_config('app.current_tenant', '', true);
END $$;
ALTER TABLE carbon.emission_records ENABLE TRIGGER assert_periods_open;
ALTER TABLE carbon.emission_records ENABLE TRIGGER audit_log;
ALTER TABLE carbon.emission_records ALTER COLUMN calculation_method SET DEFAULT 'activity_based';
ALTER TABLE carbon.emission_records ALTER COLUMN calculation_method SET NOT NULL;
ALTER TABLE carbon.emission_records ADD CONSTRAINT emission_records_method_check
  CHECK (calculation_method IN ('activity_based', 'spend_based', 'flaring_composition'));
ALTER TABLE carbon.emission_records ADD CONSTRAINT emission_records_scope3_category
  CHECK ((scope = 'scope_3') = (scope3_category IS NOT NULL));
CREATE UNIQUE INDEX uq_emission_records_data_point ON carbon.emission_records(tenant_id, data_point_id, reporting_period_start)
  WHERE data_point_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_emission_records_org_period ON carbon.emission_records(tenant_id, org_id, reporting_period_start)
  WHERE deleted_at IS NULL;

-- ------------------------------------------------------------ gas flaring (TRD §8.2 GasFlaringCalculator)
ALTER TABLE carbon.gas_flaring_records
  ADD COLUMN reporting_period_start DATE,
  ADD COLUMN reporting_period_end   DATE,
  ADD COLUMN combustion_efficiency  DECIMAL(5,4) NOT NULL DEFAULT 0.98 CHECK (combustion_efficiency > 0 AND combustion_efficiency <= 1),
  ADD COLUMN co2_tonnes             DECIMAL(20,6),
  ADD COLUMN ch4_tonnes             DECIMAL(20,6),
  ADD COLUMN gwp_set                VARCHAR(10),
  ADD COLUMN satellite_check        JSONB,
  ADD COLUMN notes                  VARCHAR(500);
UPDATE carbon.gas_flaring_records SET reporting_period_start = reporting_date, reporting_period_end = reporting_date
  WHERE reporting_period_start IS NULL;
ALTER TABLE carbon.gas_flaring_records ALTER COLUMN reporting_period_start SET NOT NULL;
ALTER TABLE carbon.gas_flaring_records ALTER COLUMN reporting_period_end SET NOT NULL;
ALTER TABLE carbon.gas_flaring_records ADD CONSTRAINT gas_flaring_period CHECK (reporting_period_end >= reporting_period_start);
ALTER TABLE carbon.gas_flaring_records ADD CONSTRAINT gas_flaring_reason
  CHECK (flaring_reason IS NULL OR flaring_reason IN ('routine', 'safety', 'upset', 'maintenance'));

-- ------------------------------------------------------------ RECs / PPAs (BRD-5.2.2-05, market-based Scope 2)
CREATE TABLE carbon.energy_instruments (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  org_id                 UUID NOT NULL,
  instrument_type        VARCHAR(20) NOT NULL CHECK (instrument_type IN ('rec', 'ppa', 'green_tariff')),
  energy_source          VARCHAR(20) NOT NULL CHECK (energy_source IN ('solar', 'wind', 'hydro', 'biomass', 'geothermal', 'other')),
  mwh                    DECIMAL(20,6) NOT NULL CHECK (mwh > 0),
  emission_factor        DECIMAL(20,10) NOT NULL DEFAULT 0 CHECK (emission_factor >= 0),
  reporting_period_start DATE NOT NULL,
  reporting_period_end   DATE NOT NULL,
  certificate_ref        VARCHAR(100),
  supplier               VARCHAR(255) NOT NULL,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by             UUID DEFAULT auth.current_user_id(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by             UUID,
  version                BIGINT NOT NULL DEFAULT 0,
  deleted_at             TIMESTAMPTZ,
  FOREIGN KEY (tenant_id, org_id) REFERENCES auth.organisations(tenant_id, id),
  CHECK (reporting_period_end >= reporting_period_start)
);
CREATE UNIQUE INDEX uq_energy_instruments_certificate ON carbon.energy_instruments(tenant_id, certificate_ref)
  WHERE certificate_ref IS NOT NULL AND deleted_at IS NULL;
SELECT auth.apply_tenant_rls('carbon.energy_instruments');
SELECT auth.apply_audit_trigger('carbon.energy_instruments');
SELECT auth.apply_audit_log_trigger('carbon.energy_instruments');
GRANT SELECT, INSERT, UPDATE ON carbon.energy_instruments TO ${app_role};

-- ------------------------------------------------------------ reduction targets (BRD-5.2.2-06/07)
ALTER TABLE carbon.reduction_targets
  ADD COLUMN pathway               VARCHAR(20) NOT NULL DEFAULT 'linear' CHECK (pathway IN ('linear', 'contraction')),
  ADD COLUMN sbti_aligned          BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN intensity_denominator VARCHAR(20) CHECK (intensity_denominator IN ('revenue', 'employees', 'production')),
  ADD COLUMN progress_year         INT;
UPDATE carbon.reduction_targets SET target_type = 'absolute' WHERE target_type IS NULL OR target_type NOT IN ('absolute', 'intensity');
ALTER TABLE carbon.reduction_targets ALTER COLUMN target_type SET NOT NULL;
ALTER TABLE carbon.reduction_targets ADD CONSTRAINT reduction_targets_type
  CHECK (target_type IN ('absolute', 'intensity') AND ((target_type = 'intensity') = (intensity_denominator IS NOT NULL)));
ALTER TABLE carbon.reduction_targets ADD CONSTRAINT reduction_targets_scope
  CHECK (scope IN ('scope_1', 'scope_2', 'scope_3', 'scope_1_2', 'scope_1_2_3'));
ALTER TABLE carbon.reduction_targets ADD CONSTRAINT reduction_targets_values
  CHECK (base_value > 0 AND target_value >= 0 AND target_value < base_value);
ALTER TABLE carbon.reduction_targets ADD CONSTRAINT reduction_targets_status
  CHECK (status IN ('on_track', 'off_track', 'achieved', 'no_data'));

-- ------------------------------------------------------------ PCAF financed emissions (BRD-5.2.2-08)
CREATE TABLE carbon.financed_positions (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id             UUID NOT NULL DEFAULT auth.current_tenant_id() REFERENCES auth.tenants(id),
  reporting_year        INT NOT NULL CHECK (reporting_year BETWEEN 2000 AND 2100),
  asset_class           VARCHAR(40) NOT NULL CHECK (asset_class IN ('listed_equity_bonds', 'business_loans_unlisted_equity',
                          'project_finance', 'commercial_real_estate', 'mortgages', 'motor_vehicle_loans')),
  counterparty          VARCHAR(255) NOT NULL,
  sector                VARCHAR(100),
  outstanding_amount    DECIMAL(20,2) NOT NULL CHECK (outstanding_amount >= 0),
  denominator_value     DECIMAL(20,2) NOT NULL CHECK (denominator_value > 0),
  currency              VARCHAR(3) NOT NULL REFERENCES reference.currencies(code),
  counterparty_emissions DECIMAL(20,6) NOT NULL CHECK (counterparty_emissions >= 0),
  data_quality_score    INT NOT NULL CHECK (data_quality_score BETWEEN 1 AND 5),
  emissions_source      VARCHAR(255) NOT NULL,
  attribution_factor    DECIMAL(12,10) NOT NULL,
  financed_emissions    DECIMAL(20,6) NOT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by            UUID DEFAULT auth.current_user_id(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by            UUID,
  version               BIGINT NOT NULL DEFAULT 0,
  deleted_at            TIMESTAMPTZ,
  CHECK (outstanding_amount <= denominator_value)
);
CREATE INDEX idx_financed_positions_year ON carbon.financed_positions(tenant_id, reporting_year) WHERE deleted_at IS NULL;
SELECT auth.apply_tenant_rls('carbon.financed_positions');
SELECT auth.apply_audit_trigger('carbon.financed_positions');
SELECT auth.apply_audit_log_trigger('carbon.financed_positions');
GRANT SELECT, INSERT, UPDATE ON carbon.financed_positions TO ${app_role};

-- Tenants choose the GWP set for per-gas factors (default AR6, the IPCC's latest).
ALTER TABLE auth.tenant_settings ADD COLUMN gwp_set VARCHAR(10) NOT NULL DEFAULT 'AR6' CHECK (gwp_set IN ('AR5', 'AR6'));
