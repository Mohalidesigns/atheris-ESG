-- ============================================================
-- Requirements evidenced by platform data other than Data Hub metrics (WP-52b F7; D26). Repeatable.
--   carbon.reduction_targets: an active GHG reduction target covering the year (Carbon → Reduction targets)
--     IFRS S2 paragraph 33 (climate-related targets; narrative → the target makes it partial, an attestation
--     completes it) and GRI 305-5 (reduction of GHG emissions).
-- ============================================================
DELETE FROM reference.requirement_data_sources;
INSERT INTO reference.requirement_data_sources (data_source, requirement_id)
SELECT 'carbon.reduction_targets', r.id
FROM compliance.framework_requirements r JOIN compliance.regulatory_frameworks f ON f.id = r.framework_id
WHERE (f.code, r.requirement_code) IN (('ISSB_S2', 'S2-33'), ('GRI', '305-5'));

-- WP-34 (D30): the governance register evidences the governance-structure disclosures (narrative → partial; an
-- attestation completes them), policies the policy-commitment disclosure, a published whistleblowing policy the
-- mechanism for raising concerns.
INSERT INTO reference.requirement_data_sources (data_source, requirement_id)
SELECT s.source, r.id
FROM (VALUES ('governance.board_members', 'GRI', '2-9'), ('governance.board_members', 'GRI', '2-10'),
             ('governance.board_members', 'GRI', '2-11'), ('governance.governance_policies', 'GRI', '2-23'),
             ('governance.whistleblowing_policy', 'GRI', '2-26')) AS s(source, framework, code)
JOIN compliance.regulatory_frameworks f ON f.code = s.framework
JOIN compliance.framework_requirements r ON r.framework_id = f.id AND r.requirement_code = s.code;

-- WP-32 (D32): an approved double materiality assessment evidences the process and list of material topics (GRI 3-1,
-- 3-2); climate risks in the register evidence the identified climate-related risks and the processes for them (IFRS S2
-- paragraphs 10 and 25, TCFD Strategy a and Risk Management a); scenario analysis (price paths for a ≤2°C and a hot-house
-- scenario, or physical risk assessed) evidences climate resilience (IFRS S2 paragraph 22, TCFD Strategy c).
INSERT INTO reference.requirement_data_sources (data_source, requirement_id)
SELECT s.source, r.id
FROM (VALUES ('risk.materiality_assessment', 'GRI', '3-1'), ('risk.materiality_assessment', 'GRI', '3-2'),
             ('risk.risk_register', 'ISSB_S2', 'S2-10'), ('risk.risk_register', 'ISSB_S2', 'S2-25'),
             ('risk.risk_register', 'TCFD', 'STR-a'), ('risk.risk_register', 'TCFD', 'RM-a'),
             ('risk.climate_scenarios', 'ISSB_S2', 'S2-22'), ('risk.climate_scenarios', 'TCFD', 'STR-c')) AS s(source, framework, code)
JOIN compliance.regulatory_frameworks f ON f.code = s.framework
JOIN compliance.framework_requirements r ON r.framework_id = f.id AND r.requirement_code = s.code;

-- WP-31 (D33): an active supplier register evidences the value-chain disclosure (GRI 2-6).
INSERT INTO reference.requirement_data_sources (data_source, requirement_id)
SELECT 'supply_chain.suppliers', r.id
FROM compliance.framework_requirements r JOIN compliance.regulatory_frameworks f ON f.id = r.framework_id
WHERE f.code = 'GRI' AND r.requirement_code = '2-6';

-- WP-35 (D34): an active custom KPI with a target evidences tracking the effectiveness of actions on material topics
-- (GRI 3-3) and the metrics used to assess climate-related risks and opportunities (TCFD Metrics and Targets a).
INSERT INTO reference.requirement_data_sources (data_source, requirement_id)
SELECT s.source, r.id
FROM (VALUES ('analytics.custom_kpis', 'GRI', '3-3'), ('analytics.custom_kpis', 'TCFD', 'MT-a')) AS s(source, framework, code)
JOIN compliance.regulatory_frameworks f ON f.code = s.framework
JOIN compliance.framework_requirements r ON r.framework_id = f.id AND r.requirement_code = s.code;

-- WP-41 (D36): retired carbon credits evidence the use of carbon credits disclosed with climate-related targets
-- (IFRS S2 paragraphs 33-36).
INSERT INTO reference.requirement_data_sources (data_source, requirement_id)
SELECT 'carbon_market.retirements', r.id
FROM compliance.framework_requirements r JOIN compliance.regulatory_frameworks f ON f.id = r.framework_id
WHERE f.code = 'ISSB_S2' AND r.requirement_code = 'S2-33';

DO $$
BEGIN
  IF (SELECT count(*) FROM reference.requirement_data_sources) <> 19 THEN
    RAISE EXCEPTION 'Expected 19 requirement data sources, found %', (SELECT count(*) FROM reference.requirement_data_sources);
  END IF;
END $$;
