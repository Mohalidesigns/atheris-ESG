import { describe, it, expect } from 'vitest';
import { createTenantSchema, organisationSchema, periodSchema, settingsSchema, toOrganisationBody } from './schemas';

const issues = (schema, v) => {
  const r = schema.safeParse(v);
  return r.success ? {} : Object.fromEntries(r.error.issues.map((i) => [i.path.join('.'), i.message]));
};

// Mirrors TenancyDtos + TenancyApiTest validation cases.
describe('organisationSchema', () => {
  const ok = { name: 'Warri', parentOrgId: '', orgType: 'facility', countryCode: 'NG', stateRegion: '', address: '', latitude: '5.5', longitude: '5.75' };
  it('accepts a valid organisation', () => expect(issues(organisationSchema, ok)).toEqual({}));
  it.each([
    ['orgType', { orgType: 'castle' }],
    ['latitude', { latitude: '91' }],
    ['longitude', { longitude: 'abc' }],
    ['name', { name: '' }],
    ['countryCode', { countryCode: 'nigeria' }],
  ])('rejects %s', (field, o) => expect(issues(organisationSchema, { ...ok, ...o })).toHaveProperty(field));
  it('maps blanks to null and numbers to numbers', () => {
    expect(toOrganisationBody({ ...ok, latitude: '', countryCode: '' })).toMatchObject({ latitude: null, longitude: 5.75, countryCode: null, parentOrgId: null });
  });
});

describe('periodSchema', () => {
  it('end must not be before start', () => {
    expect(issues(periodSchema, { name: 'X', periodType: 'quarterly', startDate: '2028-06-30', endDate: '2028-04-01' })).toHaveProperty('endDate');
  });
  it('period type is an enum', () => {
    expect(issues(periodSchema, { name: 'X', periodType: 'weekly', startDate: '2029-01-01', endDate: '2029-01-07' })).toHaveProperty('periodType');
  });
});

describe('settingsSchema', () => {
  it('rejects unknown currency and month 13', () => {
    const e = issues(settingsSchema, { baseCurrency: 'BTC', fiscalYearStartMonth: 13, reportingTimezone: 'x', defaultFrameworkCodes: [] });
    expect(Object.keys(e).sort()).toEqual(['baseCurrency', 'fiscalYearStartMonth']);
  });
});

describe('createTenantSchema', () => {
  const ok = { name: 'Acme', slug: 'acme-bank', countryCode: 'NG', industrySector: '', subscriptionTier: 'starter', billingCurrency: 'NGN',
    maxUsers: '10', adminEmail: 'bola@acme.test', adminFirstName: 'Bola', adminLastName: 'Ade', adminPassword: 'Str0ng!Passw0rd' };
  it('accepts a valid tenant', () => expect(issues(createTenantSchema, ok)).toEqual({}));
  it('checks slug format and admin password policy', () => {
    const e = issues(createTenantSchema, { ...ok, slug: 'Acme Bank', adminPassword: 'weak' });
    expect(Object.keys(e).sort()).toEqual(['adminPassword', 'slug']);
  });
});
