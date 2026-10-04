import { z } from 'zod';

/**
 * Compliance forms. Each mirrors its server DTO + service rules exactly (ComplianceDtos.DeadlineRequest, SubmitRequest,
 * WaiveRequest, ChangeRequest, AssessRequest, AttestationRequest; DeadlineService, RegulatoryChangeService,
 * ComplianceService).
 */

// no-mock-gate: config — the TRD default reminder intervals (TRD §4.5 filing_deadlines.reminder_days)
export const DEFAULT_REMINDERS = [90, 60, 30, 14, 7, 1];
// no-mock-gate: config — labels for GapAnalysisEngine statuses
export const GAP_STATUS = {
  met: { label: 'Met', color: 'success' }, partial: { label: 'Partial', color: 'warning' },
  missing: { label: 'Missing', color: 'error' }, not_applicable: { label: 'Not applicable', color: 'default' },
};
// no-mock-gate: config — how a requirement's status was decided (GapAnalysisEngine basis)
export const BASIS = {
  data: 'from approved data', attested: 'attested', mapped: 'via mapped requirement', supporting_data: 'supporting data approved',
  none: 'nothing recorded', children: 'from its requirements',
};
// no-mock-gate: config — labels for attestation statuses (tenant_compliance_status.status)
export const ATTEST_STATUS = {
  not_started: 'Not started', in_progress: 'In progress', complete: 'Complete', not_applicable: 'Not applicable',
};
// no-mock-gate: config — labels for filing deadline statuses
export const DEADLINE_STATUS = {
  upcoming: { label: 'Upcoming', color: 'info' }, submitted: { label: 'Submitted', color: 'success' },
  overdue: { label: 'Overdue', color: 'error' }, waived: { label: 'Waived', color: 'default' },
};
// no-mock-gate: config — labels for regulatory change statuses and impact levels
export const CHANGE_STATUS = {
  new: { label: 'New', color: 'info' }, assessing: { label: 'Assessing', color: 'warning' },
  assessed: { label: 'Assessed', color: 'primary' }, closed: { label: 'Closed', color: 'default' },
};
// no-mock-gate: config — impact levels (AssessRequest pattern)
export const IMPACT = { high: 'High', medium: 'Medium', low: 'Low', none: 'None' };
export const MAX_EVIDENCE = 20;

const today = () => new Date().toISOString().slice(0, 10);
const yearText = z.string().trim()
  .refine((v) => v === '' || (/^\d{4}$/.test(v) && Number(v) >= 2000 && Number(v) <= 2100), 'A year from 2000 to 2100');

// ---------------------------------------------------------------- filing deadline (DeadlineRequest)

export const emptyDeadline = {
  framework: '', description: '', deadlineDate: '', reportingYear: '', ownerUserId: '', reminderDays: DEFAULT_REMINDERS, notes: '',
};

export const deadlineSchema = z.object({
  framework: z.string(),
  description: z.string().trim().min(1, 'Description is required').max(500, 'At most 500 characters'),
  deadlineDate: z.string().min(1, 'Due date is required'),
  reportingYear: yearText,
  ownerUserId: z.string().min(1, 'Choose who owns this filing'),
  reminderDays: z.array(z.number().int().min(1, '1 to 365 days').max(365, '1 to 365 days')).max(10, 'At most 10 reminders')
    .refine((v) => new Set(v).size === v.length, 'Each reminder day once'),
  notes: z.string().trim().max(1000, 'At most 1000 characters'),
});

export const toDeadlineBody = (v) => ({
  framework: v.framework || null, description: v.description.trim(), deadlineDate: v.deadlineDate,
  reportingYear: v.reportingYear === '' ? null : Number(v.reportingYear), ownerUserId: v.ownerUserId,
  reminderDays: [...v.reminderDays].sort((a, b) => b - a), notes: v.notes.trim() || null,
});

export const toDeadlineForm = (d) => ({
  framework: d.framework ?? '', description: d.description, deadlineDate: d.deadlineDate,
  reportingYear: d.reportingYear == null ? '' : String(d.reportingYear), ownerUserId: d.ownerUserId ?? '',
  reminderDays: d.reminderDays ?? [], notes: d.notes ?? '',
});

// ---------------------------------------------------------------- submission / waiver

export const emptySubmission = { submittedOn: '', submissionRef: '', evidenceFileIds: [] };

