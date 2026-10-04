import { z } from 'zod';
import { formattingLocale } from '../../i18n';

/**
 * Supply chain form rules. Mirror the server exactly: SupplyDtos (Bean Validation) + SupplierService.validate,
 * TemplateService.check, CorrectiveActionService.check and SpendService.check. Keep them in step.
 */
// no-mock-gate: config — option labels (SupplyDtos regexes)
export const TIERS = { tier_1: 'Tier 1', tier_2: 'Tier 2', tier_3: 'Tier 3' };
export const ONBOARDING = { full: 'Full', simplified: 'Simplified (SME / informal)' };
export const SUPPLIER_STATUS = {
  pending: { label: 'Pending', color: 'default' }, active: { label: 'Active', color: 'success' }, suspended: { label: 'Suspended', color: 'warning' },
  offboarded: { label: 'Offboarded', color: 'default' },
};
export const RISK = {
  low: { label: 'Low risk', color: 'success' }, medium: { label: 'Medium risk', color: 'warning' }, high: { label: 'High risk', color: 'error' },
};
export const PILLARS = { E: 'Environmental', S: 'Social', G: 'Governance' };
export const ANSWER_TYPES = { yes_no: 'Yes / no', scale: 'Scale 1–5', number: 'Number', text: 'Text' };
export const TEMPLATE_STATUS = {
  draft: { label: 'Draft', color: 'default' }, published: { label: 'Published', color: 'success' }, retired: { label: 'Retired', color: 'default' },
};
export const Q_STATUS = {
  sent: { label: 'Sent', color: 'info' }, in_progress: { label: 'Being answered', color: 'warning' }, submitted: { label: 'Awaiting review', color: 'warning' },
  reviewed: { label: 'Reviewed', color: 'success' }, cancelled: { label: 'Cancelled', color: 'default' },
};
export const Q_ACTIONS = { review: 'Accept scorecard', return: 'Return to supplier', cancel: 'Cancel', resend: 'New link' };
export const SEVERITY = { low: 'Low', medium: 'Medium', high: 'High' };
export const ACTION_STATUS = {
  raised: { label: 'Raised', color: 'default' }, assigned: { label: 'Assigned', color: 'info' }, implemented: { label: 'Implemented', color: 'warning' },
  verified: { label: 'Verified', color: 'success' }, closed: { label: 'Closed', color: 'success' },
};
export const ACTION_STEPS = { assign: 'Assign', implement: 'Record the fix', verify: 'Verify', reject: 'Reject fix', close: 'Close' };
export const CURRENCIES = ['NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP'];

const blank = (v) => (v == null ? '' : String(v));
const nul = (v) => (v === '' || v == null ? null : v);

// ---------------------------------------------------------------- supplier (SupplierRequest)

export const emptySupplier = {
  name: '', tier: 'tier_1', onboarding: 'simplified', sectorCode: '', countryCode: 'NG', stateRegion: '', registrationNumber: '', contactName: '',
  contactEmail: '', contactPhone: '', description: '', status: 'pending', ownerUserId: '',
};

export const toSupplierForm = (s) => Object.fromEntries(Object.keys(emptySupplier).map((k) => [k, blank(s[k])]));

export const supplierSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(255, 'At most 255 characters'),
  tier: z.enum(Object.keys(TIERS)),
  onboarding: z.enum(Object.keys(ONBOARDING)),
  sectorCode: z.string(),
  countryCode: z.string().regex(/^[A-Z]{2}$/, 'Choose the country'),
  stateRegion: z.string().trim().max(100, 'At most 100 characters'),
  registrationNumber: z.string().trim().max(50, 'At most 50 characters'),
  contactName: z.string().trim().max(200, 'At most 200 characters'),
  contactEmail: z.string().trim().max(255, 'At most 255 characters').refine((v) => v === '' || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), 'Must be an email address'),
  contactPhone: z.string().trim().refine((v) => v === '' || /^\+?[0-9 ()-]{7,20}$/.test(v), 'Must be a phone number'),
  description: z.string().trim().max(2000, 'At most 2000 characters'),
  status: z.enum(Object.keys(SUPPLIER_STATUS)),
  ownerUserId: z.string(),
}).superRefine((v, ctx) => {
  if (v.onboarding === 'full' && !v.contactEmail.trim()) {
    ctx.addIssue({ code: 'custom', path: ['contactEmail'], message: 'is required for full onboarding (the questionnaire link is emailed)' });
  }
});

