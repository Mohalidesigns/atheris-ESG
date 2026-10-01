import { describe, it, expect } from 'vitest';
import { dataPointSchema, toDataPointBody } from './schemas';

const metrics = {
  electricity_kwh: { metricKey: 'electricity_kwh', dataType: 'decimal', unit: 'kWh' },
  employees_total: { metricKey: 'employees_total', dataType: 'integer', unit: 'count' },
  board_female_pct: { metricKey: 'board_female_pct', dataType: 'percentage', unit: '%' },
  policy_text: { metricKey: 'policy_text', dataType: 'text', unit: 'text' },
};
const base = { orgId: 'org-1', metricKey: 'electricity_kwh', subCategory: '', value: '1', reportingPeriodStart: '2026-07-01', reportingPeriodEnd: '2026-09-30' };
const errorsFor = (v) => {
  const r = dataPointSchema(metrics).safeParse({ ...base, ...v });
  return r.success ? {} : Object.fromEntries(r.error.issues.map((i) => [i.path[0], i.message]));
};

// Same cases as DataPointApiTest.invalid() on the server.
describe('dataPointSchema mirrors DataPointDto + DataPointRules', () => {
  it.each([
    ['metricKey', { metricKey: 'made_up_metric' }],
    ['value', { value: '' }],
    ['value', { value: '-5' }],
    ['value', { metricKey: 'employees_total', value: '10.5' }],
    ['value', { metricKey: 'board_female_pct', value: '101' }],
    ['value', { value: '1.1234567' }],
    ['value', { value: '123456789012345' }],
    ['subCategory', { subCategory: 'x'.repeat(101) }],
    ['reportingPeriodEnd', { reportingPeriodStart: '2026-09-30', reportingPeriodEnd: '2026-07-01' }],
    ['orgId', { orgId: '' }],
    ['value', { metricKey: 'policy_text', value: '' }],
  ])('%s: %j', (field, v) => {
    expect(errorsFor(v)).toHaveProperty(field);
  });

  it.each([
    [{}], [{ value: '0' }], [{ value: '12450.123456' }], [{ metricKey: 'employees_total', value: '2845' }],
    [{ metricKey: 'board_female_pct', value: '100' }], [{ metricKey: 'policy_text', value: 'Yes' }],
  ])('accepts %j', (v) => {
    expect(errorsFor(v)).toEqual({});
  });

  it('sends numeric or text value by metric type, never category or unit (the server takes them from the catalogue)', () => {
    expect(toDataPointBody({ ...base, value: '12.5', subCategory: ' Borehole ' }, metrics.electricity_kwh)).toEqual({
      orgId: 'org-1', metricKey: 'electricity_kwh', subCategory: 'Borehole', numericValue: 12.5, textValue: null,
      reportingPeriodStart: '2026-07-01', reportingPeriodEnd: '2026-09-30',
    });
    expect(toDataPointBody({ ...base, metricKey: 'policy_text', value: ' Yes ' }, metrics.policy_text))
      .toMatchObject({ numericValue: null, textValue: 'Yes', subCategory: null });
  });
});
