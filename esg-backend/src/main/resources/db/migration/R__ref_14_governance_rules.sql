-- ============================================================
-- Governance code checker rules (WP-34, D30; TRD §8.8 GovernanceComplianceChecker). Each rule is a typed check on the
-- board register with parameters and its provision reference. Pending ESG SME verification: the SME confirms each
-- provision, its threshold and whether it applies to the tenant (most apply to public companies). Rebuilt from this file.
-- ============================================================
DELETE FROM reference.governance_rules;
INSERT INTO reference.governance_rules (code, rule_set, provision, title, rule_type, params, applies_to, source, sort_order) VALUES
  ('CAMA-271', 'CAMA_2020', 's.271(1)', 'A company has at least two directors', 'min_board_size', '{"min": 2}', 'all',
   'Companies and Allied Matters Act 2020, s.271', 10),
  ('CAMA-275', 'CAMA_2020', 's.275(1)', 'A public company has at least three independent directors', 'min_independent', '{"min": 3}', 'public_company',
   'Companies and Allied Matters Act 2020, s.275', 20),
  ('CAMA-265', 'CAMA_2020', 's.265(6)', 'The chair of a public company is not also its chief executive', 'chair_not_ceo', '{}', 'public_company',
   'Companies and Allied Matters Act 2020, s.265', 30),
  ('CAMA-330', 'CAMA_2020', 's.330', 'A public company has a company secretary', 'company_secretary', '{}', 'public_company',
   'Companies and Allied Matters Act 2020, s.330', 40),
  ('CAMA-404', 'CAMA_2020', 's.404', 'A public company has an audit committee', 'committee_exists', '{"committee": "audit"}', 'public_company',
   'Companies and Allied Matters Act 2020, s.404', 50),
  ('NCCG-2', 'NCCG_2018', 'Principle 2', 'Most board members are non-executive directors', 'majority_non_executive', '{}', 'public_company',
   'Nigerian Code of Corporate Governance 2018 (FRC), Principle 2', 110),
  ('NCCG-3', 'NCCG_2018', 'Principle 3', 'The chair is a non-executive director and not the managing director', 'chair_non_executive', '{}', 'public_company',
   'Nigerian Code of Corporate Governance 2018 (FRC), Principle 3', 120),
  ('NCCG-7', 'NCCG_2018', 'Principle 7', 'Independent non-executive directors serve no more than nine years', 'max_ined_tenure_years', '{"years": 9}', 'public_company',
   'Nigerian Code of Corporate Governance 2018 (FRC), Principle 7', 130),
  ('NCCG-11-AUDIT', 'NCCG_2018', 'Principle 11', 'The board has an audit committee', 'committee_exists', '{"committee": "audit"}', 'public_company',
   'Nigerian Code of Corporate Governance 2018 (FRC), Principle 11', 140),
  ('NCCG-11-NOM', 'NCCG_2018', 'Principle 11', 'The board has a nomination and governance committee', 'committee_exists', '{"committee": "nomination_governance"}', 'public_company',
   'Nigerian Code of Corporate Governance 2018 (FRC), Principle 11', 150),
  ('NCCG-11-REM', 'NCCG_2018', 'Principle 11', 'The board has a remuneration committee', 'committee_exists', '{"committee": "remuneration"}', 'public_company',
   'Nigerian Code of Corporate Governance 2018 (FRC), Principle 11', 160),
  ('NCCG-11-RISK', 'NCCG_2018', 'Principle 11', 'The board has a risk management committee', 'committee_exists', '{"committee": "risk"}', 'public_company',
   'Nigerian Code of Corporate Governance 2018 (FRC), Principle 11', 170),
  ('KING-7-NED', 'KING_IV', 'Principle 7', 'Most board members are non-executive directors', 'majority_non_executive', '{}', 'all',
   'King IV Report on Corporate Governance for South Africa 2016, Principle 7', 210),
  ('KING-7-CHAIR', 'KING_IV', 'Principle 7', 'The chair is an independent non-executive director', 'chair_non_executive', '{"independent": true}', 'all',
   'King IV Report on Corporate Governance for South Africa 2016, Principle 7', 220),
  ('KING-7-CEO', 'KING_IV', 'Principle 7', 'The chief executive is not also the chair', 'chair_not_ceo', '{}', 'all',
   'King IV Report on Corporate Governance for South Africa 2016, Principle 7', 230),
  ('KING-8-AUDIT', 'KING_IV', 'Principle 8', 'The board has an audit committee', 'committee_exists', '{"committee": "audit"}', 'all',
   'King IV Report on Corporate Governance for South Africa 2016, Principle 8', 240);
