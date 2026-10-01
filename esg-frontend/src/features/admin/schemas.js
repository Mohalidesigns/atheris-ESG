import { z } from 'zod';

/*
 * Mirrors the server exactly (CLAUDE.md rule 5):
 *   AdminDtos.CreateUserRequest / UpdateUserRequest / RoleAssignmentRequest (Bean Validation)
 *   PasswordPolicy (≥12 chars, upper, lower, digit, symbol, not containing the email name)
 * schemas.test.js checks these rules case by case against the same values the API tests use.
 */
export const PHONE_REGEX = /^\+?[0-9]{7,15}$/;
export const LANGUAGES = ['en', 'fr', 'pt', 'ar', 'sw', 'ha', 'yo'];
export const PASSWORD_MIN = 12;
export const PASSWORD_MAX = 128;

const optionalPhone = z
  .string()
  .trim()
  .max(20, 'At most 20 characters')
  .refine((v) => v === '' || PHONE_REGEX.test(v), 'Must be 7-15 digits, optionally starting with +');

const name = (label) => z.string().trim().min(1, `${label} is required`).max(100, 'At most 100 characters');

/** Same rules as PasswordPolicy.violations(); returns the list of unmet requirements. */
export function passwordProblems(password, email) {
  const problems = [];
  if (password.length < PASSWORD_MIN) problems.push(`at least ${PASSWORD_MIN} characters`);
  if (password.length > PASSWORD_MAX) problems.push(`at most ${PASSWORD_MAX} characters`);
  if (!/[A-Z]/.test(password)) problems.push('an upper-case letter');
  if (!/[a-z]/.test(password)) problems.push('a lower-case letter');
  if (!/\d/.test(password)) problems.push('a digit');
  if (!/[^A-Za-z0-9]/.test(password)) problems.push('a symbol');
  const local = email.includes('@') ? email.slice(0, email.indexOf('@')).toLowerCase() : '';
  if (local.length >= 3 && password.toLowerCase().includes(local)) problems.push('must not contain your email name');
  return problems;
}

const profileFields = {
  firstName: name('First name'),
  lastName: name('Last name'),
  phone: optionalPhone,
  preferredLanguage: z.enum(LANGUAGES, { message: 'Choose a supported language' }),
  timezone: z.string().trim().max(50, 'At most 50 characters'),
};

export const createUserSchema = z
  .object({
    email: z.string().trim().min(1, 'Email is required').max(255, 'At most 255 characters').email('Enter a valid email address'),
    password: z.string().min(1, 'Password is required').max(PASSWORD_MAX, `At most ${PASSWORD_MAX} characters`),
    ...profileFields,
  })
  .superRefine((v, ctx) => {
    const problems = passwordProblems(v.password, v.email);
    if (v.password && problems.length) {
      ctx.addIssue({ code: 'custom', path: ['password'], message: `Password needs ${problems.join(', ')}` });
    }
  });

export const updateUserSchema = z.object(profileFields);

/** One row of the role-assignment form; org-scoped roles need an organisation, expiry must be in the future. */
export const roleAssignmentsSchema = (roleScopeById) =>
  z.object({
    assignments: z
      .array(
        z
          .object({
            roleId: z.string().min(1, 'Choose a role'),
            orgScopeId: z.string(),
            expiresAt: z.string().refine((v) => v === '' || new Date(v) > new Date(), 'Expiry must be in the future'),
          })
          .superRefine((a, ctx) => {
            if (roleScopeById[a.roleId] === 'org' && !a.orgScopeId) {
              ctx.addIssue({ code: 'custom', path: ['orgScopeId'], message: 'This role must be limited to an organisation' });
            }
          }),
      )
      .max(20, 'At most 20 roles'),
  });

/** ISO instant → value for <input type="datetime-local"> (local time, minute precision). */
export const toLocalInput = (iso) => {
  if (!iso) return '';
  const d = new Date(iso);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};

/** Form values → API body (empty strings become null; dates become ISO instants). */
export const toUserBody = (values) => ({ ...values, phone: values.phone?.trim() ? values.phone.trim() : null });

export const toAssignmentsBody = (assignments) =>
  assignments.map((a) => ({
    roleId: a.roleId,
    orgScopeId: a.orgScopeId || null,
    expiresAt: a.expiresAt ? new Date(a.expiresAt).toISOString() : null,
  }));

// ------------------------------------------------------------------ WP-14 (mirror TenancyDtos)

export const ORG_TYPE_VALUES = ['group', 'headquarters', 'subsidiary', 'business_unit', 'facility', 'site'];
export const CURRENCIES = ['NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP'];
export const TIERS = ['starter', 'professional', 'enterprise'];
const COUNTRY = /^[A-Z]{2,3}$/;
const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const optionalNumberIn = (min, max, label) => z
  .string()
  .trim()
  .refine((v) => v === '' || (!Number.isNaN(Number(v)) && Number(v) >= min && Number(v) <= max), `${label} must be between ${min} and ${max}`);

