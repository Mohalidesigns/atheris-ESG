import { z } from 'zod';

/** Mirrors LoginRequest (@NotBlank @Email email, @NotBlank password). */
export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

/** Mirrors MfaRequests.Verify.code (@Pattern \d{6}). */
export const mfaCodeSchema = z.object({
  code: z.string().trim().regex(/^\d{6}$/, 'Enter the 6-digit code'),
});
