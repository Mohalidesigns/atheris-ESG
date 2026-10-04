import { z } from 'zod';
import { humanize } from '../../services/labels';
import { CURRENCIES } from '../admin/schemas';

/**
 * Carbon forms. Each mirrors its server DTO exactly: ReferenceDtos.CustomFactorRequest (+ EmissionFactorService rules) and
 * CarbonDtos.EmissionRequest / FlaringRequest / InstrumentRequest / TargetRequest / FinancedRequest (+ service rules).
 */
export const REGION = /^([A-Z]{2}|WEST_AFRICA|EAST_AFRICA|SOUTHERN_AFRICA|NORTH_AFRICA|CENTRAL_AFRICA|GLOBAL)$/;
export const CATEGORY = /^[a-z][a-z0-9_]*$/;
const FACTOR = /^\d{1,10}(\.\d{1,10})?$/; // @Digits(integer = 10, fraction = 10)
const AMOUNT_6 = /^\d{1,14}(\.\d{1,6})?$/; // @Digits(integer = 14, fraction = 6)
const AMOUNT_2 = /^\d{1,14}(\.\d{1,2})?$/; // @Digits(integer = 14, fraction = 2)
const MONEY = /^\d{1,18}(\.\d{1,2})?$/; // @Digits(integer = 18, fraction = 2)

const positive = (re, label = 'value') => z.string().trim()
  .refine((v) => v === '' || re.test(v), `Enter a number (${label})`)
  .refine((v) => v === '' || Number(v) > 0, 'Must be greater than 0');
const between = (s, min, max) => s !== '' && Number(s) >= min && Number(s) <= max;

// no-mock-gate: config — enum labels mirroring CarbonDtos patterns
export const SCOPES = [
  { value: 'scope_1', label: 'Scope 1 — direct' },
  { value: 'scope_2', label: 'Scope 2 — purchased energy' },
  { value: 'scope_3', label: 'Scope 3 — value chain' },
];
// no-mock-gate: config — labels for EmissionService factor levels (FactorUsed.level)
export const FACTOR_LEVELS = { selected: 'chosen', custom: 'your own factor', country: 'country factor', region: 'regional factor', global: 'global factor' };
// no-mock-gate: config — enum labels mirroring CarbonDtos patterns
export const FLARING_REASONS = ['routine', 'safety', 'upset', 'maintenance'];
// no-mock-gate: config — gas components FlaringCalculator understands (mole %)
export const GAS_COMPONENTS = [
  { key: 'methane', label: 'Methane (CH4)' }, { key: 'ethane', label: 'Ethane (C2H6)' }, { key: 'propane', label: 'Propane (C3H8)' },
  { key: 'butane', label: 'Butane (C4H10)' }, { key: 'pentane_plus', label: 'Pentane+ (C5+)' }, { key: 'co2', label: 'Carbon dioxide (CO2)' },
  { key: 'nitrogen', label: 'Nitrogen (N2)' },
];
// no-mock-gate: config — enum labels mirroring CarbonDtos patterns
export const INSTRUMENT_TYPES = { rec: 'REC (certificate)', ppa: 'PPA (power purchase agreement)', green_tariff: 'Green tariff' };
// no-mock-gate: config — enum labels mirroring CarbonDtos patterns
export const ENERGY_SOURCES = ['solar', 'wind', 'hydro', 'biomass', 'geothermal', 'other'];
// no-mock-gate: config — enum labels mirroring CarbonDtos patterns
export const TARGET_SCOPES = {
  scope_1: 'Scope 1', scope_2: 'Scope 2', scope_3: 'Scope 3', scope_1_2: 'Scopes 1 + 2', scope_1_2_3: 'Scopes 1 + 2 + 3',
};
// no-mock-gate: config — enum labels mirroring CarbonDtos patterns
export const DENOMINATORS = { revenue: 'per million revenue', employees: 'per employee', production: 'per unit produced' };
// no-mock-gate: config — PCAF asset classes and the denominator each uses (PCAF Standard Part A)
export const ASSET_CLASSES = {
  listed_equity_bonds: { label: 'Listed equity & corporate bonds', denominator: 'EVIC' },
  business_loans_unlisted_equity: { label: 'Business loans & unlisted equity', denominator: 'Total equity + debt (or EVIC)' },
  project_finance: { label: 'Project finance', denominator: 'Total project equity + debt' },
  commercial_real_estate: { label: 'Commercial real estate', denominator: 'Property value at origination' },
  mortgages: { label: 'Mortgages', denominator: 'Property value at origination' },
  motor_vehicle_loans: { label: 'Motor vehicle loans', denominator: 'Vehicle value at origination' },
};

