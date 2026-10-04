-- ============================================================
-- Spend-based (EEIO) emission factors for Scope 3 (WP-52b F4; D26). Source: US EPA, "Supply Chain Greenhouse Gas
-- Emission Factors v1.3 by NAICS-6", 2022 data, kg CO2e per 2022 USD purchaser price, supply chain factors *with*
-- margins, all GHGs, IPCC AR5 GWP-100. DOI 10.23719/1531143 (published July 2024).
-- One NAICS-6 commodity per activity, chosen as the closest proxy for what a Nigerian/African organisation buys in
-- that Scope 3 category. US-economy intensities applied to spend converted to USD are a screening estimate: rows stay
-- 'pending_sme' until the ESG SME confirms the proxies (or replaces them with an African EEIO model). Spend in
-- another currency is converted to USD with the tenant's exchange rates before the factor is applied.
-- Versioned like R__ref_02: a published value is never edited in place.
-- ============================================================
CREATE TEMP TABLE spend_seed (category VARCHAR(100), naics VARCHAR(10), title VARCHAR(200), factor_value DECIMAL(20,10))
  ON COMMIT DROP;
INSERT INTO spend_seed VALUES
  ('spend_professional_services', '541611', 'Administrative Management and General Management Consulting Services', 0.078),
  ('spend_paper_stationery',      '322230', 'Stationery Product Manufacturing', 0.296),
  ('spend_telecom_services',      '517311', 'Wired Telecommunications Carriers', 0.075),
  ('spend_computers',             '334111', 'Electronic Computer Manufacturing', 0.058),
  ('spend_building_construction', '236220', 'Commercial and Institutional Building Construction', 0.224),
  ('spend_road_freight',          '484121', 'General Freight Trucking, Long-Distance, Truckload', 0.595),
  ('spend_waste_services',        '562111', 'Solid Waste Collection', 0.481),
  ('spend_air_travel',            '481111', 'Scheduled Passenger Air Transportation', 0.644),
  ('spend_hotels',                '721110', 'Hotels (except Casino Hotels) and Motels', 0.303),
  ('spend_bus_transport',         '485210', 'Interurban and Rural Bus Transportation', 0.566),
  ('spend_leased_buildings',      '531120', 'Lessors of Nonresidential Buildings (except Miniwarehouses)', 0.246);

DO $$
DECLARE changed TEXT;
BEGIN
  SELECT string_agg(s.category, ', ') INTO changed FROM spend_seed s JOIN carbon.emission_factors f
    ON f.tenant_id IS NULL AND f.region = 'GLOBAL' AND f.category = s.category AND f.valid_from = DATE '2022-01-01'
   AND f.factor_version = '1'
  WHERE f.factor_value <> s.factor_value;
  IF changed IS NOT NULL THEN
    RAISE EXCEPTION 'Published spend factor values changed (%). Add a new factor_version instead.', changed;
  END IF;
END $$;

INSERT INTO carbon.emission_factors (region, category, sub_category, factor_value, unit, source, valid_from, factor_version,
                                     source_year, gwp_set, ghg_basis, derivation, factor_method, spend_currency)
SELECT 'GLOBAL', s.category, 'NAICS ' || s.naics, s.factor_value, 'kgCO2e/USD',
       'US EPA Supply Chain GHG Emission Factors v1.3 (NAICS-6, 2022 USD, with margins), DOI 10.23719/1531143',
       DATE '2022-01-01', '1', 2022, 'AR5', 'CO2e',
       'Supply chain emission factor with margins for NAICS ' || s.naics || ' "' || s.title || '", kg CO2e per 2022 USD '
         || 'purchaser price. US-economy proxy; spend in other currencies is converted to USD first. Pending SME review.',
       'spend', 'USD'
FROM spend_seed s
WHERE NOT EXISTS (SELECT 1 FROM carbon.emission_factors f WHERE f.tenant_id IS NULL AND f.region = 'GLOBAL'
                  AND f.category = s.category AND f.valid_from = DATE '2022-01-01' AND f.factor_version = '1');
