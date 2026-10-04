import { describe, it, expect } from 'vitest';
import { createUserSchema, passwordProblems, roleAssignmentsSchema, toAssignmentsBody, toLocalInput, updateUserSchema } from './schemas';

const valid = {
  email: 'ngozi@fixture.test', password: 'Str0ng!Passw0rd', firstName: 'Ngozi', lastName: 'Eze',
  phone: '+2348031234567', preferredLanguage: 'fr', timezone: 'Africa/Nairobi',
};
const fieldErrors = (schema, values) => {
  const result = schema.safeParse(values);
  return result.success ? {} : Object.fromEntries(result.error.issues.map((i) => [i.path.join('.'), i.message]));
};

// Same invalid cases as AdminUserApiTest.invalidCreates on the server (CLAUDE.md rule 5).
describe('createUserSchema mirrors CreateUserRequest + PasswordPolicy', () => {
  it('accepts a valid user', () => expect(fieldErrors(createUserSchema, valid)).toEqual({}));
  it.each([
    ['email', { email: 'not-an-email' }],
    ['email', { email: '' }],
    ['firstName', { firstName: '' }],
    ['lastName', { lastName: 'x'.repeat(101) }],
    ['phone', { phone: '12ab' }],
    ['preferredLanguage', { preferredLanguage: 'de' }],
    ['password', { password: 'weakpassword' }],
  ])('rejects %s %o', (field, override) => {
    expect(fieldErrors(createUserSchema, { ...valid, ...override })).toHaveProperty(field);
  });
  it('allows an empty phone (optional)', () => expect(fieldErrors(createUserSchema, { ...valid, phone: '' })).toEqual({}));
});

describe('passwordProblems mirrors PasswordPolicy', () => {
  it.each([
    ['Str0ng!Passw0rd', 'ada@example.com', []],
    ['Short1!', 'x@y.z', ['at least 12 characters']],
    ['alllowercase1!', 'x@y.z', ['an upper-case letter']],
    ['ALLUPPERCASE1!', 'x@y.z', ['a lower-case letter']],
    ['NoDigitsHere!!', 'x@y.z', ['a digit']],
    ['NoSymbols12345', 'x@y.z', ['a symbol']],
    ['Adaeze!2026Strong', 'adaeze@example.com', ['must not contain your email name']],
  ])('%s', (password, email, expected) => expect(passwordProblems(password, email)).toEqual(expected));
});

describe('updateUserSchema', () => {
  it('requires names and validates phone', () => {
    const errors = fieldErrors(updateUserSchema, { firstName: '', lastName: 'B', phone: 'abc', preferredLanguage: 'en', timezone: '' });
    expect(Object.keys(errors).sort()).toEqual(['firstName', 'phone']);
  });
});

describe('roleAssignmentsSchema', () => {
  const schema = roleAssignmentsSchema({ r1: 'org', r2: 'tenant' });
  it('requires an organisation for org-scoped roles only', () => {
    expect(fieldErrors(schema, { assignments: [{ roleId: 'r1', orgScopeId: '', expiresAt: '' }] }))
      .toHaveProperty('assignments.0.orgScopeId');
    expect(fieldErrors(schema, { assignments: [{ roleId: 'r2', orgScopeId: '', expiresAt: '' }] })).toEqual({});
  });
  it('rejects a past expiry', () => {
    expect(fieldErrors(schema, { assignments: [{ roleId: 'r2', orgScopeId: '', expiresAt: '2020-01-01T00:00' }] }))
      .toHaveProperty('assignments.0.expiresAt');
  });
  it('converts to the API body and back without shifting the time', () => {
    const iso = '2030-05-01T09:30:00.000Z';
    const local = toLocalInput(iso);
    expect(toAssignmentsBody([{ roleId: 'r2', orgScopeId: '', expiresAt: local }]))
      .toEqual([{ roleId: 'r2', orgScopeId: null, expiresAt: iso }]);
  });
});
