import { z } from 'zod';

/** WP-50 form rules. Mirror the server exactly: AiDtos (Bean Validation) + ForecastService / ExtractionService checks. */
// no-mock-gate: config — option labels (AiDtos.TARGET_REGEX)
export const TARGETS = { emissions_total: 'Total emissions (Scopes 1–3)', scope_1: 'Scope 1 emissions', scope_2: 'Scope 2 emissions (location-based)',
  scope_3: 'Scope 3 emissions', metric: 'A catalogue metric' };
export const FREQUENCIES = { month: 'Monthly', year: 'Yearly' };
export const LEVELS = { 80: '80%', 90: '90%', 95: '95%' };
export const RUN_STATUS = { running: { label: 'Running', color: 'info' }, completed: { label: 'Completed', color: 'success' }, failed: { label: 'Failed', color: 'error' } };
export const VALUE_STATUS = { proposed: { label: 'To review', color: 'warning' }, accepted: { label: 'Accepted', color: 'success' }, rejected: { label: 'Rejected', color: 'default' } };

export const emptyForecast = { target: 'emissions_total', metricKey: '', frequency: 'month', horizon: '12', levelPct: '80' };
export const forecastSchema = z.object({
  target: z.enum(Object.keys(TARGETS)),
  metricKey: z.string(),
  frequency: z.enum(Object.keys(FREQUENCIES)),
  horizon: z.string().regex(/^\d+$/, 'A whole number').refine((v) => Number(v) >= 1 && Number(v) <= 36, 'Between 1 and 36'),
  levelPct: z.string().refine((v) => Number(v) >= 50 && Number(v) <= 95, 'Between 50 and 95'),
}).superRefine((v, ctx) => {
  if (v.target === 'metric' && !v.metricKey) ctx.addIssue({ code: 'custom', path: ['metricKey'], message: 'choose the metric to forecast' });
  if (v.target === 'metric' && v.frequency !== 'year') ctx.addIssue({ code: 'custom', path: ['frequency'], message: 'metrics are forecast by year' });
  if (v.frequency === 'year' && Number(v.horizon) > 10) ctx.addIssue({ code: 'custom', path: ['horizon'], message: 'at most 10 years ahead' });
});
export const toForecastBody = (v) => ({ target: v.target, metricKey: v.target === 'metric' ? v.metricKey : null, frequency: v.frequency,
  horizon: Number(v.horizon), levelPct: Number(v.levelPct) });

export const dismissSchema = z.object({ note: z.string().trim().min(1, 'say why it is set aside').max(1000, 'At most 1000 characters') });

export const acceptSchema = z.object({
  orgId: z.string().min(1, 'Organisation is required'),
  metricKey: z.string().min(1, 'Metric is required'),
  value: z.string().trim().regex(/^-?\d{1,14}(\.\d{1,6})?$/, 'A number (up to 6 decimals)'),
  periodStart: z.string().min(1, 'Start is required'),
  periodEnd: z.string().min(1, 'End is required'),
  note: z.string().trim().max(500, 'At most 500 characters'),
}).refine((v) => !v.periodStart || !v.periodEnd || v.periodEnd >= v.periodStart, { path: ['periodEnd'], message: "can't be before the start" });

export const scanSchema = z.object({ contaminationPct: z.string().regex(/^\d+$/, 'A whole percentage').refine((v) => Number(v) >= 1 && Number(v) <= 20, 'Between 1 and 20') });
