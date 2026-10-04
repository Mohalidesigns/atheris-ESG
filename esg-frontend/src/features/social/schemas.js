import { z } from 'zod';
import { formattingLocale } from '../../i18n';

/**
 * Social Impact form rules. Mirror the server exactly: SocialDtos (Bean Validation) + WorkforceService.check,
 * SafetyService.check, CommunityService.check and GrievanceService.check. Keep them in step.
 */
// no-mock-gate: config — option labels (SocialDtos regexes)
export const LEVELS = { executive: 'Executive', senior_management: 'Senior management', middle_management: 'Middle management', non_management: 'Non-management' };
export const GENDERS = { female: 'Female', male: 'Male', undisclosed: 'Undisclosed' };
export const AGE_BANDS = { under_30: 'Under 30', '30_50': '30–50', over_50: 'Over 50' };
export const CURRENCIES = ['NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP'];
export const INCIDENT_TYPES = {
  injury: 'Injury', illness: 'Illness', near_miss: 'Near miss', dangerous_occurrence: 'Dangerous occurrence', property_damage: 'Property damage',
  environmental: 'Environmental',
};
export const OUTCOMES = {
  none: 'No injury', first_aid: 'First aid', medical_treatment: 'Medical treatment', restricted_work: 'Restricted work', lost_time: 'Lost time',
  fatality: 'Fatality',
};
export const PERSON_TYPES = { employee: 'Employee', contractor: 'Contractor', visitor: 'Visitor', community_member: 'Community member' };
export const PROJECT_CATEGORIES = {
  education: 'Education', health: 'Health', water_sanitation: 'Water and sanitation', infrastructure: 'Infrastructure', livelihoods: 'Livelihoods',
  environment: 'Environment', electrification: 'Electrification', scholarships: 'Scholarships', other: 'Other',
};
export const PROJECT_STATUS = {
  planning: { label: 'Planning', color: 'default' }, active: { label: 'Active', color: 'info' }, on_hold: { label: 'On hold', color: 'warning' },
  completed: { label: 'Completed', color: 'success' }, cancelled: { label: 'Cancelled', color: 'default' },
};
export const CHANNELS = {
  in_person: 'In person', phone: 'Phone', sms: 'SMS', email: 'Email', letter: 'Letter', community_meeting: 'Community meeting', other: 'Other',
};
export const GRIEVANCE_CATEGORIES = {
  land_compensation: 'Land and compensation', environment: 'Environment', employment: 'Employment and local hiring', security: 'Security',
  health_safety: 'Health and safety', community_investment: 'Community investment', cultural_heritage: 'Cultural heritage', other: 'Other',
};
export const SEVERITIES = { low: 'Low', medium: 'Medium', high: 'High', critical: 'Critical' };
// no-mock-gate: config — workflow labels (WorkforceService / SafetyService / GrievanceService ACTIONS)
export const SNAPSHOT_STATUS = {
  draft: { label: 'Draft', color: 'default' }, submitted: { label: 'Submitted', color: 'warning' }, approved: { label: 'Approved', color: 'success' },
};
export const SNAPSHOT_ACTIONS = { submit: 'Submit for approval', approve: 'Approve', return: 'Send back', reopen: 'Reopen' };
export const INCIDENT_STATUS = {
  reported: { label: 'Reported', color: 'info' }, investigating: { label: 'Investigating', color: 'warning' },
  actions_open: { label: 'Actions open', color: 'warning' }, closed: { label: 'Closed', color: 'success' },
};
export const INCIDENT_ACTIONS = { start_investigation: 'Start investigation', record_findings: 'Record findings', close: 'Close incident', reopen: 'Reopen' };
export const GRIEVANCE_STATUS = {
  received: { label: 'Received', color: 'info' }, acknowledged: { label: 'Acknowledged', color: 'info' }, investigating: { label: 'Investigating', color: 'warning' },
  resolved: { label: 'Resolved', color: 'success' }, closed: { label: 'Closed', color: 'default' },
};
export const GRIEVANCE_ACTIONS = {
  acknowledge: 'Acknowledge', investigate: 'Start investigating', escalate: 'Escalate', resolve: 'Resolve', close: 'Close grievance', reopen: 'Reopen',
};
export const SDGS = Array.from({ length: 17 }, (_, i) => i + 1);

