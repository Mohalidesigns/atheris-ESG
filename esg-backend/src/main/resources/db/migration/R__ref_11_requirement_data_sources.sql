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

DO $$
BEGIN
  IF (SELECT count(*) FROM reference.requirement_data_sources) <> 7 THEN
    RAISE EXCEPTION 'Expected 7 requirement data sources, found %', (SELECT count(*) FROM reference.requirement_data_sources);
  END IF;
END $$;