// ---------------------------------------------------------------- custom factors

export const emptyFactor = {
  region: 'NG', category: '', subCategory: '', factorValue: '', unit: '', source: '', validFrom: '', validTo: '',
  factorMethod: 'activity', spendCurrency: '', newActivity: false, activityLabel: '', activityScope: '', activityScope3Category: '',
};

export const customFactorSchema = z
  .object({
    region: z.string().trim().regex(REGION, 'Use an ISO country code (e.g. NG) or a region such as WEST_AFRICA'),
    category: z.string().trim().min(1, 'Category is required').max(100, 'At most 100 characters')
      .regex(CATEGORY, 'Lower-case letters, digits and underscores, e.g. diesel_generator'),
    subCategory: z.string().trim().max(200, 'At most 200 characters'),
    factorValue: z.string().trim().min(1, 'Value is required')
      .regex(FACTOR, 'Enter a positive number with up to 10 decimal places')
      .refine((v) => Number(v) > 0, 'Must be greater than 0')
      .refine((v) => Number(v) <= 1000000, 'At most 1,000,000'),
    unit: z.string().trim().max(50, 'At most 50 characters'),
    source: z.string().trim().min(1, 'Say where this value comes from').max(255, 'At most 255 characters'),
    validFrom: z.string().min(1, 'Start date is required'),
    validTo: z.string(),
    factorMethod: z.enum(['activity', 'spend'], { message: 'Activity or spend' }),
    spendCurrency: z.string(),
    // A new activity (F11): only when the category isn't one of the known activities.
    newActivity: z.boolean().optional(),
    activityLabel: z.string().trim().max(150, 'At most 150 characters').optional(),
    activityScope: z.string().optional(),
    activityScope3Category: z.string().optional(),
  })
  .superRefine((v, ctx) => {
    const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
    if (v.validTo && v.validFrom && v.validTo < v.validFrom) issue('validTo', 'Must be on or after the start date');
    // Mirrors EmissionFactorService.checkActivity (F11).
    // A spend factor's unit is kgCO2e per its currency, set by the server; an activity factor's is required.
    if (v.factorMethod !== 'spend') {
      if (!v.unit.trim()) issue('unit', 'Unit is required');
      else if (!/^kgCO2e\/.+/.test(v.unit.trim())) issue('unit', 'Must be kgCO2e per unit, e.g. kgCO2e/litre');
    }
    if (v.newActivity) {
      if (!v.activityLabel?.trim()) issue('activityLabel', 'Name the new activity');
      if (!v.activityScope) issue('activityScope', 'Choose the GHG scope of the new activity');
      else if (v.activityScope === 'scope_3' && !v.activityScope3Category) issue('activityScope3Category', 'Choose the Scope 3 category');
    }
    if (v.factorMethod === 'spend' && !v.spendCurrency) issue('spendCurrency', 'Choose the currency the factor is per');
    if (v.factorMethod !== 'spend' && v.spendCurrency) issue('spendCurrency', 'Only for spend-based factors');
    if (v.spendCurrency && !CURRENCIES.includes(v.spendCurrency)) issue('spendCurrency', `Must be one of ${CURRENCIES.join(', ')}`);
  });

