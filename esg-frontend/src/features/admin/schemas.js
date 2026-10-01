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
