import { z } from 'zod';

/**
 * Analytics form rules. Mirror the server exactly: AnalyticsDtos (Bean Validation) + KpiService.check and
 * BenchmarkService.check. Keep them in step.
 */
// no-mock-gate: config — option labels (AnalyticsDtos regexes, V031 checks)
export const PILLARS = { E: 'Environmental', S: 'Social', G: 'Governance' };
export const OPERATORS = { none: '— (the metric alone)', divide: '÷ divided by', multiply: '× multiplied by', add: '+ plus', subtract: '− minus' };
export const DIRECTIONS = { lower_better: 'Lower', higher_better: 'Higher' };
export const KPI_STATUS = { active: 'Active', archived: 'Archived' };
export const ANOMALY_STATUS = {
  open: { label: 'Open', color: 'warning' }, explained: { label: 'Explained', color: 'info' }, accepted: { label: 'Accepted', color: 'success' },
  dismissed: { label: 'Dismissed', color: 'default' }, cleared: { label: 'Cleared', color: 'default' },
};
export const ANOMALY_KINDS = { outlier: 'Outlier', variance: 'Year-on-year change', ml: 'Machine learning (AI)' };
export const ANOMALY_ACTIONS = { explain: 'Explain', accept: 'Accept explanation', return: 'Send back', dismiss: 'Dismiss as false positive' };
export const METHODS = { z_score: 'z-score', iqr: 'IQR fences', both: 'z-score and IQR', yoy: 'same period last year', iforest: 'Isolation Forest (AI)' };
export const BANDS = { 1: 'Best quarter', 2: 'Second quarter', 3: 'Third quarter', 4: 'Bottom quarter' };
export const SDG_GOALS = Array.from({ length: 17 }, (_, i) => i + 1);

const blank = (v) => (v == null ? '' : String(v));
const nul = (v) => (v === '' || v == null ? null : v);
const year = (label) => z.string().trim().refine((v) => v === '' || (/^\d{4}$/.test(v) && Number(v) >= 2000 && Number(v) <= 2100), `${label}: a year from 2000 to 2100`);
// @Digits(integer = 14, fraction = 6)
const decimal = /^-?\d{1,14}(\.\d{1,6})?$/;

// ---------------------------------------------------------------- custom KPI (KpiRequest)

export const emptyKpi = {
  code: '', name: '', description: '', pillar: 'E', orgId: '', metricA: '', operator: 'none', metricB: '', multiplier: '1', unit: '', decimals: '2',
  direction: 'lower_better', baselineYear: '', targetValue: '', targetYear: '', sdgGoals: [], status: 'active',
};

export const toKpiForm = (k) => ({
  ...Object.fromEntries(Object.keys(emptyKpi).map((key) => [key, blank(k[key])])),
  sdgGoals: (k.sdgGoals ?? []).map(Number),
});

export const kpiSchema = z.object({
  code: z.string().trim().min(1, 'Code is required').max(40, 'At most 40 characters')
    .regex(/^[a-z][a-z0-9_]*$/, 'lower-case letters, digits and underscores, starting with a letter'),
  name: z.string().trim().min(1, 'Name is required').max(150, 'At most 150 characters'),
  description: z.string().trim().max(1000, 'At most 1000 characters'),
  pillar: z.enum(Object.keys(PILLARS)),
  orgId: z.string(),
  metricA: z.string().min(1, 'Choose the metric'),
  operator: z.enum(Object.keys(OPERATORS)),
  metricB: z.string(),
  multiplier: z.string().trim().refine((v) => decimal.test(v), 'A number (up to 6 decimals)').refine((v) => Number(v) !== 0, "can't be zero"),
  unit: z.string().trim().min(1, 'Unit is required').max(30, 'At most 30 characters'),
  decimals: z.string().refine((v) => /^[0-4]$/.test(v), 'A whole number from 0 to 4'),
  direction: z.enum(Object.keys(DIRECTIONS)),
  baselineYear: year('Baseline year'),
  targetValue: z.string().trim().refine((v) => v === '' || decimal.test(v), 'A number (up to 6 decimals)'),
  targetYear: year('Target year'),
  sdgGoals: z.array(z.number().int().min(1).max(17)).max(17),
  status: z.enum(Object.keys(KPI_STATUS)),
}).superRefine((v, ctx) => {
  const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
  if (v.operator === 'none' && v.metricB) issue('metricB', 'leave empty when there is no second metric');
  if (v.operator !== 'none' && !v.metricB) issue('metricB', 'is required for this operation');
  if ((v.targetValue.trim() === '') !== (v.targetYear.trim() === '')) {
    issue(v.targetValue.trim() === '' ? 'targetValue' : 'targetYear', 'set both the target value and the target year, or neither');
  }
  if (v.baselineYear && v.targetYear && Number(v.targetYear) <= Number(v.baselineYear)) issue('targetYear', 'must be after the baseline year');
});

const num = (v) => (v === '' || v == null ? null : Number(v));
export const toKpiBody = (v) => ({
  code: v.code.trim(), name: v.name.trim(), description: nul(v.description.trim()), pillar: v.pillar, orgId: nul(v.orgId), metricA: v.metricA,
  operator: v.operator, metricB: v.operator === 'none' ? null : nul(v.metricB), multiplier: Number(v.multiplier), unit: v.unit.trim(),
  decimals: Number(v.decimals), direction: v.direction, baselineYear: num(v.baselineYear), targetValue: num(v.targetValue.trim()), targetYear: num(v.targetYear),
  sdgGoals: [...v.sdgGoals].sort((a, b) => a - b), status: v.status,
});

