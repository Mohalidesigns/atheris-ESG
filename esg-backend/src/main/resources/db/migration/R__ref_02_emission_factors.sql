-- ============================================================
-- Platform emission factors (WP-17, TRD §8.2). Repeatable and versioned:
--   * a row is identified by (region, category, sub_category, valid_from, factor_version);
--   * a published factor_value/unit is never edited in place: this script fails if one changes. To correct a value,
--     add a row with a new factor_version (and set valid_to on the old one), so past calculations keep their factor;
--   * every row names its source and how the value was derived; all rows stay 'pending_sme' until an ESG SME signs
--     them off (master plan §10, before Gate G2).
-- DEFRA transport and EEIO spend-based factors are not loaded yet: they need the licensed/published datasets
-- supplied and checked by the SME (STATUS.md, WP-17).
-- ============================================================

CREATE TEMP TABLE ef_seed (
  region VARCHAR(100), category VARCHAR(100), sub_category VARCHAR(200), factor_value DECIMAL(20,10), unit VARCHAR(50),
  source VARCHAR(255), valid_from DATE, valid_to DATE, factor_version VARCHAR(20), source_year INT, gwp_set VARCHAR(10),
  ghg_basis VARCHAR(10), derivation TEXT
) ON COMMIT DROP;

INSERT INTO ef_seed VALUES
  ('NG', 'electricity_grid', 'National Grid', 0.43, 'kgCO2e/kWh', 'FMPWH Nigeria 2024', '2024-01-01', NULL, '1', 2024, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified.'),
  ('NG', 'diesel_generator', 'Stationary Combustion', 2.68, 'kgCO2e/litre', 'DEFRA adjusted for Nigeria', '2025-01-01', NULL, '1', 2025, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified. Matches the IPCC 2006 default for gas/diesel oil on a CO2-only basis: 74,100 kg/TJ x 43.0 TJ/Gg x 0.84 kg/L = 2.68 kg CO2/L (CH4 + N2O add about 0.3%).'),
  ('NG', 'petrol_pms', 'Transport Fuel', 2.31, 'kgCO2e/litre', 'DEFRA', '2025-01-01', NULL, '1', 2025, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified.'),
  ('NG', 'natural_gas', 'Stationary Combustion', 2.02, 'kgCO2e/m3', 'GHG Protocol', '2024-01-01', NULL, '1', 2024, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified.'),
  ('WEST_AFRICA', 'air_travel_domestic', 'Business Travel', 0.255, 'kgCO2e/km', 'DEFRA', '2025-01-01', NULL, '1', 2025, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified.'),
  ('GLOBAL', 'air_travel_longhaul', 'Business Travel', 0.195, 'kgCO2e/km', 'DEFRA', '2025-01-01', NULL, '1', 2025, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified.'),
  ('KE', 'electricity_grid', 'National Grid', 0.322, 'kgCO2e/kWh', 'IEA Kenya 2024', '2024-01-01', NULL, '1', 2024, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified.'),
  ('ZA', 'electricity_grid', 'National Grid', 0.928, 'kgCO2e/kWh', 'Eskom 2024', '2024-01-01', NULL, '1', 2024, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified.'),
  ('GH', 'electricity_grid', 'National Grid', 0.395, 'kgCO2e/kWh', 'IEA Ghana 2024', '2024-01-01', NULL, '1', 2024, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified.'),
  ('GLOBAL', 'company_vehicles', 'Road Transport', 0.171, 'kgCO2e/km', 'DEFRA', '2025-01-01', NULL, '1', 2025, NULL, 'CO2e', 'Legacy seed value carried over from V1_006 with its originally recorded source; not independently verified.'),
  ('GLOBAL', 'diesel_generator', 'Stationary Combustion', 2.6856, 'kgCO2e/litre', 'IPCC 2006 Guidelines for National GHG Inventories, Vol. 2 (Energy): NCV Table 1.2, CO2 Table 1.4, CH4/N2O Table 2.3', '2006-01-01', NULL, '1', 2006, 'AR6', 'CO2e', 'CO2e = 0.84 kg/litre (assumed density) x NCV 43.0 TJ/Gg x (CO2 74100 + CH4 3 x 29.8 + N2O 0.6 x 273) kg/TJ; AR6 GWP-100. Density is an assumption for SME review.'),
  ('GLOBAL', 'petrol_generator', 'Stationary Combustion', 2.2801, 'kgCO2e/litre', 'IPCC 2006 Guidelines for National GHG Inventories, Vol. 2 (Energy): NCV Table 1.2, CO2 Table 1.4, CH4/N2O Table 2.3', '2006-01-01', NULL, '1', 2006, 'AR6', 'CO2e', 'CO2e = 0.74 kg/litre (assumed density) x NCV 44.3 TJ/Gg x (CO2 69300 + CH4 3 x 29.8 + N2O 0.6 x 273) kg/TJ; AR6 GWP-100. Density is an assumption for SME review.'),
  ('GLOBAL', 'kerosene', 'Stationary Combustion', 2.5282, 'kgCO2e/litre', 'IPCC 2006 Guidelines for National GHG Inventories, Vol. 2 (Energy): NCV Table 1.2, CO2 Table 1.4, CH4/N2O Table 2.3', '2006-01-01', NULL, '1', 2006, 'AR6', 'CO2e', 'CO2e = 0.8 kg/litre (assumed density) x NCV 43.8 TJ/Gg x (CO2 71900 + CH4 3 x 29.8 + N2O 0.6 x 273) kg/TJ; AR6 GWP-100. Density is an assumption for SME review.'),
  ('GLOBAL', 'heavy_fuel_oil', 'Stationary Combustion', 3.0744, 'kgCO2e/litre', 'IPCC 2006 Guidelines for National GHG Inventories, Vol. 2 (Energy): NCV Table 1.2, CO2 Table 1.4, CH4/N2O Table 2.3', '2006-01-01', NULL, '1', 2006, 'AR6', 'CO2e', 'CO2e = 0.98 kg/litre (assumed density) x NCV 40.4 TJ/Gg x (CO2 77400 + CH4 3 x 29.8 + N2O 0.6 x 273) kg/TJ; AR6 GWP-100. Density is an assumption for SME review.'),
  ('GLOBAL', 'lpg', 'Stationary Combustion', 2.9873, 'kgCO2e/kg', 'IPCC 2006 Guidelines for National GHG Inventories, Vol. 2 (Energy): NCV Table 1.2, CO2 Table 1.4, CH4/N2O Table 2.3', '2006-01-01', NULL, '1', 2006, 'AR6', 'CO2e', 'CO2e = NCV 47.3 TJ/Gg x (CO2 63100 + CH4 1 x 29.8 + N2O 0.1 x 273) kg/TJ per kg; AR6 GWP-100.');

DO $$
DECLARE changed TEXT;
BEGIN
  SELECT string_agg(s.region || '/' || s.category || ' v' || s.factor_version, ', ') INTO changed
  FROM ef_seed s JOIN carbon.emission_factors f
    ON f.tenant_id IS NULL AND f.region = s.region AND f.category = s.category
   AND COALESCE(f.sub_category, '') = COALESCE(s.sub_category, '') AND f.valid_from = s.valid_from
   AND f.factor_version = s.factor_version
  WHERE f.factor_value <> s.factor_value OR f.unit <> s.unit;
  IF changed IS NOT NULL THEN
    RAISE EXCEPTION 'Published emission factor values changed (%). Add a new factor_version instead.', changed;
  END IF;
END $$;

UPDATE carbon.emission_factors f
SET source = s.source, valid_to = s.valid_to, source_year = s.source_year, gwp_set = s.gwp_set, ghg_basis = s.ghg_basis,
    derivation = s.derivation, updated_at = now()
FROM ef_seed s
WHERE f.tenant_id IS NULL AND f.region = s.region AND f.category = s.category
  AND COALESCE(f.sub_category, '') = COALESCE(s.sub_category, '') AND f.valid_from = s.valid_from
  AND f.factor_version = s.factor_version;

INSERT INTO carbon.emission_factors (region, category, sub_category, factor_value, unit, source, valid_from, valid_to,
                                     factor_version, source_year, gwp_set, ghg_basis, derivation)
SELECT s.region, s.category, s.sub_category, s.factor_value, s.unit, s.source, s.valid_from, s.valid_to,
       s.factor_version, s.source_year, s.gwp_set, s.ghg_basis, s.derivation
FROM ef_seed s
WHERE NOT EXISTS (
  SELECT 1 FROM carbon.emission_factors f
  WHERE f.tenant_id IS NULL AND f.region = s.region AND f.category = s.category
    AND COALESCE(f.sub_category, '') = COALESCE(s.sub_category, '') AND f.valid_from = s.valid_from
    AND f.factor_version = s.factor_version);