export const toFactorBody = (v) => ({
  region: v.region.trim(),
  category: v.category.trim(),
  subCategory: v.subCategory.trim() || null,
  factorValue: Number(v.factorValue),
  unit: v.factorMethod === 'spend' ? null : v.unit.trim(),
  source: v.source.trim(),
  validFrom: v.validFrom,
  validTo: v.validTo || null,
  factorMethod: v.factorMethod,
  spendCurrency: v.factorMethod === 'spend' ? v.spendCurrency : null,
  activityLabel: v.newActivity ? v.activityLabel.trim() : null,
  activityScope: v.newActivity ? v.activityScope : null,
  activityScope3Category: v.newActivity && v.activityScope === 'scope_3' ? Number(v.activityScope3Category) : null,
});

export const toFactorForm = (f) => ({
  region: f.region, category: f.category, subCategory: f.subCategory ?? '', factorValue: String(f.factorValue),
  unit: f.unit, source: f.source, validFrom: f.validFrom, validTo: f.validTo ?? '',
  factorMethod: f.factorMethod ?? 'activity', spendCurrency: f.spendCurrency ?? '',
  newActivity: false, activityLabel: '', activityScope: '', activityScope3Category: '',
});

/** "diesel_generator" → "Diesel generator". */
export const activityLabel = (category) => humanize(category);

/** "kgCO2e/litre" → "litre": the unit the activity data must be in. */
export const factorActivityUnit = (unit) => (unit?.includes('/') ? unit.slice(unit.indexOf('/') + 1) : unit ?? '');

// ---------------------------------------------------------------- emission calculator (EmissionRequest)

export const emptyEmission = {
  orgId: '', scope: 'scope_1', scope3Category: '', calculationMethod: 'activity_based', sourceActivity: '', activityData: '',
  activityUnit: '', spendAmount: '', spendCurrency: '', emissionFactorId: '', reportingPeriodStart: '', reportingPeriodEnd: '', notes: '',
};

export const emissionSchema = z
  .object({
    orgId: z.string().min(1, 'Choose an organisation'),
    scope: z.enum(['scope_1', 'scope_2', 'scope_3'], { message: 'Choose a scope' }),
    scope3Category: z.string(),
    calculationMethod: z.enum(['activity_based', 'spend_based']),
    sourceActivity: z.string().trim().min(1, 'Choose an activity').max(100, 'At most 100 characters')
      .regex(CATEGORY, 'Lower-case letters, digits and underscores'),
    activityData: positive(AMOUNT_6, 'up to 6 decimals'),
    activityUnit: z.string().trim().max(50, 'At most 50 characters'),
    spendAmount: positive(AMOUNT_2, 'up to 2 decimals'),
    spendCurrency: z.string(),
    emissionFactorId: z.string(),
    reportingPeriodStart: z.string().min(1, 'Start date is required'),
    reportingPeriodEnd: z.string().min(1, 'End date is required'),
    notes: z.string().trim().max(500, 'At most 500 characters'),
  })
  .superRefine((v, ctx) => {
    const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
    if (v.reportingPeriodStart && v.reportingPeriodEnd && v.reportingPeriodEnd < v.reportingPeriodStart) {
      issue('reportingPeriodEnd', 'Must be on or after the start date');
    }
    if (v.scope === 'scope_3' && !v.scope3Category) issue('scope3Category', 'Choose the Scope 3 category');
    if (v.scope !== 'scope_3' && v.scope3Category) issue('scope3Category', 'Only for Scope 3');
    if (v.scope3Category && !between(v.scope3Category, 1, 15)) issue('scope3Category', 'Category 1 to 15');
    if (v.calculationMethod === 'spend_based') {
      if (!v.spendAmount) issue('spendAmount', 'Enter the amount spent');
      if (!v.spendCurrency) issue('spendCurrency', 'Choose the currency');
    } else if (!v.activityData) {
      issue('activityData', 'Enter the activity data');
    }
  });

export const toEmissionBody = (v) => {
  const spend = v.calculationMethod === 'spend_based';
  return {
    orgId: v.orgId,
    scope: v.scope,
    scope3Category: v.scope === 'scope_3' && v.scope3Category ? Number(v.scope3Category) : null,
    calculationMethod: v.calculationMethod,
    sourceActivity: v.sourceActivity.trim(),
    activityData: spend ? null : Number(v.activityData),
    activityUnit: spend ? null : v.activityUnit.trim() || null,
    spendAmount: spend ? Number(v.spendAmount) : null,
    spendCurrency: spend ? v.spendCurrency : null,
    emissionFactorId: v.emissionFactorId || null,
    reportingPeriodStart: v.reportingPeriodStart,
    reportingPeriodEnd: v.reportingPeriodEnd,
    notes: v.notes.trim() || null,
  };
};