const optionalInt = (max, label = 'a whole number') => z.string().trim()
  .refine((v) => v === '' || /^\d+$/.test(v), `Must be ${label} (0 or more)`)
  .refine((v) => v === '' || Number(v) <= max, `At most ${max.toLocaleString(formattingLocale())}`);
const optionalDecimal = (fraction, intDigits) => z.string().trim()
  .refine((v) => v === '' || new RegExp(`^\\d{1,${intDigits}}(\\.\\d{1,${fraction}})?$`).test(v), `A number of 0 or more, up to ${fraction} decimal${fraction > 1 ? 's' : ''}`);
const num = (v) => (v === '' || v == null ? null : Number(v));
const str = (v) => (v == null ? '' : String(v));

// ---------------------------------------------------------------- workforce (WorkforceSnapshotRequest)

export const emptyLine = { level: '', gender: '', ageBand: '', headcount: '', disabledHeadcount: '0', totalAnnualPay: '' };
export const emptySnapshot = {
  orgId: '', reportingPeriodStart: '', reportingPeriodEnd: '', hoursWorked: '', newHires: '', leavers: '', payCurrency: 'NGN', notes: '',
  lines: [{ ...emptyLine }],
};

export const toSnapshotForm = (s) => ({
  orgId: s.orgId ?? '', reportingPeriodStart: s.reportingPeriodStart ?? '', reportingPeriodEnd: s.reportingPeriodEnd ?? '',
  hoursWorked: str(s.hoursWorked), newHires: str(s.newHires), leavers: str(s.leavers), payCurrency: s.payCurrency ?? '', notes: s.notes ?? '',
  lines: (s.lines ?? []).map((l) => ({ level: l.level, gender: l.gender, ageBand: l.ageBand, headcount: str(l.headcount),
    disabledHeadcount: str(l.disabledHeadcount ?? 0), totalAnnualPay: str(l.totalAnnualPay) })),
});

const lineSchema = z.object({
  level: z.enum(Object.keys(LEVELS), { message: 'Choose a level' }),
  gender: z.enum(Object.keys(GENDERS), { message: 'Choose' }),
  ageBand: z.enum(Object.keys(AGE_BANDS), { message: 'Choose' }),
  headcount: z.string().trim().min(1, 'Required').pipe(optionalInt(1000000)),
  disabledHeadcount: optionalInt(1000000),
  totalAnnualPay: optionalDecimal(2, 18),
});

export const snapshotSchema = z.object({
  orgId: z.string().min(1, 'Choose the organisation'),
  reportingPeriodStart: z.string().min(1, 'Start date is required'),
  reportingPeriodEnd: z.string().min(1, 'End date is required'),
  hoursWorked: optionalDecimal(1, 15),
  newHires: optionalInt(1000000),
  leavers: optionalInt(1000000),
  payCurrency: z.string(),
  notes: z.string().trim().max(1000, 'At most 1000 characters'),
  lines: z.array(lineSchema).min(1, 'Add at least one line').max(60, 'At most 60 lines'),
}).superRefine((v, ctx) => {
  const issue = (path, message) => ctx.addIssue({ code: 'custom', path, message });
  if (v.reportingPeriodStart && v.reportingPeriodEnd && v.reportingPeriodEnd < v.reportingPeriodStart) {
    issue(['reportingPeriodEnd'], 'must be on or after the start date');
  }
  const seen = new Set();
  v.lines.forEach((l, i) => {
    const key = `${l.level}|${l.gender}|${l.ageBand}`;
    if (seen.has(key)) issue(['lines', i, 'level'], 'this level, gender and age band is already listed');
    seen.add(key);
    if (l.disabledHeadcount !== '' && l.headcount !== '' && Number(l.disabledHeadcount) > Number(l.headcount)) {
      issue(['lines', i, 'disabledHeadcount'], "can't be more than the headcount");
    }
    if (l.totalAnnualPay !== '' && Number(l.headcount) === 0 && Number(l.totalAnnualPay) > 0) issue(['lines', i, 'totalAnnualPay'], 'a line with no people has no pay');
  });
  if (v.lines.some((l) => l.totalAnnualPay !== '') && !v.payCurrency) issue(['payCurrency'], 'is required when pay is entered');
});

