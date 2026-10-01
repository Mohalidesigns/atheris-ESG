import { z } from 'zod';

/**
 * Data point form rules. Mirrors the server exactly: DataPointDto (Bean Validation) + DataPointRules (metric
 * catalogue rules) in esg-backend. Keep the two in step.
 */
export const NUMERIC_TYPES = ['decimal', 'integer', 'percentage', 'currency'];
const DECIMAL = /^\d{1,14}(\.\d{1,6})?$/; // @Digits(integer = 14, fraction = 6); sign handled separately

export const emptyDataPoint = {
  orgId: '', metricKey: '', subCategory: '', value: '', reportingPeriodStart: '', reportingPeriodEnd: '',
};

/** Server field → form field. */
export const DATA_POINT_SERVER_FIELDS = { numericValue: 'value', textValue: 'value' };

export const dataPointSchema = (metricsByKey) => z
  .object({
    orgId: z.string().min(1, 'Choose an organisation'),
    metricKey: z.string().min(1, 'Choose a metric'),
    subCategory: z.string().trim().max(100, 'At most 100 characters'),
    value: z.string().trim(),
    reportingPeriodStart: z.string().min(1, 'Start date is required'),
    reportingPeriodEnd: z.string().min(1, 'End date is required'),
  })
  .superRefine((v, ctx) => {
    const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
    if (v.reportingPeriodStart && v.reportingPeriodEnd && v.reportingPeriodEnd < v.reportingPeriodStart) {
      issue('reportingPeriodEnd', 'Must be on or after the start date');
    }
    const metric = metricsByKey[v.metricKey];
    if (v.metricKey && !metric) {
      issue('metricKey', 'Is not in the metric catalogue');
      return;
    }
    if (!metric) return;
    if (NUMERIC_TYPES.includes(metric.dataType)) {
      if (v.value === '') return issue('value', 'A value is required');
      if (v.value.startsWith('-')) return issue('value', 'Must be zero or more');
      if (!DECIMAL.test(v.value)) return issue('value', 'Enter a number with up to 6 decimal places');
      const n = Number(v.value);
      if (metric.dataType === 'integer' && !Number.isInteger(n)) return issue('value', 'Must be a whole number');
      if (metric.dataType === 'percentage' && n > 100) return issue('value', 'Must be between 0 and 100');
    } else if (metric.dataType === 'text') {
      if (v.value === '') return issue('value', 'A value is required');
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
    reportingPeriodStart: v.reportingPeriodStart,
    reportingPeriodEnd: v.reportingPeriodEnd,
  };
}
