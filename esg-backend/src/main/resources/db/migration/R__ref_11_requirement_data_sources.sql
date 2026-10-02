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

DO $$
BEGIN
  IF (SELECT count(*) FROM reference.requirement_data_sources) <> 2 THEN
    RAISE EXCEPTION 'Expected 2 requirement data sources (IFRS S2-33, GRI 305-5)';
  END IF;
END $$;
