-- ============================================================
-- Reference catalogues (WP-17; BRD §5.2.1-06). Repeatable: upserts by natural key, never deletes (rows may be
-- referenced). Every row names its source. Edit this file to change the catalogue; git is the change history.
-- SDG targets (169) are not loaded yet: pending ESG SME (see STATUS.md, WP-17).
-- ============================================================

INSERT INTO reference.currencies (code, name, symbol, minor_unit, source) VALUES
  ('NGN', 'Naira', '₦', 2, 'ISO 4217 (currency codes and minor units)'),
  ('USD', 'US Dollar', '$', 2, 'ISO 4217 (currency codes and minor units)'),
  ('EUR', 'Euro', '€', 2, 'ISO 4217 (currency codes and minor units)'),
  ('GBP', 'Pound Sterling', '£', 2, 'ISO 4217 (currency codes and minor units)'),
  ('KES', 'Kenyan Shilling', 'KSh', 2, 'ISO 4217 (currency codes and minor units)'),
  ('ZAR', 'Rand', 'R', 2, 'ISO 4217 (currency codes and minor units)'),
  ('GHS', 'Ghana Cedi', 'GH₵', 2, 'ISO 4217 (currency codes and minor units)'),
  ('XOF', 'CFA Franc BCEAO', 'CFA', 0, 'ISO 4217 (currency codes and minor units)'),
  ('EGP', 'Egyptian Pound', 'E£', 2, 'ISO 4217 (currency codes and minor units)')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, symbol = EXCLUDED.symbol, minor_unit = EXCLUDED.minor_unit, source = EXCLUDED.source;

INSERT INTO reference.countries (code, name, region, currency, source) VALUES
  ('NG', 'Nigeria', 'WEST_AFRICA', 'NGN', 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('GH', 'Ghana', 'WEST_AFRICA', 'GHS', 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('CI', 'Côte d''Ivoire', 'WEST_AFRICA', 'XOF', 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('SN', 'Senegal', 'WEST_AFRICA', 'XOF', 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('BJ', 'Benin', 'WEST_AFRICA', 'XOF', 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('TG', 'Togo', 'WEST_AFRICA', 'XOF', 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('KE', 'Kenya', 'EAST_AFRICA', 'KES', 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('TZ', 'Tanzania', 'EAST_AFRICA', NULL, 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('UG', 'Uganda', 'EAST_AFRICA', NULL, 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('RW', 'Rwanda', 'EAST_AFRICA', NULL, 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('ET', 'Ethiopia', 'EAST_AFRICA', NULL, 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('ZA', 'South Africa', 'SOUTHERN_AFRICA', 'ZAR', 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('BW', 'Botswana', 'SOUTHERN_AFRICA', NULL, 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('NA', 'Namibia', 'SOUTHERN_AFRICA', NULL, 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('EG', 'Egypt', 'NORTH_AFRICA', 'EGP', 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)'),
  ('MA', 'Morocco', 'NORTH_AFRICA', NULL, 'ISO 3166-1 alpha-2; region = UN M49 sub-region (Africa)')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, region = EXCLUDED.region, currency = EXCLUDED.currency, source = EXCLUDED.source;

INSERT INTO reference.sdgs (goal, title, source) VALUES
  (1, 'No poverty', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (2, 'Zero hunger', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (3, 'Good health and well-being', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (4, 'Quality education', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (5, 'Gender equality', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (6, 'Clean water and sanitation', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (7, 'Affordable and clean energy', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (8, 'Decent work and economic growth', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (9, 'Industry, innovation and infrastructure', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (10, 'Reduced inequalities', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (11, 'Sustainable cities and communities', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (12, 'Responsible consumption and production', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (13, 'Climate action', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (14, 'Life below water', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (15, 'Life on land', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (16, 'Peace, justice and strong institutions', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles'),
  (17, 'Partnerships for the goals', 'United Nations, Transforming our world: the 2030 Agenda (A/RES/70/1), goal short titles')
ON CONFLICT (goal) DO UPDATE SET title = EXCLUDED.title, source = EXCLUDED.source;

INSERT INTO reference.scope3_categories (category, name, direction, source) VALUES
  (1, 'Purchased goods and services', 'upstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (2, 'Capital goods', 'upstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (3, 'Fuel- and energy-related activities (not included in Scope 1 or 2)', 'upstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (4, 'Upstream transportation and distribution', 'upstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (5, 'Waste generated in operations', 'upstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (6, 'Business travel', 'upstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (7, 'Employee commuting', 'upstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (8, 'Upstream leased assets', 'upstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (9, 'Downstream transportation and distribution', 'downstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (10, 'Processing of sold products', 'downstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (11, 'Use of sold products', 'downstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (12, 'End-of-life treatment of sold products', 'downstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (13, 'Downstream leased assets', 'downstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (14, 'Franchises', 'downstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3'),
  (15, 'Investments', 'downstream', 'GHG Protocol Corporate Value Chain (Scope 3) Standard (2011), Table 5.3')
ON CONFLICT (category) DO UPDATE SET name = EXCLUDED.name, direction = EXCLUDED.direction, source = EXCLUDED.source;

INSERT INTO reference.sectors (code, name, classification, source) VALUES
  ('A', 'Agriculture, forestry and fishing', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('B', 'Mining and quarrying', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('C', 'Manufacturing', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('D', 'Electricity, gas, steam and air conditioning supply', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('E', 'Water supply; sewerage, waste management and remediation activities', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('F', 'Construction', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('G', 'Wholesale and retail trade; repair of motor vehicles and motorcycles', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('H', 'Transportation and storage', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('I', 'Accommodation and food service activities', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('J', 'Information and communication', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('K', 'Financial and insurance activities', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('L', 'Real estate activities', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('M', 'Professional, scientific and technical activities', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('N', 'Administrative and support service activities', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('O', 'Public administration and defence; compulsory social security', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('P', 'Education', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('Q', 'Human health and social work activities', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('R', 'Arts, entertainment and recreation', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('S', 'Other service activities', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('T', 'Activities of households as employers; undifferentiated goods- and services-producing activities of households for own use', 'ISIC_REV4', 'UN ISIC Rev. 4, sections'),
  ('U', 'Activities of extraterritorial organizations and bodies', 'ISIC_REV4', 'UN ISIC Rev. 4, sections')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, classification = EXCLUDED.classification, source = EXCLUDED.source;