export const toSnapshotBody = (v) => ({
  orgId: v.orgId, reportingPeriodStart: v.reportingPeriodStart, reportingPeriodEnd: v.reportingPeriodEnd, hoursWorked: num(v.hoursWorked),
  newHires: num(v.newHires), leavers: num(v.leavers), payCurrency: v.payCurrency || null, notes: v.notes.trim() || null,
  lines: v.lines.map((l) => ({ level: l.level, gender: l.gender, ageBand: l.ageBand, headcount: Number(l.headcount),
    disabledHeadcount: num(l.disabledHeadcount) ?? 0, totalAnnualPay: num(l.totalAnnualPay) })),
});

// ---------------------------------------------------------------- incident (IncidentRequest)

export const emptyIncident = {
  orgId: '', title: '', incidentType: '', outcome: '', personType: '', occurredAt: '', lostDays: '', location: '', description: '',
};

export const toIncidentForm = (i) => ({
  orgId: i.orgId ?? '', title: i.title ?? '', incidentType: i.incidentType ?? '', outcome: i.outcome ?? '', personType: i.personType ?? '',
  occurredAt: (i.occurredAt ?? '').slice(0, 16), lostDays: str(i.lostDays), location: i.location ?? '', description: i.description ?? '',
});

const INJURY = ['injury', 'illness'];

export const incidentSchema = (now = new Date()) => z.object({
  orgId: z.string().min(1, 'Choose the organisation'),
  title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
  incidentType: z.enum(Object.keys(INCIDENT_TYPES), { message: 'Choose the type' }),
  outcome: z.enum(Object.keys(OUTCOMES), { message: 'Choose the outcome' }),
  personType: z.string(),
  occurredAt: z.string().min(1, 'Date and time are required'),
  lostDays: optionalInt(3650),
  location: z.string().trim().max(255, 'At most 255 characters'),
  description: z.string().trim().max(4000, 'At most 4000 characters'),
}).superRefine((v, ctx) => {
  const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
  const injury = INJURY.includes(v.incidentType);
  if (injury && v.outcome === 'none') issue('outcome', 'an injury or illness needs its outcome (first aid or worse)');
  if (v.incidentType && !injury && v.outcome && v.outcome !== 'none') issue('outcome', 'only an injury or illness has an outcome; record a hurt person as an injury');
  if (injury && !v.personType) issue('personType', 'say who was hurt');
  if (v.outcome === 'lost_time' && !(Number(v.lostDays) >= 1)) issue('lostDays', 'a lost-time injury has at least one lost day');
  if (!['lost_time', 'restricted_work'].includes(v.outcome) && Number(v.lostDays) > 0) {
    issue('lostDays', 'days lost or restricted apply to lost-time and restricted-work cases only');
  }
  if (v.occurredAt && new Date(v.occurredAt) > now) issue('occurredAt', "can't be in the future");
});

export const toIncidentBody = (v) => ({
  orgId: v.orgId, title: v.title.trim(), incidentType: v.incidentType, outcome: v.outcome, personType: v.personType || null,
  occurredAt: v.occurredAt, lostDays: num(v.lostDays), location: v.location.trim() || null, description: v.description.trim() || null,
});

// ---------------------------------------------------------------- community project (ProjectRequest)