export const toEmissionForm = (r) => ({
  orgId: r.orgId ?? '', scope: r.scope ?? 'scope_1', scope3Category: r.scope3Category ? String(r.scope3Category) : '',
  calculationMethod: r.calculationMethod === 'spend_based' ? 'spend_based' : 'activity_based', sourceActivity: r.sourceActivity ?? '',
  activityData: r.calculationMethod === 'spend_based' || r.activityData == null ? '' : String(Number(r.activityData)),
  activityUnit: r.calculationMethod === 'spend_based' ? '' : r.activityUnit ?? '',
  spendAmount: r.spendAmount == null ? '' : String(Number(r.spendAmount)), spendCurrency: r.spendCurrency ?? '',
  emissionFactorId: r.factor?.level === 'selected' ? r.factor.id : '',
  reportingPeriodStart: r.reportingPeriodStart ?? '', reportingPeriodEnd: r.reportingPeriodEnd ?? '', notes: r.notes ?? '',
});

// ---------------------------------------------------------------- gas flaring (FlaringRequest)

export const emptyFlaring = {
  facilityId: '', volumeMcf: '', composition: Object.fromEntries(GAS_COMPONENTS.map((g) => [g.key, ''])),
  combustionEfficiency: '0.98', flaringReason: '', reportingPeriodStart: '', reportingPeriodEnd: '', notes: '',
};

export const flaringSchema = z
  .object({
    facilityId: z.string().min(1, 'Choose the facility'),
    volumeMcf: positive(AMOUNT_6, 'Mcf, up to 6 decimals').refine((v) => v !== '', 'Volume is required'),
    composition: z.record(z.string(), z.string()),
    combustionEfficiency: z.string().trim(),
    flaringReason: z.string(),
    reportingPeriodStart: z.string().min(1, 'Start date is required'),
    reportingPeriodEnd: z.string().min(1, 'End date is required'),
    notes: z.string().trim().max(500, 'At most 500 characters'),
  })
  .superRefine((v, ctx) => {
    const issue = (path, message) => ctx.addIssue({ code: 'custom', path, message });
    if (v.reportingPeriodStart && v.reportingPeriodEnd && v.reportingPeriodEnd < v.reportingPeriodStart) {
      issue(['reportingPeriodEnd'], 'Must be on or after the start date');
    }
    let total = 0;
    Object.entries(v.composition).forEach(([k, s]) => {
      if (s === '') return;
      if (Number.isNaN(Number(s)) || Number(s) < 0 || Number(s) > 100) issue(['composition', k], '0 to 100');
      else total += Number(s);
    });
    if (Math.abs(total - 100) > 0.5) issue(['gasComposition'], `Mole % must add up to 100 (now ${Math.round(total * 100) / 100})`);
    if (v.combustionEfficiency !== '' && !between(v.combustionEfficiency, 0.5, 1)) issue(['combustionEfficiency'], 'Between 0.5 and 1');
    if (v.flaringReason && !FLARING_REASONS.includes(v.flaringReason)) issue(['flaringReason'], 'Choose a reason');
  });

export const toFlaringBody = (v) => ({
  facilityId: v.facilityId,
  volumeMcf: Number(v.volumeMcf),
  gasComposition: Object.fromEntries(Object.entries(v.composition).filter(([, s]) => s !== '').map(([k, s]) => [k, Number(s)])),
  combustionEfficiency: v.combustionEfficiency === '' ? null : Number(v.combustionEfficiency),
  flaringReason: v.flaringReason || null,
  reportingPeriodStart: v.reportingPeriodStart,
  reportingPeriodEnd: v.reportingPeriodEnd,
  notes: v.notes.trim() || null,
});

