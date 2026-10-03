import { z } from 'zod';

/**
 * Training form rules. Mirror the server exactly: TrainingDtos (Bean Validation) + CourseService.check / checkPath and
 * EnrollmentService.assign / recordAttendance. Keep them in step.
 */
// no-mock-gate: config — option labels (TrainingDtos regexes, V034 checks)
export const CATEGORIES = {
  esg_fundamentals: 'ESG fundamentals', climate_carbon: 'Climate and carbon', reporting_disclosure: 'Reporting and disclosure',
  social_human_rights: 'Social and human rights', governance_ethics: 'Governance and ethics', data_collection: 'Data collection',
  regulatory: 'Regulatory', other: 'Other',
};
export const DELIVERY = { online: 'Online (modules and quiz)', classroom: 'Classroom (attendance recorded)' };
export const MODULE_KINDS = { text: 'Text', pdf: 'PDF', video: 'Video link' };
export const PATH_STATUS = { active: 'Active', archived: 'Archived' };
/** The organisation's working roles a learning path can target (CourseService.checkPath: system roles except SUPER_ADMIN and INVESTOR_VIEWER). */
export const PATH_ROLES = ['TENANT_ADMIN', 'ESG_DIRECTOR', 'ESG_MANAGER', 'ESG_ANALYST', 'DATA_COLLECTOR', 'AUDITOR', 'CONSULTANT', 'BOARD_MEMBER',
  'COMPLIANCE_OFFICER', 'TRAINING_ADMIN', 'ETHICS_OFFICER'];
export const COURSE_STATUS = { draft: { label: 'Draft', color: 'default' }, published: { label: 'Published', color: 'success' },
  retired: { label: 'Retired', color: 'warning' } };
export const ENROLMENT_STATUS = { enrolled: { label: 'Not started', color: 'default' }, in_progress: { label: 'In progress', color: 'info' },
  passed: { label: 'Passed', color: 'success' }, failed: { label: 'Not passed yet', color: 'warning' }, withdrawn: { label: 'Withdrawn', color: 'default' } };
export const CERT_STATUS = { valid: { label: 'Valid', color: 'success' }, expired: { label: 'Expired', color: 'warning' }, revoked: { label: 'Revoked', color: 'error' } };
export const GAP_STATE = { not_enrolled: 'Not enrolled', enrolled: 'Not started', in_progress: 'In progress', failed: 'Not passed yet', withdrawn: 'Withdrawn',
  expired: 'Certificate expired', revoked: 'Certificate revoked' };

const blank = (v) => (v == null ? '' : String(v));
const nul = (v) => (v === '' || v == null ? null : v);
const whole = /^\d+$/;
const oneDecimal = /^\d{1,3}(\.\d)?$/;

// ---------------------------------------------------------------- course (CourseRequest)

export const emptyModule = { title: '', kind: 'text', body: '', fileId: '', url: '', minutes: '' };
export const emptyQuestion = { question: '', options: '', correctIndex: '0' };
export const emptyCourse = { code: '', title: '', description: '', category: 'esg_fundamentals', delivery: 'online', durationHours: '', passingScore: '70',
  certificateValidityMonths: '', prerequisiteIds: [], modules: [], questions: [] };

export const toCourseForm = (c) => ({
  code: blank(c.code), title: blank(c.title), description: blank(c.description), category: c.category ?? 'esg_fundamentals', delivery: c.delivery ?? 'online',
  durationHours: blank(c.durationHours), passingScore: blank(c.passingScore), certificateValidityMonths: blank(c.certificateValidityMonths),
  prerequisiteIds: c.prerequisiteIds ?? [],
  modules: (c.modules ?? []).map((m) => ({ title: blank(m.title), kind: m.kind, body: blank(m.body), fileId: blank(m.fileId), url: blank(m.url),
    minutes: blank(m.minutes), fileName: m.fileName ?? '' })),
  questions: (c.questions ?? []).map((q) => ({ question: blank(q.question), options: (q.options ?? []).join('\n'), correctIndex: blank(q.correctIndex ?? 0) })),
});

/** Options are typed one per line; blank lines are ignored. */
export const optionList = (text) => String(text ?? '').split('\n').map((o) => o.trim()).filter(Boolean);

const moduleSchema = z.object({
  title: z.string().trim().min(1, 'Give the module a title').max(200, 'At most 200 characters'),
  kind: z.enum(Object.keys(MODULE_KINDS)),
  body: z.string().max(20000, 'At most 20,000 characters'),
  fileId: z.string(),
  url: z.string().trim().max(500, 'At most 500 characters').refine((v) => v === '' || /^https:\/\/.+/.test(v), 'must start with https://'),
  minutes: z.string().trim().regex(whole, 'Whole minutes').refine((v) => Number(v) >= 1 && Number(v) <= 600, 'Between 1 and 600 minutes'),
}).passthrough().superRefine((m, ctx) => {
  if (m.kind === 'text' && m.body.trim() === '') ctx.addIssue({ code: 'custom', path: ['body'], message: 'write the text' });
  if (m.kind === 'pdf' && !m.fileId) ctx.addIssue({ code: 'custom', path: ['fileId'], message: 'attach the PDF' });
  if (m.kind === 'video' && m.url.trim() === '') ctx.addIssue({ code: 'custom', path: ['url'], message: 'give the video link' });
});