export const emptyProject = {
  orgId: '', name: '', category: '', description: '', community: '', stateRegion: '', lga: '', latitude: '', longitude: '', budget: '',
  currency: 'NGN', plannedBeneficiaries: '', actualBeneficiaries: '', status: 'planning', startDate: '', endDate: '', sdgs: [],
  piaHostCommunity: false,
};

export const toProjectForm = (p) => ({
  orgId: p.orgId ?? '', name: p.name ?? '', category: p.category ?? '', description: p.description ?? '', community: p.community ?? '',
  stateRegion: p.stateRegion ?? '', lga: p.lga ?? '', latitude: str(p.latitude), longitude: str(p.longitude), budget: str(p.budget),
  currency: p.currency ?? 'NGN', plannedBeneficiaries: str(p.plannedBeneficiaries), actualBeneficiaries: str(p.actualBeneficiaries),
  status: p.status ?? 'planning', startDate: p.startDate ?? '', endDate: p.endDate ?? '', sdgs: p.sdgs ?? [], piaHostCommunity: !!p.piaHostCommunity,
});

const coordinate = (limit) => z.string().trim()
  .refine((v) => v === '' || /^-?\d{1,3}(\.\d{1,6})?$/.test(v), 'A number with up to 6 decimals')
  .refine((v) => v === '' || Math.abs(Number(v)) <= limit, `Between -${limit} and ${limit}`);

export const projectSchema = z.object({
  orgId: z.string().min(1, 'Choose the organisation'),
  name: z.string().trim().min(1, 'Project name is required').max(255, 'At most 255 characters'),
  category: z.enum(Object.keys(PROJECT_CATEGORIES), { message: 'Choose a category' }),
  description: z.string().trim().max(2000, 'At most 2000 characters'),
  community: z.string().trim().max(255, 'At most 255 characters'),
  stateRegion: z.string().trim().max(100, 'At most 100 characters'),
  lga: z.string().trim().max(100, 'At most 100 characters'),
  latitude: coordinate(90),
  longitude: coordinate(180),
  budget: optionalDecimal(2, 18),
  currency: z.enum(CURRENCIES, { message: 'Choose a currency' }),
  plannedBeneficiaries: optionalInt(100000000),
  actualBeneficiaries: optionalInt(100000000),
  status: z.enum(Object.keys(PROJECT_STATUS), { message: 'Choose a status' }),
  startDate: z.string(),
  endDate: z.string(),
  sdgs: z.array(z.number().int().min(1).max(17)).max(17),
  piaHostCommunity: z.boolean(),
}).superRefine((v, ctx) => {
  const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
  if ((v.latitude === '') !== (v.longitude === '')) issue(v.latitude === '' ? 'latitude' : 'longitude', 'give both latitude and longitude, or neither');
  if (v.startDate && v.endDate && v.endDate < v.startDate) issue('endDate', 'must be on or after the start date');
  if (v.status === 'completed' && v.actualBeneficiaries === '') issue('actualBeneficiaries', 'record how many people benefited before completing the project');
});

export const toProjectBody = (v) => ({
  orgId: v.orgId, name: v.name.trim(), category: v.category, description: v.description.trim() || null, community: v.community.trim() || null,
  stateRegion: v.stateRegion.trim() || null, lga: v.lga.trim() || null, latitude: num(v.latitude), longitude: num(v.longitude),
  budget: num(v.budget), currency: v.currency, plannedBeneficiaries: num(v.plannedBeneficiaries), actualBeneficiaries: num(v.actualBeneficiaries),
  status: v.status, startDate: v.startDate || null, endDate: v.endDate || null, sdgs: [...v.sdgs].sort((a, b) => a - b),
  piaHostCommunity: v.piaHostCommunity,
});

