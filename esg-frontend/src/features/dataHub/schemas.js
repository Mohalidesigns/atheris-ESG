import { z } from 'zod';
import { CURRENCIES } from '../admin/schemas';

/**
 * Data Hub form rules. Mirror the server exactly: DataPointDto (Bean Validation) + DataPointRules (type rules) +
 * reference.metric_rules severity "error" (fetched from GET /data/rules) for data points; DataHubDtos for sources and
 * exchange rates; ImportService.start for the import mapping. Keep them in step.
 */
// no-mock-gate: config — metric data types that take a number (mirrors DataPointRules.NUMERIC_TYPES)
export const NUMERIC_TYPES = ['decimal', 'integer', 'percentage', 'currency'];
const DECIMAL = /^\d{1,14}(\.\d{1,6})?$/; // @Digits(integer = 14, fraction = 6); sign handled separately
export const MAX_EVIDENCE = 10;

// no-mock-gate: config — workflow status labels and colours
export const STATUS = {
  draft: { label: 'Draft', color: '#4A5568', bg: '#EDF2F7' },
  submitted: { label: 'Submitted', color: '#975A16', bg: '#FEF9E7' },
  validated: { label: 'Validated', color: '#2B6CB0', bg: '#EBF4FF' },
  approved: { label: 'Approved', color: '#2D7D46', bg: '#E6F4EA' },
  rejected: { label: 'Rejected', color: '#C53030', bg: '#FEE2E2' },
};

// no-mock-gate: config — source system kinds (enum mirrors DataHubDtos.SOURCE_TYPE_REGEX). A person is never a source (Phase 3).
export const SOURCE_TYPES = {
  manual: 'Manual entry', csv: 'CSV file', excel: 'Excel workbook', api: 'API', erp: 'ERP system',
  mobile: 'Mobile app', iot: 'IoT sensor', sms: 'SMS / USSD', meter: 'Meter',
};

export const emptyDataPoint = {
  orgId: '', metricKey: '', subCategory: '', value: '', currency: '', sourceId: '', ownerUserId: '', evidenceFileIds: [],
  reportingPeriodStart: '', reportingPeriodEnd: '', notes: '',
};

/** Server field → form field. */
export const DATA_POINT_SERVER_FIELDS = { numericValue: 'value', textValue: 'value' };

/** Saved data point → form values (edit reload). */
export const toDataPointForm = (dp) => ({
  orgId: dp.orgId ?? '',
  metricKey: dp.metricKey ?? '',
  subCategory: dp.subCategory ?? '',
  // A converted amount is edited in its original currency.
  value: dp.originalAmount != null ? String(dp.originalAmount)
    : dp.numericValue != null ? String(Number(dp.numericValue)) : dp.textValue ?? '',
  currency: dp.originalCurrency ?? '',
  sourceId: dp.sourceId ?? '',
  ownerUserId: dp.ownerUserId ?? '',
  evidenceFileIds: dp.evidenceFileIds ?? [],
  reportingPeriodStart: dp.reportingPeriodStart ?? '',
  reportingPeriodEnd: dp.reportingPeriodEnd ?? '',
  notes: dp.notes ?? '',
});