export const toFlaringForm = (f) => ({
  facilityId: f.facilityId, volumeMcf: String(Number(f.volumeMcf)),
  composition: Object.fromEntries(GAS_COMPONENTS.map((g) => [g.key, f.gasComposition?.[g.key] != null ? String(Number(f.gasComposition[g.key])) : ''])),
  combustionEfficiency: String(Number(f.combustionEfficiency)), flaringReason: f.flaringReason ?? '',
  reportingPeriodStart: f.reportingPeriodStart, reportingPeriodEnd: f.reportingPeriodEnd, notes: f.notes ?? '',
});

// ---------------------------------------------------------------- RECs / PPAs (InstrumentRequest)

export const emptyInstrument = {
  orgId: '', instrumentType: 'rec', energySource: 'solar', mwh: '', emissionFactor: '0', reportingPeriodStart: '',
  reportingPeriodEnd: '', certificateRef: '', supplier: '',
};

export const instrumentSchema = z
  .object({
    orgId: z.string().min(1, 'Choose an organisation'),
    instrumentType: z.enum(Object.keys(INSTRUMENT_TYPES), { message: 'Choose a type' }),
    energySource: z.enum(ENERGY_SOURCES, { message: 'Choose a source' }),
    mwh: positive(AMOUNT_6, 'MWh').refine((v) => v !== '', 'MWh is required'),
    emissionFactor: z.string().trim(),
    reportingPeriodStart: z.string().min(1, 'Start date is required'),
    reportingPeriodEnd: z.string().min(1, 'End date is required'),
    certificateRef: z.string().trim().max(100, 'At most 100 characters'),
    supplier: z.string().trim().min(1, 'Supplier or registry is required').max(255, 'At most 255 characters'),
  })
  .superRefine((v, ctx) => {
    if (v.reportingPeriodStart && v.reportingPeriodEnd && v.reportingPeriodEnd < v.reportingPeriodStart) {
      ctx.addIssue({ code: 'custom', path: ['reportingPeriodEnd'], message: 'Must be on or after the start date' });
    }
    if (v.emissionFactor !== '' && !between(v.emissionFactor, 0, 2)) {
      ctx.addIssue({ code: 'custom', path: ['emissionFactor'], message: 'Between 0 and 2 kgCO2e/kWh' });
    }
  });

export const toInstrumentBody = (v) => ({
  ...v, mwh: Number(v.mwh), emissionFactor: v.emissionFactor === '' ? 0 : Number(v.emissionFactor),
  certificateRef: v.certificateRef.trim() || null, supplier: v.supplier.trim(),
});

export const toInstrumentForm = (i) => ({
  orgId: i.orgId, instrumentType: i.instrumentType, energySource: i.energySource, mwh: String(Number(i.mwh)),
  emissionFactor: String(Number(i.emissionFactor)), reportingPeriodStart: i.reportingPeriodStart, reportingPeriodEnd: i.reportingPeriodEnd,
  certificateRef: i.certificateRef ?? '', supplier: i.supplier,
});

// ---------------------------------------------------------------- targets (TargetRequest)

export const emptyTarget = {
  targetName: '', targetType: 'absolute', scope: 'scope_1_2', baseYear: '', baseValue: '', targetYear: '', targetValue: '',
  pathway: 'linear', sbtiAligned: false, intensityDenominator: '',
};

const year = z.string().trim().refine((v) => /^\d{4}$/.test(v) && between(v, 2000, 2100), 'A year from 2000 to 2100');

export const targetSchema = z
  .object({
    targetName: z.string().trim().min(1, 'Name is required').max(255, 'At most 255 characters'),
    targetType: z.enum(['absolute', 'intensity']),
    scope: z.enum(Object.keys(TARGET_SCOPES)),
    baseYear: year,
    baseValue: positive(AMOUNT_6).refine((v) => v !== '', 'Base value is required'),
    targetYear: year,
    targetValue: z.string().trim().refine((v) => v !== '' && AMOUNT_6.test(v), 'Enter the target value'),
    pathway: z.enum(['linear', 'contraction']),
    sbtiAligned: z.boolean(),
    intensityDenominator: z.string(),
  })
  .superRefine((v, ctx) => {
    const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
    if (v.baseYear && v.targetYear && Number(v.targetYear) <= Number(v.baseYear)) issue('targetYear', 'Must be after the base year');
    if (v.baseValue && v.targetValue && Number(v.targetValue) >= Number(v.baseValue)) issue('targetValue', 'Must be below the base value');
    if (v.targetType === 'intensity' && !v.intensityDenominator) issue('intensityDenominator', 'Choose what the intensity is per');
    if (v.targetType !== 'intensity' && v.intensityDenominator) issue('intensityDenominator', 'Only for intensity targets');
  });