export const expenditureSchema = (today) => z.object({
  spentOn: z.string().min(1, 'Date is required').refine((d) => !d || d <= today, "can't be in the future"),
  description: z.string().trim().min(1, 'Say what it was for').max(500, 'At most 500 characters'),
  amount: z.string().trim().min(1, 'Amount is required').pipe(optionalDecimal(2, 18)).refine((v) => Number(v) > 0, 'Must be more than 0'),
});

export const evidenceSchema = (today) => z.object({
  latitude: z.string().trim().min(1, 'Latitude is required').pipe(coordinate(90)),
  longitude: z.string().trim().min(1, 'Longitude is required').pipe(coordinate(180)),
  capturedOn: z.string().min(1, 'Date is required').refine((d) => !d || d <= today, "can't be in the future"),
  caption: z.string().trim().max(300, 'At most 300 characters'),
});

// ---------------------------------------------------------------- grievance (GrievanceRequest)

export const emptyGrievance = {
  orgId: '', projectId: '', community: '', anonymous: false, complainantName: '', complainantContact: '', channel: '', category: '', severity: '',
  title: '', description: '', receivedOn: '', assignedTo: '',
};

export const toGrievanceForm = (g) => ({
  orgId: g.orgId ?? '', projectId: g.projectId ?? '', community: g.community ?? '', anonymous: !!g.anonymous, complainantName: g.complainantName ?? '',
  complainantContact: g.complainantContact ?? '', channel: g.channel ?? '', category: g.category ?? '', severity: g.severity ?? '', title: g.title ?? '',
  description: g.description ?? '', receivedOn: g.receivedOn ?? '', assignedTo: g.assignedTo ?? '',
});

export const grievanceSchema = (today) => z.object({
  orgId: z.string().min(1, 'Choose the organisation'),
  projectId: z.string(),
  community: z.string().trim().max(255, 'At most 255 characters'),
  anonymous: z.boolean(),
  complainantName: z.string().trim().max(200, 'At most 200 characters'),
  complainantContact: z.string().trim().max(200, 'At most 200 characters'),
  channel: z.enum(Object.keys(CHANNELS), { message: 'Choose how it was received' }),
  category: z.enum(Object.keys(GRIEVANCE_CATEGORIES), { message: 'Choose a category' }),
  severity: z.enum(Object.keys(SEVERITIES), { message: 'Choose the severity' }),
  title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
  description: z.string().trim().min(1, 'Description is required').max(4000, 'At most 4000 characters'),
  receivedOn: z.string().min(1, 'Date received is required'),
  assignedTo: z.string(),
}).superRefine((v, ctx) => {
  const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
  if (v.anonymous && (v.complainantName.trim() || v.complainantContact.trim())) issue('anonymous', 'an anonymous grievance has no complainant name or contact');
  if (!v.anonymous && !v.complainantName.trim()) issue('complainantName', 'is required unless the grievance is anonymous');
  if (v.receivedOn && v.receivedOn > today) issue('receivedOn', "can't be in the future");
});

export const toGrievanceBody = (v) => ({
  orgId: v.orgId, projectId: v.projectId || null, community: v.community.trim() || null, anonymous: v.anonymous,
  complainantName: v.anonymous ? null : v.complainantName.trim() || null, complainantContact: v.anonymous ? null : v.complainantContact.trim() || null,
  channel: v.channel, category: v.category, severity: v.severity, title: v.title.trim(), description: v.description.trim(), receivedOn: v.receivedOn,
  assignedTo: v.assignedTo || null,
});

/** Today in Lagos as YYYY-MM-DD (dates are compared with the server's Africa/Lagos calendar). */
export const lagosToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' });

/** "1234567.5" → "1,234,567.5" */
export const fmt = (v, digits = 1) => (v == null ? '—' : Number(v).toLocaleString(formattingLocale(), { maximumFractionDigits: digits }));
export const pct = (v) => (v == null ? '—' : `${fmt(v)}%`);

/** The server's explanation of a refused step, for an alert. */
export const problemText = (err) => err?.data?.detail ?? Object.values(err?.data?.errors ?? {})[0] ?? 'Something went wrong';