const questionSchema = z.object({
  question: z.string().trim().min(1, 'Write the question').max(1000, 'At most 1000 characters'),
  options: z.string(),
  correctIndex: z.string(),
}).superRefine((q, ctx) => {
  const opts = optionList(q.options);
  if (opts.length < 2 || opts.length > 6) ctx.addIssue({ code: 'custom', path: ['options'], message: 'give 2 to 6 options' });
  if (opts.some((o) => o.length > 300)) ctx.addIssue({ code: 'custom', path: ['options'], message: 'each option at most 300 characters' });
  if (!whole.test(q.correctIndex) || Number(q.correctIndex) >= opts.length) ctx.addIssue({ code: 'custom', path: ['correctIndex'], message: 'must be one of the options' });
});

export const courseSchema = z.object({
  code: z.string().trim().min(1, 'Code is required').max(30, 'At most 30 characters').regex(/^[A-Z0-9][A-Z0-9-]*$/, 'capital letters, digits and hyphens'),
  title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
  description: z.string().trim().max(2000, 'At most 2000 characters'),
  category: z.enum(Object.keys(CATEGORIES)),
  delivery: z.enum(Object.keys(DELIVERY)),
  durationHours: z.string().trim().regex(oneDecimal, 'Hours, to one decimal place').refine((v) => Number(v) >= 0.1 && Number(v) <= 500, 'Between 0.1 and 500 hours'),
  passingScore: z.string().trim().regex(whole, 'A whole percentage').refine((v) => Number(v) <= 100, 'Between 0 and 100'),
  certificateValidityMonths: z.string().trim().refine((v) => v === '' || (whole.test(v) && Number(v) >= 1 && Number(v) <= 120), 'Between 1 and 120 months, or blank'),
  prerequisiteIds: z.array(z.string()).max(10, 'At most 10 prerequisites'),
  modules: z.array(moduleSchema).max(50, 'At most 50 modules'),
  questions: z.array(questionSchema).max(50, 'At most 50 questions'),
});

export const toCourseBody = (v) => ({
  code: v.code.trim(), title: v.title.trim(), description: nul(v.description.trim()), category: v.category, delivery: v.delivery,
  durationHours: Number(v.durationHours), passingScore: Number(v.passingScore), certificateValidityMonths: v.certificateValidityMonths === '' ? null : Number(v.certificateValidityMonths),
  prerequisiteIds: v.prerequisiteIds,
  modules: v.modules.map((m) => ({ title: m.title.trim(), kind: m.kind, body: m.kind === 'text' ? m.body : null, fileId: m.kind === 'pdf' ? nul(m.fileId) : null,
    url: m.kind === 'video' ? m.url.trim() : null, minutes: Number(m.minutes) })),
  questions: v.questions.map((q) => ({ question: q.question.trim(), options: optionList(q.options), correctIndex: Number(q.correctIndex) })),
});

// ---------------------------------------------------------------- learning path (PathRequest)

export const emptyPath = { name: '', description: '', targetRole: '', courseIds: [], status: 'active' };
export const toPathForm = (p) => ({ name: blank(p.name), description: blank(p.description), targetRole: blank(p.targetRole), courseIds: p.courseIds ?? [],
  status: p.status ?? 'active' });
export const pathSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
  description: z.string().trim().max(2000, 'At most 2000 characters'),
  targetRole: z.string().refine((v) => v === '' || PATH_ROLES.includes(v), "must be one of the organisation's roles"),
  courseIds: z.array(z.string()).min(1, 'add at least one course').max(50, 'At most 50 courses'),
  status: z.enum(Object.keys(PATH_STATUS)),
});
export const toPathBody = (v) => ({ name: v.name.trim(), description: nul(v.description.trim()), targetRole: nul(v.targetRole), courseIds: v.courseIds,
  status: v.status });

// ---------------------------------------------------------------- assigning (AssignRequest / AssignPathRequest)

export const assignSchema = (today) => z.object({
  userIds: z.array(z.string()).min(1, 'choose at least one person').max(500, 'At most 500 people'),
  dueOn: z.string().refine((d) => d === '' || d >= today, "can't be in the past"),
});

/** A path with a role may be assigned to everyone holding the role (no one chosen); otherwise people are required. */
export const assignPathSchema = (today, hasRole) => z.object({
  userIds: z.array(z.string()).max(500, 'At most 500 people').refine((v) => hasRole || v.length > 0, 'choose at least one person'),
  dueOn: z.string().refine((d) => d === '' || d >= today, "can't be in the past"),
});

// ---------------------------------------------------------------- classroom attendance (AttendanceRequest)

export const attendanceSchema = (today) => z.object({
  completedOn: z.string().min(1, 'Date is required').refine((d) => !d || d <= today, "can't be in the future"),
  hours: z.string().trim().regex(oneDecimal, 'Hours, to one decimal place').refine((v) => Number(v) >= 0.1 && Number(v) <= 500, 'Between 0.1 and 500 hours'),
  passed: z.boolean(),
  score: z.string().trim().refine((v) => v === '' || (whole.test(v) && Number(v) <= 100), 'must be 0 to 100'),
});

export const revokeSchema = z.object({ note: z.string().trim().min(1, 'say why the certificate is revoked').max(1000, 'At most 1000 characters') });

export const lagosToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' });
export const fmt = (v, digits = 1) => (v == null ? '—' : Number(v).toLocaleString(undefined, { maximumFractionDigits: digits }));
export const problemText = (err) => err?.data?.detail ?? Object.values(err?.data?.errors ?? {})[0] ?? 'Something went wrong';
export const saveBlob = (blobData, name) => {
  const href = URL.createObjectURL(blobData);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  a.rel = 'noopener';
  a.click();
  setTimeout(() => URL.revokeObjectURL(href), 10_000);
};
