import { z } from 'zod';

/**
 * Governance form rules. Mirror the server exactly: GovernanceDtos (Bean Validation) + BoardService.check,
 * PolicyService.check and WhistleblowerService.update. Keep them in step.
 */
// no-mock-gate: config — board roles (GovernanceDtos.BOARD_ROLE_REGEX)
export const BOARD_ROLES = {
  chair: 'Chair', vice_chair: 'Vice-chair', md_ceo: 'MD / CEO', executive_director: 'Executive director',
  non_executive_director: 'Non-executive director', independent_non_executive_director: 'Independent non-executive director',
  company_secretary: 'Company secretary',
};
// no-mock-gate: config — board committees (GovernanceDtos.COMMITTEE_REGEX)
export const COMMITTEES = {
  audit: 'Audit', risk: 'Risk', nomination_governance: 'Nomination & governance', remuneration: 'Remuneration',
  sustainability: 'Sustainability', finance: 'Finance', technical: 'Technical',
};
// no-mock-gate: config — option labels
export const GENDERS = { female: 'Female', male: 'Male', undisclosed: 'Prefer not to say' };
export const AGE_BANDS = { under_40: 'Under 40', '40_49': '40–49', '50_59': '50–59', '60_plus': '60 and over' };
// no-mock-gate: config — policy categories (GovernanceDtos.POLICY_CATEGORY_REGEX)
export const POLICY_CATEGORIES = {
  code_of_conduct: 'Code of conduct', anti_bribery_corruption: 'Anti-bribery and corruption', whistleblowing: 'Whistleblowing',
  related_party: 'Related-party transactions', conflict_of_interest: 'Conflict of interest', sustainability: 'Sustainability',
  health_safety: 'Health and safety', human_rights: 'Human rights', data_protection: 'Data protection', board_charter: 'Board charter',
  other: 'Other',
};
// no-mock-gate: config — policy lifecycle labels and action labels (PolicyService)
export const POLICY_STATUS = {
  draft: { label: 'Draft', color: 'default' }, in_review: { label: 'In review', color: 'warning' }, approved: { label: 'Approved', color: 'info' },
  published: { label: 'Published', color: 'success' }, retired: { label: 'Retired', color: 'default' },
};
export const POLICY_ACTIONS = {
  submit: 'Submit for approval', approve: 'Approve', send_back: 'Send back', publish: 'Publish', review: 'Record review',
  retire: 'Retire', revise: 'Revise',
};
// no-mock-gate: config — whistleblower categories and statuses (GovernanceDtos, WhistleblowerService.NEXT)
export const CASE_CATEGORIES = {
  bribery_corruption: 'Bribery or corruption', fraud: 'Fraud', harassment_discrimination: 'Harassment or discrimination',
  health_safety: 'Health and safety', environment: 'Environmental harm', human_rights: 'Human rights', conflict_of_interest: 'Conflict of interest',
  data_privacy: 'Data privacy', other: 'Something else',
};
export const CASE_STATUS = {
  received: { label: 'Received', color: 'info' }, triage: { label: 'Being assessed', color: 'warning' },
  investigating: { label: 'Under investigation', color: 'warning' }, resolved: { label: 'Resolved', color: 'success' },
  closed: { label: 'Closed', color: 'default' }, dismissed: { label: 'Dismissed', color: 'default' },
};
export const CASE_NEXT = {
  received: ['triage', 'dismissed'], triage: ['investigating', 'dismissed'], investigating: ['resolved', 'dismissed'],
  resolved: ['closed', 'investigating'], dismissed: ['closed', 'triage'], closed: [],
};

// ---------------------------------------------------------------- board member (BoardMemberRequest)

export const emptyBoardMember = {
  name: '', boardRole: '', independent: false, executive: false, gender: '', ageBand: '', appointedOn: '', leftOn: '',
  committees: [], skills: '', notes: '',
};

export const toBoardMemberForm = (m) => ({
  name: m.name ?? '', boardRole: m.boardRole ?? '', independent: !!m.independent, executive: !!m.executive, gender: m.gender ?? '',
  ageBand: m.ageBand ?? '', appointedOn: m.appointedOn ?? '', leftOn: m.leftOn ?? '', committees: m.committees ?? [],
  skills: (m.skills ?? []).join(', '), notes: m.notes ?? '',
});

export const boardMemberSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(255, 'At most 255 characters'),
  boardRole: z.enum(Object.keys(BOARD_ROLES), { message: 'Choose a role' }),
  independent: z.boolean(),
  executive: z.boolean(),
  gender: z.string(),
  ageBand: z.string(),
  appointedOn: z.string().min(1, 'Appointment date is required'),
  leftOn: z.string(),
  committees: z.array(z.enum(Object.keys(COMMITTEES))).max(7),
  skills: z.string().max(1000),
  notes: z.string().trim().max(1000, 'At most 1000 characters'),
}).superRefine((v, ctx) => {
  const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
  if (v.leftOn && v.appointedOn && v.leftOn < v.appointedOn) issue('leftOn', 'must be on or after the appointment date');
  if (v.independent && v.executive) issue('independent', "an executive director can't be independent");
  if (v.boardRole === 'independent_non_executive_director' && (!v.independent || v.executive)) {
    issue('boardRole', 'an independent non-executive director is independent and non-executive');
  }
  if (['md_ceo', 'executive_director'].includes(v.boardRole) && !v.executive) issue('executive', 'this role is executive');
  const skills = v.skills.split(',').map((s) => s.trim()).filter(Boolean);
  if (skills.length > 15) issue('skills', 'At most 15 skills');
  if (skills.some((s) => s.length > 60)) issue('skills', 'Each skill at most 60 characters');
});