export const toSupplierBody = (v) => ({
  name: v.name.trim(), tier: v.tier, onboarding: v.onboarding, sectorCode: nul(v.sectorCode), countryCode: v.countryCode, stateRegion: nul(v.stateRegion.trim()),
  registrationNumber: nul(v.registrationNumber.trim()), contactName: nul(v.contactName.trim()), contactEmail: nul(v.contactEmail.trim()),
  contactPhone: nul(v.contactPhone.trim()), description: nul(v.description.trim()), status: v.status, ownerUserId: nul(v.ownerUserId),
});

// ---------------------------------------------------------------- template (TemplateRequest)

export const emptyQuestion = { pillar: 'E', question: '', helpText: '', answerType: 'yes_no', positiveAnswer: 'yes', weight: '1', evidenceRequired: false };
export const emptyTemplate = { name: '', kind: 'simplified', description: '', weightE: '40', weightS: '30', weightG: '30', questions: [{ ...emptyQuestion }] };

export const toTemplateForm = (t) => ({
  name: t.name ?? '', kind: t.kind ?? 'full', description: t.description ?? '', weightE: blank(t.weightE), weightS: blank(t.weightS), weightG: blank(t.weightG),
  questions: (t.questions ?? []).map((q) => ({ pillar: q.pillar, question: q.question, helpText: q.helpText ?? '', answerType: q.answerType,
    positiveAnswer: q.positiveAnswer ?? '', weight: blank(q.weight), evidenceRequired: !!q.evidenceRequired })),
});

const int = (max, label) => z.string().refine((v) => /^\d+$/.test(v) && Number(v) <= max, `${label}: a whole number from 0 to ${max}`);

export const templateSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
  kind: z.enum(Object.keys(ONBOARDING)),
  description: z.string().trim().max(2000, 'At most 2000 characters'),
  weightE: int(100, 'E weight'),
  weightS: int(100, 'S weight'),
  weightG: int(100, 'G weight'),
  questions: z.array(z.object({
    pillar: z.enum(Object.keys(PILLARS)),
    question: z.string().trim().min(1, 'Required').max(500, 'At most 500 characters'),
    helpText: z.string().trim().max(1000, 'At most 1000 characters'),
    answerType: z.enum(Object.keys(ANSWER_TYPES)),
    positiveAnswer: z.string(),
    weight: int(10, 'Weight'),
    evidenceRequired: z.boolean(),
  })).min(1, 'Add at least one question').max(60, 'At most 60 questions'),
}).superRefine((v, ctx) => {
  if (Number(v.weightE) + Number(v.weightS) + Number(v.weightG) !== 100) {
    ctx.addIssue({ code: 'custom', path: ['weightE'], message: 'the E, S and G weights must add up to 100' });
  }
  v.questions.forEach((q, i) => {
    const issue = (f, m) => ctx.addIssue({ code: 'custom', path: ['questions', i, f], message: m });
    if (q.answerType === 'yes_no' && !q.positiveAnswer) issue('positiveAnswer', 'say which answer is the good one');
    if (q.answerType !== 'yes_no' && q.positiveAnswer) issue('positiveAnswer', 'only yes/no questions have a good answer');
    if (['number', 'text'].includes(q.answerType) && Number(q.weight) > 0) issue('weight', "number and text answers aren't scored; use weight 0");
  });
});

export const toTemplateBody = (v) => ({
  name: v.name.trim(), kind: v.kind, description: nul(v.description.trim()), weightE: Number(v.weightE), weightS: Number(v.weightS), weightG: Number(v.weightG),
  questions: v.questions.map((q) => ({ pillar: q.pillar, question: q.question.trim(), helpText: nul(q.helpText.trim()), answerType: q.answerType,
    positiveAnswer: q.answerType === 'yes_no' ? q.positiveAnswer : null, weight: Number(q.weight), evidenceRequired: q.evidenceRequired })),
});