// ---------------------------------------------------------------- benchmark dataset (BenchmarkDatasetRequest)

export const emptyBenchmarkValue = { metricKey: '', dataYear: '', p25: '', median: '', p75: '', sampleSize: '' };
export const emptyDataset = {
  name: '', publisher: '', publicationYear: '', sourceUrl: '', sectorCode: '', countryCode: '', sourceFileId: '', notes: '', status: 'active',
  values: [{ ...emptyBenchmarkValue }],
};

export const toDatasetForm = (d) => ({
  ...Object.fromEntries(Object.keys(emptyDataset).filter((k) => k !== 'values').map((k) => [k, blank(d[k])])),
  values: (d.values ?? []).map((v) => Object.fromEntries(Object.keys(emptyBenchmarkValue).map((k) => [k, blank(v[k])]))),
});

const required = (label) => z.string().trim().min(1, `${label} is required`).refine((v) => decimal.test(v), `${label}: a number (up to 6 decimals)`);

export const datasetSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
  publisher: z.string().trim().min(1, 'Publisher is required').max(200, 'At most 200 characters'),
  publicationYear: z.string().trim().min(1, 'Publication year is required').pipe(year('Publication year')),
  sourceUrl: z.string().trim().max(500, 'At most 500 characters').refine((v) => v === '' || /^https?:\/\/.+/.test(v), 'must start with http:// or https://'),
  sectorCode: z.string(),
  countryCode: z.string().trim().refine((v) => v === '' || /^[A-Z]{2}$/.test(v), 'must be a 2-letter country code'),
  sourceFileId: z.string().min(1, 'attach the source document'),
  notes: z.string().trim().max(1000, 'At most 1000 characters'),
  status: z.enum(Object.keys(KPI_STATUS)),
  values: z.array(z.object({
    metricKey: z.string().min(1, 'Choose the metric'),
    dataYear: z.string().trim().min(1, 'Required').pipe(year('Data year')),
    p25: required('P25'),
    median: required('Median'),
    p75: required('P75'),
    sampleSize: z.string().trim().refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= 1), 'A whole number from 1'),
  })).min(1, 'add at least one benchmark value').max(200, 'At most 200 values'),
}).superRefine((v, ctx) => {
  const seen = new Set();
  v.values.forEach((row, i) => {
    const key = `${row.metricKey}|${row.dataYear}`;
    if (row.metricKey && seen.has(key)) ctx.addIssue({ code: 'custom', path: ['values', i, 'metricKey'], message: 'this metric and year is already in the dataset' });
    seen.add(key);
    if (Number(row.p25) > Number(row.median) || Number(row.median) > Number(row.p75)) {
      ctx.addIssue({ code: 'custom', path: ['values', i, 'median'], message: 'P25 ≤ median ≤ P75' });
    }
  });
});

export const toDatasetBody = (v) => ({
  name: v.name.trim(), publisher: v.publisher.trim(), publicationYear: Number(v.publicationYear), sourceUrl: nul(v.sourceUrl.trim()), sectorCode: nul(v.sectorCode),
  countryCode: nul(v.countryCode.trim()), sourceFileId: v.sourceFileId, notes: nul(v.notes.trim()), status: v.status,
  values: v.values.map((r) => ({ metricKey: r.metricKey, dataYear: Number(r.dataYear), p25: Number(r.p25), median: Number(r.median), p75: Number(r.p75),
    sampleSize: num(r.sampleSize.trim()) })),
});

// ---------------------------------------------------------------- sensitivity (SettingsRequest)

export const toSettingsForm = (s) => ({ zscoreThreshold: blank(s.zscoreThreshold), iqrK: blank(s.iqrK), variancePct: blank(s.variancePct),
  minHistory: blank(s.minHistory) });
const between = (min, max, scale, label) => z.string().trim().refine((v) => new RegExp(`^\\d+(\\.\\d{1,${scale}})?$`).test(v) && Number(v) >= min && Number(v) <= max,
  `${label}: from ${min} to ${max}`);
export const settingsSchema = z.object({
  zscoreThreshold: between(2, 5, 2, 'Z-score threshold'),
  iqrK: between(1, 3, 1, 'IQR multiplier'),
  variancePct: between(5, 500, 1, 'Change'),
  minHistory: z.string().trim().refine((v) => /^\d+$/.test(v) && Number(v) >= 3 && Number(v) <= 20, 'A whole number from 3 to 20'),
});
export const toSettingsBody = (v) => ({ zscoreThreshold: Number(v.zscoreThreshold), iqrK: Number(v.iqrK), variancePct: Number(v.variancePct),
  minHistory: Number(v.minHistory) });

// ---------------------------------------------------------------- small helpers

export const explainSchema = z.object({ note: z.string().trim().min(1, 'Write the explanation').max(2000, 'At most 2000 characters') });
export const prioritySchema = z.object({ rationale: z.string().trim().min(1, 'Say why this goal is a priority').max(1000, 'At most 1000 characters') });
export const fmt = (v, digits = 2) => (v == null ? '—' : Number(v).toLocaleString(undefined, { maximumFractionDigits: digits }));
export const lagosYear = () => Number(new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' }).slice(0, 4));
export const problemText = (err) => err?.data?.detail ?? Object.values(err?.data?.errors ?? {})[0] ?? 'Something went wrong';
