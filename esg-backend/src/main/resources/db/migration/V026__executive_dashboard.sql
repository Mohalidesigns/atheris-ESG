-- ============================================================
-- WP-24 Executive dashboard (D29). SDG alignment needs to know which SDGs a metric evidences. Global reference data,
-- read-only for the application, maintained by R__ref_13 from the GRI–SDG linkage via each metric's GRI disclosure,
-- pending ESG SME verification.
-- ============================================================
CREATE TABLE reference.metric_sdgs (
  metric_key          VARCHAR(100) NOT NULL REFERENCES reference.metrics(metric_key),
  goal                SMALLINT NOT NULL REFERENCES reference.sdgs(goal),
  targets             VARCHAR(100) NOT NULL,            -- e.g. '13.1' or '7.2, 7.3'
  via_disclosure      VARCHAR(30) NOT NULL,             -- the GRI disclosure the link comes through, e.g. 'GRI 305-1'
  source              VARCHAR(255) NOT NULL,
  verification_status VARCHAR(20) NOT NULL DEFAULT 'pending_sme' CHECK (verification_status IN ('pending_sme', 'sme_verified')),
  PRIMARY KEY (metric_key, goal)
);
GRANT SELECT ON reference.metric_sdgs TO ${app_role};