// ---------------------------------------------------------------- corrective action, spend, send

export const emptyAction = { supplierId: '', questionnaireId: '', questionId: '', title: '', description: '', severity: 'medium', ownerUserId: '', dueDate: '' };
export const toActionForm = (a) => Object.fromEntries(Object.keys(emptyAction).map((k) => [k, blank(a[k])]));
export const actionSchema = z.object({
  supplierId: z.string().min(1, 'Choose the supplier'),
  questionnaireId: z.string(),
  questionId: z.string(),
  title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
  description: z.string().trim().max(4000, 'At most 4000 characters'),
  severity: z.enum(Object.keys(SEVERITY)),
  ownerUserId: z.string(),
  dueDate: z.string().min(1, 'Due date is required'),
}).superRefine((v, ctx) => {
  if (v.questionId && !v.questionnaireId) ctx.addIssue({ code: 'custom', path: ['questionId'], message: 'must be a question of the questionnaire' });
});
export const toActionBody = (v) => ({
  supplierId: v.supplierId, questionnaireId: nul(v.questionnaireId), questionId: nul(v.questionId), title: v.title.trim(), description: nul(v.description.trim()),
  severity: v.severity, ownerUserId: nul(v.ownerUserId), dueDate: v.dueDate,
});

export const emptySpend = { supplierId: '', orgId: '', reportingPeriodStart: '', reportingPeriodEnd: '', amount: '', currency: 'NGN', sourceActivity: '', description: '' };
export const toSpendForm = (s) => Object.fromEntries(Object.keys(emptySpend).map((k) => [k, blank(s[k])]));
export const spendSchema = (today) => z.object({
  supplierId: z.string().min(1, 'Choose the supplier'),
  orgId: z.string().min(1, 'Choose the organisation'),
  reportingPeriodStart: z.string().min(1, 'Start date is required'),
  reportingPeriodEnd: z.string().min(1, 'End date is required'),
  amount: z.string().trim().refine((v) => /^\d{1,14}(\.\d{1,2})?$/.test(v) && Number(v) > 0, 'An amount above 0, up to 2 decimals'),
  currency: z.enum(CURRENCIES),
  sourceActivity: z.string().min(1, 'Choose what was bought'),
  description: z.string().trim().max(500, 'At most 500 characters'),
}).superRefine((v, ctx) => {
  if (v.reportingPeriodStart && v.reportingPeriodEnd && v.reportingPeriodEnd < v.reportingPeriodStart) {
    ctx.addIssue({ code: 'custom', path: ['reportingPeriodEnd'], message: 'must be on or after the start date' });
  } else if (v.reportingPeriodEnd > today) {
    ctx.addIssue({ code: 'custom', path: ['reportingPeriodEnd'], message: 'spend is recorded for periods that have ended' });
  }
});
export const toSpendBody = (v) => ({
  supplierId: v.supplierId, orgId: v.orgId, reportingPeriodStart: v.reportingPeriodStart, reportingPeriodEnd: v.reportingPeriodEnd, amount: Number(v.amount),
  currency: v.currency, sourceActivity: v.sourceActivity, description: nul(v.description.trim()),
});

export const sendSchema = (today) => z.object({
  supplierId: z.string().min(1, 'Choose the supplier'),
  templateId: z.string().min(1, 'Choose a published template'),
  dueDate: z.string().min(1, 'Due date is required').refine((d) => !d || d >= today, "can't be in the past"),
  title: z.string().trim().max(255, 'At most 255 characters'),
});

export const lagosToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' });
export const fmt = (v, digits = 1) => (v == null ? '—' : Number(v).toLocaleString(formattingLocale(), { maximumFractionDigits: digits }));
export const problemText = (err) => err?.data?.detail ?? Object.values(err?.data?.errors ?? {})[0] ?? 'Something went wrong';