export const organisationSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(255, 'At most 255 characters'),
  parentOrgId: z.string(),
  orgType: z.enum(ORG_TYPE_VALUES, { message: 'Choose a type' }),
  countryCode: z.string().trim().refine((v) => v === '' || COUNTRY.test(v), 'Use an ISO country code, e.g. NG'),
  stateRegion: z.string().trim().max(100, 'At most 100 characters'),
  address: z.string().trim().max(500, 'At most 500 characters'),
  latitude: optionalNumberIn(-90, 90, 'Latitude'),
  longitude: optionalNumberIn(-180, 180, 'Longitude'),
});

export const toOrganisationBody = (v) => ({
  name: v.name.trim(),
  parentOrgId: v.parentOrgId || null,
  orgType: v.orgType,
  countryCode: v.countryCode.trim() || null,
  stateRegion: v.stateRegion.trim() || null,
  address: v.address.trim() || null,
  latitude: v.latitude.trim() === '' ? null : Number(v.latitude),
  longitude: v.longitude.trim() === '' ? null : Number(v.longitude),
});

export const periodSchema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(100, 'At most 100 characters'),
    periodType: z.enum(['monthly', 'quarterly', 'annual'], { message: 'Choose a period type' }),
    startDate: z.string().min(1, 'Start date is required'),
    endDate: z.string().min(1, 'End date is required'),
  })
  .refine((v) => !v.startDate || !v.endDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'Must be on or after the start date' });

export const settingsSchema = z.object({
  baseCurrency: z.enum(CURRENCIES, { message: 'Choose a currency' }),
  fiscalYearStartMonth: z.coerce.number().int().min(1, '1-12').max(12, '1-12'),
  reportingTimezone: z.string().trim().min(1, 'Time zone is required').max(50, 'At most 50 characters'),
  defaultFrameworkCodes: z.array(z.string()).max(20, 'At most 20 frameworks'),
  gwpSet: z.enum(['AR5', 'AR6'], { message: 'Choose AR5 or AR6' }),
});

export const setPasswordSchema = (email) => z
  .object({ password: z.string().min(1, 'Password is required').max(PASSWORD_MAX, `At most ${PASSWORD_MAX} characters`) })
  .superRefine((v, ctx) => {
    const problems = passwordProblems(v.password, email);
    if (v.password && problems.length) ctx.addIssue({ code: 'custom', path: ['password'], message: `Password needs ${problems.join(', ')}` });
  });

const tenantCommon = {
  name: z.string().trim().min(1, 'Name is required').max(255, 'At most 255 characters'),
  industrySector: z.string().trim().max(100, 'At most 100 characters'),
  subscriptionTier: z.enum(TIERS, { message: 'Choose a tier' }),
  maxUsers: z.coerce.number().int('Whole number').min(1, 'At least 1').max(100000, 'At most 100000'),
};

export const createTenantSchema = z
  .object({
    ...tenantCommon,
    slug: z.string().trim().min(1, 'Slug is required').max(100, 'At most 100 characters')
      .regex(SLUG, 'Lower-case letters, digits and single hyphens'),
    countryCode: z.string().trim().regex(COUNTRY, 'Use an ISO country code, e.g. NG'),
    billingCurrency: z.enum(CURRENCIES, { message: 'Choose a currency' }),
    adminEmail: z.string().trim().min(1, 'Email is required').max(255).email('Enter a valid email address'),
    adminFirstName: z.string().trim().min(1, 'First name is required').max(100),
    adminLastName: z.string().trim().min(1, 'Last name is required').max(100),
    adminPassword: z.string().min(1, 'Password is required').max(PASSWORD_MAX),
  })
  .superRefine((v, ctx) => {
    const problems = passwordProblems(v.adminPassword, v.adminEmail);
    if (v.adminPassword && problems.length) {
      ctx.addIssue({ code: 'custom', path: ['adminPassword'], message: `Password needs ${problems.join(', ')}` });
    }
  });

export const updateTenantSchema = z.object({ ...tenantCommon, active: z.boolean() });

export const toCreateTenantBody = (v) => ({
  name: v.name.trim(), slug: v.slug.trim(), countryCode: v.countryCode.trim(), industrySector: v.industrySector.trim() || null,
  subscriptionTier: v.subscriptionTier, billingCurrency: v.billingCurrency, maxUsers: Number(v.maxUsers),
  admin: { email: v.adminEmail.trim(), firstName: v.adminFirstName.trim(), lastName: v.adminLastName.trim(), password: v.adminPassword },
});

/** Server field names for the tenant form ("admin.password") → form field names. */
export const TENANT_SERVER_FIELDS = {
  'admin.email': 'adminEmail', 'admin.firstName': 'adminFirstName', 'admin.lastName': 'adminLastName', 'admin.password': 'adminPassword',
};

// ---------------------------------------------------------------- module entitlements (WP-19)
// Mirrors SubscriptionDtos.OverrideRequest: reason @NotBlank @Size(max=500).
export const moduleOverrideSchema = z.object({
  enabled: z.enum(['plan', 'on', 'off']),
  reason: z.string().trim().min(1, 'Required').max(500, 'At most 500 characters'),
});

/** 'plan' clears the override (null), 'on' adds the module, 'off' removes it. */
export const toOverrideBody = (v) => ({ enabled: v.enabled === 'plan' ? null : v.enabled === 'on', reason: v.reason.trim() });
