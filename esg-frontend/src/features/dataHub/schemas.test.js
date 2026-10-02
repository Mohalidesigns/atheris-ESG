import { describe, it, expect } from 'vitest';
import {
  dataPointSchema, dataSourceSchema, fxRateSchema, importStartSchema, reasonSchema, toDataPointBody, toDataPointForm, toImportStartBody,
} from './schemas';

const metrics = {
  electricity_kwh: { metricKey: 'electricity_kwh', dataType: 'decimal', unit: 'kWh' },
  employees_total: { metricKey: 'employees_total', dataType: 'integer', unit: 'count' },
  board_female_pct: { metricKey: 'board_female_pct', dataType: 'percentage', unit: '%' },
  board_size: { metricKey: 'board_size', dataType: 'integer', unit: 'count' },
  community_investment: { metricKey: 'community_investment', dataType: 'currency', unit: 'currency' },
  policy_text: { metricKey: 'policy_text', dataType: 'text', unit: 'text' },
};
// GET /data/rules (reference.metric_rules): only severity "error" range rules block the form.
const rules = [
  { code: 'board_size_minimum', metricKey: 'board_size', ruleType: 'range', severity: 'error', minValue: 1, maxValue: null, message: 'A board has at least one member' },
  { code: 'board_size_plausible', metricKey: 'board_size', ruleType: 'range', severity: 'warning', minValue: 3, maxValue: 30, message: 'unusual' },
];
const base = {
  orgId: 'org-1', metricKey: 'electricity_kwh', subCategory: '', value: '1', currency: '', sourceId: '', evidenceFileIds: [],
  reportingPeriodStart: '2026-07-01', reportingPeriodEnd: '2026-09-30',
};
const errorsFor = (v) => {
  const r = dataPointSchema(metrics, rules).safeParse({ ...base, ...v });
  return r.success ? {} : Object.fromEntries(r.error.issues.map((i) => [i.path[0], i.message]));
};

// Same cases as DataPointApiTest / DataPointWorkflowTest / testing/roundtrip/data-point*.json on the server.
describe('dataPointSchema mirrors DataPointDto + DataPointRules + metric error rules', () => {
  it.each([
    ['metricKey', { metricKey: 'made_up_metric' }],
    ['value', { value: '' }],
    ['value', { value: '-5' }],
    ['value', { metricKey: 'employees_total', value: '10.5' }],
    ['value', { metricKey: 'board_female_pct', value: '101' }],
    ['value', { value: '1.1234567' }],
    ['value', { value: '123456789012345' }],
    ['value', { metricKey: 'board_size', value: '0' }],
    ['subCategory', { subCategory: 'x'.repeat(101) }],
    ['reportingPeriodEnd', { reportingPeriodStart: '2026-09-30', reportingPeriodEnd: '2026-07-01' }],
    ['orgId', { orgId: '' }],
    ['value', { metricKey: 'policy_text', value: '' }],
    ['currency', { currency: 'USD' }],
    ['currency', { metricKey: 'community_investment', currency: 'BTC' }],
    ['evidenceFileIds', { evidenceFileIds: Array.from({ length: 11 }, (_, i) => `f${i}`) }],
  ])('%s: %j', (field, v) => {
    expect(errorsFor(v)).toHaveProperty(field);
  });

  it('a warning rule never blocks the form (the server stores it as a warning)', () => {
    expect(errorsFor({ metricKey: 'board_size', value: '45' })).toEqual({});
  });

  it.each([
    [{}], [{ value: '0' }], [{ value: '12450.123456' }], [{ metricKey: 'employees_total', value: '2845' }],
    [{ metricKey: 'board_female_pct', value: '100' }], [{ metricKey: 'policy_text', value: 'Yes' }],
    [{ metricKey: 'community_investment', value: '10', currency: 'USD' }],
    [{ evidenceFileIds: ['f1', 'f2'], sourceId: 'src-1' }],
  ])('accepts %j', (v) => {
    expect(errorsFor(v)).toEqual({});
  });

  it('flags an empty value on the first submit, even before a metric is chosen (Phase 2)', () => {
    expect(errorsFor({ metricKey: '', value: '' })).toMatchObject({ value: 'A value is required', metricKey: 'Choose a metric' });
  });

  it('sends numeric or text by metric type, the currency only for money metrics, and evidence and source', () => {
    expect(toDataPointBody({ ...base, value: '12.5', subCategory: ' Borehole ', evidenceFileIds: ['f1'], sourceId: 's1' }, metrics.electricity_kwh))
      .toEqual({
        orgId: 'org-1', metricKey: 'electricity_kwh', subCategory: 'Borehole', numericValue: 12.5, textValue: null, currency: null,
        sourceId: 's1', evidenceFileIds: ['f1'], reportingPeriodStart: '2026-07-01', reportingPeriodEnd: '2026-09-30', notes: null,
      });
    expect(toDataPointBody({ ...base, metricKey: 'community_investment', value: '10', currency: 'USD' }, metrics.community_investment))
      .toMatchObject({ numericValue: 10, currency: 'USD', sourceId: null });
    expect(toDataPointBody({ ...base, metricKey: 'policy_text', value: ' Yes ' }, metrics.policy_text))
      .toMatchObject({ numericValue: null, textValue: 'Yes', subCategory: null });
  });

  it('edit reload shows a converted amount in its original currency', () => {
    expect(toDataPointForm({ numericValue: 15000, originalAmount: 10, originalCurrency: 'USD', evidenceFileIds: ['f1'] }))
      .toMatchObject({ value: '10', currency: 'USD', evidenceFileIds: ['f1'] });
    expect(toDataPointForm({ numericValue: 420.5 })).toMatchObject({ value: '420.5', currency: '', evidenceFileIds: [] });
  });
});

