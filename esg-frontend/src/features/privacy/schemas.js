import { z } from 'zod';

/** NDPA form rules. Mirror the server exactly: PrivacyDtos (Bean Validation) + PrivacyService checks. */
// no-mock-gate: config — option labels (PrivacyDtos.KIND_REGEX, V037 checks)
export const KINDS = { access: 'Access (a copy of their data)', rectification: 'Rectification (correct data)', erasure: 'Erasure (delete data)',
  objection: 'Objection (stop a use of data)', portability: 'Portability (data in a reusable form)' };
export const REQUEST_STATUS = { received: { label: 'Received', color: 'warning' }, in_progress: { label: 'In progress', color: 'info' },
  completed: { label: 'Completed', color: 'success' }, refused: { label: 'Refused', color: 'default' } };

export const emptyNotice = { title: '', body: '', dpoContact: '' };
export const noticeSchema = z.object({
  title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
  body: z.string().trim().min(1, 'Write the notice').max(20000, 'At most 20,000 characters'),
  dpoContact: z.string().trim().min(1, 'Say how to reach the data protection officer').max(300, 'At most 300 characters'),
});

export const emptyRequest = { kind: 'access', subjectUserId: '', subjectName: '', subjectEmail: '', details: '', receivedOn: '' };
export const requestSchema = (today) => z.object({
  kind: z.enum(Object.keys(KINDS)),
  subjectUserId: z.string(),
  subjectName: z.string().trim().min(1, 'Name the person').max(200, 'At most 200 characters'),
  subjectEmail: z.string().trim().max(255, 'At most 255 characters').refine((v) => v === '' || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), 'must be an email address'),
  details: z.string().trim().max(4000, 'At most 4000 characters'),
  receivedOn: z.string().min(1, 'Date is required').refine((d) => !d || d <= today, "can't be in the future"),
}).refine((v) => v.kind !== 'erasure' || v.subjectUserId, { path: ['subjectUserId'], message: 'choose the user whose data is to be erased' });
export const toRequestBody = (v) => ({ kind: v.kind, subjectUserId: v.subjectUserId || null, subjectName: v.subjectName.trim(),
  subjectEmail: v.subjectEmail.trim() || null, details: v.details.trim() || null, receivedOn: v.receivedOn });

const days = (min, max) => z.string().trim().regex(/^\d+$/, 'Whole days').refine((v) => Number(v) >= min && Number(v) <= max, `Between ${min} and ${max} days`);
export const retentionSchema = z.object({
  notificationsDays: days(30, 3650), deliveryLogDays: days(7, 3650), sessionsDays: days(1, 365), accessLogIpDays: days(7, 3650),
});

export const lagosToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' });
export { problemText, saveBlob } from '../../services/download';