/** @param rules GET /data/rules — only severity "error" range rules block saving (warnings are server-side). */
export const dataPointSchema = (metricsByKey, rules = []) => z
  .object({
    orgId: z.string().min(1, 'Choose an organisation'),
    metricKey: z.string().min(1, 'Choose a metric'),
    subCategory: z.string().trim().max(100, 'At most 100 characters'),
    value: z.string().trim(),
    currency: z.string(),
    sourceId: z.string(),
    ownerUserId: z.string().optional(),
    evidenceFileIds: z.array(z.string()).max(MAX_EVIDENCE, `At most ${MAX_EVIDENCE} evidence files`),
    reportingPeriodStart: z.string().min(1, 'Start date is required'),
    reportingPeriodEnd: z.string().min(1, 'End date is required'),
    notes: z.string().trim().max(1000, 'At most 1000 characters').optional(),
  })
  .superRefine((v, ctx) => {
    const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
    if (v.reportingPeriodStart && v.reportingPeriodEnd && v.reportingPeriodEnd < v.reportingPeriodStart) {
      issue('reportingPeriodEnd', 'Must be on or after the start date');
    }
    // Phase 2: an empty value is flagged on the first submit, before a metric is chosen too.
    if (v.value === '') issue('value', 'A value is required');
    const metric = metricsByKey[v.metricKey];
    if (v.metricKey && !metric) {
      issue('metricKey', 'Is not in the metric catalogue');
      return;
    }
    if (!metric) return;
    if (v.currency && metric.dataType !== 'currency') issue('currency', 'Only applies to currency metrics');
    if (v.currency && !CURRENCIES.includes(v.currency)) issue('currency', `Must be one of ${CURRENCIES.join(', ')}`);
    if (NUMERIC_TYPES.includes(metric.dataType)) {
      if (v.value === '') return undefined;
      if (v.value.startsWith('-')) return issue('value', 'Must be zero or more');
      if (!DECIMAL.test(v.value)) return issue('value', 'Enter a number with up to 6 decimal places');
      const n = Number(v.value);
      if (metric.dataType === 'integer' && !Number.isInteger(n)) return issue('value', 'Must be a whole number');
      if (metric.dataType === 'percentage' && n > 100) return issue('value', 'Must be between 0 and 100');
      const broken = rules.find((r) => r.metricKey === metric.metricKey && r.ruleType === 'range' && r.severity === 'error'
        && ((r.minValue != null && n < Number(r.minValue)) || (r.maxValue != null && n > Number(r.maxValue))));
      if (broken) return issue('value', broken.message);
    } else if (metric.dataType === 'text') {
      if (v.value === '') return undefined;
      if (v.value.length > 2000) return issue('value', 'At most 2000 characters');
    }
    return undefined;
  });

export function toDataPointBody(v, metric) {
  const numeric = NUMERIC_TYPES.includes(metric.dataType);
  return {
    orgId: v.orgId,
    metricKey: v.metricKey,
    subCategory: v.subCategory.trim() || null,
    numericValue: numeric ? Number(v.value) : null,
    textValue: numeric ? null : v.value.trim(),
    currency: metric.dataType === 'currency' && v.currency ? v.currency : null,
    sourceId: v.sourceId || null,
    // Empty: the server uses the source system's owner, else the person saving it (Phase 3).
    ownerUserId: v.ownerUserId || null,
    evidenceFileIds: v.evidenceFileIds,
    reportingPeriodStart: v.reportingPeriodStart,
    reportingPeriodEnd: v.reportingPeriodEnd,
    notes: (v.notes ?? '').trim() || null,
  };
}

/** Reject and reopen need a reason (TransitionRequest: required by the service, ≤1000). */
export const reasonSchema = z.object({
  reason: z.string().trim().min(1, 'A reason is required').max(1000, 'At most 1000 characters'),
});

// ---------------------------------------------------------------- data sources (DataSourceRequest)

export const emptySource = { name: '', sourceType: 'manual', description: '', ownerUserId: '', active: true };

/** @param people GET /data/owners — a name that is a colleague's full name is refused, as on the server. */
export const dataSourceSchema = (people = []) => z.object({
  name: z.string().trim().min(1, 'Name is required').max(255, 'At most 255 characters')
    .refine((n) => !people.some((p) => p.name.toLowerCase() === n.toLowerCase()),
      'This is a person\'s name. Name the system (e.g. "Generator meter #2") and choose the person as the owner'),
  sourceType: z.enum(Object.keys(SOURCE_TYPES), { message: 'Choose a type' }),
  description: z.string().trim().max(500, 'At most 500 characters'),
  ownerUserId: z.string().optional(),
  active: z.boolean(),
});