export const toTargetBody = (v) => ({
  ...v, targetName: v.targetName.trim(), baseYear: Number(v.baseYear), baseValue: Number(v.baseValue), targetYear: Number(v.targetYear),
  targetValue: Number(v.targetValue), intensityDenominator: v.targetType === 'intensity' ? v.intensityDenominator : null,
});

export const toTargetForm = (t) => ({
  targetName: t.targetName, targetType: t.targetType, scope: t.scope, baseYear: String(t.baseYear), baseValue: String(Number(t.baseValue)),
  targetYear: String(t.targetYear), targetValue: String(Number(t.targetValue)), pathway: t.pathway, sbtiAligned: !!t.sbtiAligned,
  intensityDenominator: t.intensityDenominator ?? '',
});

// ---------------------------------------------------------------- PCAF (FinancedRequest)

export const emptyFinanced = {
  reportingYear: String(new Date().getFullYear()), assetClass: 'business_loans_unlisted_equity', counterparty: '', sector: '',
  outstandingAmount: '', denominatorValue: '', currency: 'NGN', counterpartyEmissions: '', dataQualityScore: '', emissionsSource: '',
};

export const financedSchema = z
  .object({
    reportingYear: year,
    assetClass: z.enum(Object.keys(ASSET_CLASSES), { message: 'Choose an asset class' }),
    counterparty: z.string().trim().min(1, 'Counterparty is required').max(255, 'At most 255 characters'),
    sector: z.string().trim().max(100, 'At most 100 characters'),
    outstandingAmount: z.string().trim().refine((v) => MONEY.test(v), 'Enter the amount outstanding'),
    denominatorValue: z.string().trim().refine((v) => MONEY.test(v) && Number(v) > 0, 'Enter a value above 0'),
    currency: z.enum(CURRENCIES, { message: 'Choose a currency' }),
    counterpartyEmissions: z.string().trim().refine((v) => AMOUNT_6.test(v), 'Enter tCO2e (0 or more)'),
    dataQualityScore: z.string().refine((v) => ['1', '2', '3', '4', '5'].includes(v), 'Choose a score from 1 to 5'),
    emissionsSource: z.string().trim().min(1, 'Say where the emissions come from').max(255, 'At most 255 characters'),
  })
  .superRefine((v, ctx) => {
    if (MONEY.test(v.outstandingAmount) && MONEY.test(v.denominatorValue) && Number(v.outstandingAmount) > Number(v.denominatorValue)) {
      ctx.addIssue({ code: 'custom', path: ['outstandingAmount'], message: "Can't exceed the denominator (attribution above 1)" });
    }
  });

export const toFinancedBody = (v) => ({
  ...v, reportingYear: Number(v.reportingYear), counterparty: v.counterparty.trim(), sector: v.sector.trim() || null,
  outstandingAmount: Number(v.outstandingAmount), denominatorValue: Number(v.denominatorValue),
  counterpartyEmissions: Number(v.counterpartyEmissions), dataQualityScore: Number(v.dataQualityScore), emissionsSource: v.emissionsSource.trim(),
});

export const toFinancedForm = (p) => ({
  reportingYear: String(p.reportingYear), assetClass: p.assetClass, counterparty: p.counterparty, sector: p.sector ?? '',
  outstandingAmount: String(Number(p.outstandingAmount)), denominatorValue: String(Number(p.denominatorValue)), currency: p.currency,
  counterpartyEmissions: String(Number(p.counterpartyEmissions)), dataQualityScore: String(p.dataQualityScore), emissionsSource: p.emissionsSource,
});