export const toBoardMemberBody = (v) => ({
  name: v.name.trim(), boardRole: v.boardRole, independent: v.independent, executive: v.executive, gender: v.gender || null,
  ageBand: v.ageBand || null, appointedOn: v.appointedOn, leftOn: v.leftOn || null, committees: v.committees,
  skills: [...new Set(v.skills.split(',').map((s) => s.trim()).filter(Boolean))], notes: v.notes.trim() || null,
});

// ---------------------------------------------------------------- policy (PolicyRequest)

export const emptyPolicy = {
  name: '', category: '', policyVersion: '1.0', summary: '', content: '', ownerUserId: '', fileId: '', reviewMonths: '12',
  effectiveDate: '', requiresAcknowledgement: true,
};

export const toPolicyForm = (p) => ({
  name: p.name ?? '', category: p.category ?? '', policyVersion: p.policyVersion ?? '', summary: p.summary ?? '', content: p.content ?? '',
  ownerUserId: p.ownerUserId ?? '', fileId: p.fileId ?? '', reviewMonths: String(p.reviewMonths ?? 12), effectiveDate: p.effectiveDate ?? '',
  requiresAcknowledgement: p.requiresAcknowledgement ?? true,
});

export const policySchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(255, 'At most 255 characters'),
  category: z.enum(Object.keys(POLICY_CATEGORIES), { message: 'Choose a category' }),
  policyVersion: z.string().trim().min(1, 'Version is required').max(20, 'At most 20 characters')
    .regex(/^[0-9A-Za-z.-]+$/, 'letters, digits, dots and hyphens'),
  summary: z.string().trim().max(1000, 'At most 1000 characters'),
  content: z.string().max(100000, 'At most 100,000 characters'),
  ownerUserId: z.string().min(1, 'Choose the policy owner'),
  fileId: z.string(),
  reviewMonths: z.string().regex(/^\d+$/, 'Whole months').refine((m) => Number(m) >= 1 && Number(m) <= 60, 'Between 1 and 60 months'),
  effectiveDate: z.string(),
  requiresAcknowledgement: z.boolean(),
}).superRefine((v, ctx) => {
  if (!v.content.trim() && !v.fileId) ctx.addIssue({ code: 'custom', path: ['content'], message: 'Add the policy text or attach the policy document' });
});

export const toPolicyBody = (v) => ({
  name: v.name.trim(), category: v.category, policyVersion: v.policyVersion.trim(), summary: v.summary.trim() || null,
  content: v.content.trim() || null, ownerUserId: v.ownerUserId, fileId: v.fileId || null, reviewMonths: Number(v.reviewMonths),
  effectiveDate: v.effectiveDate || null, requiresAcknowledgement: v.requiresAcknowledgement,
});

export const reasonSchema = z.object({ reason: z.string().trim().min(1, 'A reason is required').max(1000, 'At most 1000 characters') });

// ---------------------------------------------------------------- whistleblower

export const submissionSchema = z.object({
  category: z.enum(Object.keys(CASE_CATEGORIES), { message: 'Choose what it is about' }),
  description: z.string().trim().min(20, 'Please describe it in at least 20 characters').max(10000, 'At most 10,000 characters'),
});

export const statusCheckSchema = z.object({
  caseReference: z.string().trim().min(1, 'Enter your case reference').max(30),
  secret: z.string().trim().min(1, 'Enter your secret').max(40),
});

/** CaseUpdate + WhistleblowerService.update rules. */
export const caseUpdateSchema = (current) => z.object({
  status: z.enum(Object.keys(CASE_STATUS)),
  assignedTo: z.string(),
  publicUpdate: z.string().trim().max(1000, 'At most 1000 characters'),
  resolution: z.string().trim().max(10000, 'At most 10,000 characters'),
}).superRefine((v, ctx) => {
  if (v.status !== current && !CASE_NEXT[current].includes(v.status)) {
    ctx.addIssue({ code: 'custom', path: ['status'], message: `A ${current} case can't move to ${v.status}` });
  }
  if (['resolved', 'dismissed'].includes(v.status) && !v.resolution) {
    ctx.addIssue({ code: 'custom', path: ['resolution'], message: 'Record the outcome before resolving or dismissing the case' });
  }
});

export const noteSchema = z.object({ note: z.string().trim().min(1, 'Write the note').max(10000, 'At most 10,000 characters') });