export const submissionSchema = z.object({
  submittedOn: z.string().min(1, 'Date submitted is required').refine((v) => !v || v <= today(), "Can't be in the future"),
  submissionRef: z.string().trim().min(1, 'Reference is required').max(100, 'At most 100 characters'),
  evidenceFileIds: z.array(z.string()).max(MAX_EVIDENCE, `At most ${MAX_EVIDENCE} files`),
});

export const waiverSchema = z.object({ reason: z.string().trim().min(1, 'Say why it is waived').max(500, 'At most 500 characters') });

// ---------------------------------------------------------------- regulatory change (ChangeRequest)

export const emptyChange = {
  framework: '', regulator: '', title: '', summary: '', sourceUrl: '', publishedDate: '', effectiveDate: '', assessmentDue: '', ownerUserId: '',
};

export const changeSchema = z.object({
  framework: z.string(),
  regulator: z.string().trim().min(1, 'Regulator is required').max(100, 'At most 100 characters'),
  title: z.string().trim().min(1, 'Title is required').max(255, 'At most 255 characters'),
  summary: z.string().trim().max(4000, 'At most 4000 characters'),
  sourceUrl: z.string().trim().max(500, 'At most 500 characters').refine((v) => v === '' || /^https?:\/\/\S+$/.test(v), 'Must be an http(s) link'),
  publishedDate: z.string().min(1, 'Published date is required'),
  effectiveDate: z.string(),
  assessmentDue: z.string(),
  ownerUserId: z.string().min(1, 'Choose who assesses it'),
}).superRefine((v, ctx) => {
  if (v.publishedDate && v.effectiveDate && v.effectiveDate < v.publishedDate) {
    ctx.addIssue({ code: 'custom', path: ['effectiveDate'], message: 'Must be on or after the published date' });
  }
  if (v.publishedDate && v.assessmentDue && v.assessmentDue < v.publishedDate) {
    ctx.addIssue({ code: 'custom', path: ['assessmentDue'], message: 'Must be on or after the published date' });
  }
});

export const toChangeBody = (v) => ({
  framework: v.framework || null, regulator: v.regulator.trim(), title: v.title.trim(), summary: v.summary.trim() || null,
  sourceUrl: v.sourceUrl.trim() || null, publishedDate: v.publishedDate, effectiveDate: v.effectiveDate || null,
  assessmentDue: v.assessmentDue || null, ownerUserId: v.ownerUserId,
});

export const toChangeForm = (c) => ({
  framework: c.framework ?? '', regulator: c.regulator, title: c.title, summary: c.summary ?? '', sourceUrl: c.sourceUrl ?? '',
  publishedDate: c.publishedDate, effectiveDate: c.effectiveDate ?? '', assessmentDue: c.assessmentDue ?? '', ownerUserId: c.ownerUserId ?? '',
});

export const assessSchema = z.object({
  impactLevel: z.enum(Object.keys(IMPACT), { message: 'Choose the impact' }),
  impactAssessment: z.string().trim().min(1, 'Describe the impact').max(4000, 'At most 4000 characters'),
  requiredActions: z.string().trim().max(4000, 'At most 4000 characters'),
});

// ---------------------------------------------------------------- attestation (AttestationRequest)

/** `quantitative`: met by data; can be marked not applicable, or complete once some of its data is approved. */
export const attestationSchema = (quantitative) => z.object({
  status: z.enum(Object.keys(ATTEST_STATUS), { message: 'Choose a status' }),
  notes: z.string().trim().max(2000, 'At most 2000 characters'),
  evidenceFileIds: z.array(z.string()).max(MAX_EVIDENCE, `At most ${MAX_EVIDENCE} files`),
}).superRefine((v, ctx) => {
  if (v.status === 'complete' && v.evidenceFileIds.length === 0) {
    ctx.addIssue({ code: 'custom', path: ['evidenceFileIds'], message: 'Attach the evidence for a complete requirement' });
  }
  if (v.status === 'not_applicable' && !v.notes.trim()) {
    ctx.addIssue({ code: 'custom', path: ['notes'], message: "Say why it doesn't apply" });
  }
  if (quantitative && v.status === 'in_progress') {
    ctx.addIssue({ code: 'custom', path: ['status'], message: 'Progress on this requirement comes from its data in the Data Hub' });
  }
});

export const toAttestationBody = (v, { framework, requirementCode, year, version }) => ({
  framework, requirementCode, year, status: v.status, notes: v.notes.trim() || null, evidenceFileIds: v.evidenceFileIds,
  version: version ?? null,
});