export const toSourceBody = (v) => ({
  ...v, name: v.name.trim(), description: v.description.trim() || null, ownerUserId: v.ownerUserId || null,
});

// ---------------------------------------------------------------- exchange rates (FxRateRequest)

const RATE = /^\d{1,10}(\.\d{1,10})?$/; // @Digits(integer = 10, fraction = 10)

export const emptyRate = { fromCurrency: 'USD', toCurrency: 'NGN', rate: '', rateDate: '', source: '' };

export const fxRateSchema = z
  .object({
    fromCurrency: z.enum(CURRENCIES, { message: 'Choose a currency' }),
    toCurrency: z.enum(CURRENCIES, { message: 'Choose a currency' }),
    rate: z.string().trim(),
    rateDate: z.string().min(1, 'Rate date is required'),
    source: z.string().trim().min(1, 'Say where the rate comes from').max(255, 'At most 255 characters'),
  })
  .superRefine((v, ctx) => {
    if (v.fromCurrency === v.toCurrency) ctx.addIssue({ code: 'custom', path: ['toCurrency'], message: 'Must differ from the source currency' });
    if (!RATE.test(v.rate) || Number(v.rate) <= 0 || Number(v.rate) > 1000000) {
      ctx.addIssue({ code: 'custom', path: ['rate'], message: 'Enter a rate above 0 and up to 1,000,000 (10 decimals max)' });
    }
  });

export const toRateBody = (v) => ({ ...v, rate: Number(v.rate), source: v.source.trim() });

// ---------------------------------------------------------------- import mapping (ImportService.start)

// no-mock-gate: config — fields a file column can map to (mirrors ImportDtos.FIELDS)
export const IMPORT_FIELDS = [
  { key: 'organisation', label: 'Organisation', hint: 'name or id' },
  { key: 'metric', label: 'Metric', hint: 'key or name', required: true },
  { key: 'value', label: 'Value', required: true },
  { key: 'currency', label: 'Currency', hint: 'currency metrics only' },
  { key: 'periodStart', label: 'Period start' },
  { key: 'periodEnd', label: 'Period end' },
  { key: 'subCategory', label: 'Sub-category' },
  { key: 'notes', label: 'Notes', hint: 'comment or remark' },
  { key: 'owner', label: 'Responsible person', hint: 'email or full name' },
];

export const importStartSchema = z
  .object({
    mapping: z.record(z.string(), z.string()),
    defaultOrgId: z.string(),
    defaultPeriodStart: z.string(),
    defaultPeriodEnd: z.string(),
    sourceId: z.string(),
  })
  .superRefine((v, ctx) => {
    const issue = (path, message) => ctx.addIssue({ code: 'custom', path, message });
    if (!v.mapping.metric) issue(['mapping', 'metric'], 'Map a column to this field');
    if (!v.mapping.value) issue(['mapping', 'value'], 'Map a column to this field');
    if (!v.mapping.organisation && !v.defaultOrgId) issue(['mapping', 'organisation'], 'Map a column or choose an organisation for every row');
    if (!v.mapping.periodStart && !v.defaultPeriodStart) issue(['mapping', 'periodStart'], 'Map a column or enter a start date for every row');
    if (!v.mapping.periodEnd && !v.defaultPeriodEnd) issue(['mapping', 'periodEnd'], 'Map a column or enter an end date for every row');
    if (v.defaultPeriodStart && v.defaultPeriodEnd && v.defaultPeriodEnd < v.defaultPeriodStart) {
      issue(['defaultPeriodEnd'], 'Must be on or after the start date');
    }
  });

export const toImportStartBody = (v, version) => ({
  version,
  mapping: Object.fromEntries(Object.entries(v.mapping).filter(([, column]) => column)),
  defaultOrgId: v.defaultOrgId || null,
  defaultPeriodStart: v.defaultPeriodStart || null,
  defaultPeriodEnd: v.defaultPeriodEnd || null,
  sourceId: v.sourceId || null,
});