const fieldsOf = (r) => (r.success ? [] : r.error.issues.map((i) => i.path.join('.')));

describe('other Data Hub forms mirror their DTOs', () => {
  it('reason: required, at most 1000', () => {
    expect(fieldsOf(reasonSchema.safeParse({ reason: '  ' }))).toEqual(['reason']);
    expect(fieldsOf(reasonSchema.safeParse({ reason: 'x'.repeat(1001) }))).toEqual(['reason']);
    expect(reasonSchema.safeParse({ reason: 'Meter photo missing' }).success).toBe(true);
  });

  it('data source (DataSourceRequest)', () => {
    const ok = { name: 'SAP', sourceType: 'erp', description: '', active: true };
    expect(dataSourceSchema.safeParse(ok).success).toBe(true);
    expect(fieldsOf(dataSourceSchema.safeParse({ ...ok, name: '' }))).toEqual(['name']);
    expect(fieldsOf(dataSourceSchema.safeParse({ ...ok, name: 'x'.repeat(256) }))).toEqual(['name']);
    expect(fieldsOf(dataSourceSchema.safeParse({ ...ok, sourceType: 'fax' }))).toEqual(['sourceType']);
    expect(fieldsOf(dataSourceSchema.safeParse({ ...ok, description: 'x'.repeat(501) }))).toEqual(['description']);
  });

  it('exchange rate (FxRateRequest)', () => {
    const ok = { fromCurrency: 'USD', toCurrency: 'NGN', rate: '1520.5', rateDate: '2026-03-31', source: 'CBN' };
    expect(fxRateSchema.safeParse(ok).success).toBe(true);
    expect(fieldsOf(fxRateSchema.safeParse({ ...ok, toCurrency: 'USD' }))).toEqual(['toCurrency']);
    expect(fieldsOf(fxRateSchema.safeParse({ ...ok, rate: '0' }))).toEqual(['rate']);
    expect(fieldsOf(fxRateSchema.safeParse({ ...ok, rate: '1000001' }))).toEqual(['rate']);
    expect(fieldsOf(fxRateSchema.safeParse({ ...ok, rate: '1.12345678901' }))).toEqual(['rate']);
    expect(fieldsOf(fxRateSchema.safeParse({ ...ok, fromCurrency: 'BTC' }))).toEqual(['fromCurrency']);
    expect(fieldsOf(fxRateSchema.safeParse({ ...ok, rateDate: '' }))).toEqual(['rateDate']);
    expect(fieldsOf(fxRateSchema.safeParse({ ...ok, source: ' ' }))).toEqual(['source']);
  });

  it('import mapping (ImportService.start): metric and value columns; organisation and period by column or default', () => {
    const empty = { mapping: {}, defaultOrgId: '', defaultPeriodStart: '', defaultPeriodEnd: '', sourceId: '' };
    expect(fieldsOf(importStartSchema.safeParse(empty)).sort()).toEqual(
      ['mapping.metric', 'mapping.organisation', 'mapping.periodEnd', 'mapping.periodStart', 'mapping.value']);
    const withDefaults = { ...empty, mapping: { metric: 'KPI', value: 'Amount' }, defaultOrgId: 'o1',
      defaultPeriodStart: '2026-09-01', defaultPeriodEnd: '2026-08-31' };
    expect(fieldsOf(importStartSchema.safeParse(withDefaults))).toEqual(['defaultPeriodEnd']);
    expect(toImportStartBody({ ...withDefaults, defaultPeriodEnd: '2026-09-30', mapping: { metric: 'KPI', value: 'Amount', currency: '' } }, 3))
      .toEqual({ version: 3, mapping: { metric: 'KPI', value: 'Amount' }, defaultOrgId: 'o1', defaultPeriodStart: '2026-09-01',
        defaultPeriodEnd: '2026-09-30', sourceId: null });
  });
});
