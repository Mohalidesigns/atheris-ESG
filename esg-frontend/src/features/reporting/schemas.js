import { z } from 'zod';

/**
 * Reporting forms, mirroring ReportingDtos (ReportRequest, ReportDetailsRequest, SectionInput, StepActionRequest) and
 * ReportService / VariableEngine rules.
 */

// no-mock-gate: config — labels for report statuses
export const REPORT_STATUS = {
  draft: { label: 'Draft', color: 'default' }, in_review: { label: 'In review', color: 'warning' }, approved: { label: 'Approved', color: 'success' },
};
// no-mock-gate: config — labels for report version events
export const VERSION_EVENT = {
  created: 'Created from template', saved: 'Saved', refreshed: 'Data refreshed', submitted: 'Submitted for approval', approved: 'Approved',
  changes_requested: 'Changes requested', reopened: 'Reopened',
};
// no-mock-gate: config — labels for approval step statuses
export const STEP_STATUS = {
  pending: { label: 'Waiting', color: 'default' }, approved: { label: 'Approved', color: 'success' },
  rejected: { label: 'Rejected', color: 'error' }, changes_requested: { label: 'Changes requested', color: 'warning' },
};
// no-mock-gate: config — variable sources VariableEngine understands (metrics come from the catalogue)
export const CARBON_VARIABLES = {
  scope1: 'Scope 1 emissions', scope2_location: 'Scope 2 emissions (location-based)', scope3: 'Scope 3 emissions',
  total_location: 'Total emissions (location-based)', flaring: 'Gas flaring emissions',
};
// no-mock-gate: config — governance variables (VariableEngine, WP-34)
export const GOVERNANCE_VARIABLES = {
  board_size: 'Board size', board_independent_pct: 'Independent directors %', board_female_pct: 'Women on the board %',
  policies_published: 'Published policies', whistleblower_cases: 'Whistleblower cases received',
};
// no-mock-gate: config — social variables (VariableEngine / SocialMetricsService.VARIABLES, WP-33)
export const SOCIAL_VARIABLES = {
  headcount: 'Headcount', women_pct: 'Women %', women_management_pct: 'Women in management %', women_senior_pct: 'Women in senior roles %',
  disability_pct: 'Employees with disability %', pay_gap_pct: 'Gender pay gap (mean) %', turnover_pct: 'Employee turnover %',
  hours_worked: 'Hours worked', ltifr: 'LTIFR', trir: 'TRIR', lost_time_injuries: 'Lost-time injuries', recordable_cases: 'Recordable cases',
  fatalities: 'Fatalities', community_investment: 'Community investment', beneficiaries: 'Community project beneficiaries',
  grievances_received: 'Grievances received', grievances_resolved: 'Grievances resolved', grievances_open: 'Grievances open at period end',
};
// no-mock-gate: config — risk variables (VariableEngine / RiskInsights.VARIABLES, WP-32)
export const RISK_VARIABLES = {
  material_topics: 'Number of material topics', material_topic_list: 'List of material topics', open_risks: 'Open risks',
  high_risks: 'High and very high risks', climate_risks: 'Open climate risks', physical_high_risks: 'Sites at high physical risk',
  scenarios_analysed: 'Climate scenarios analysed',
};
// no-mock-gate: config — supply chain variables (VariableEngine.SUPPLY, WP-31)
export const SUPPLY_VARIABLES = {
  suppliers: 'Suppliers on the register', suppliers_assessed: 'Suppliers assessed', high_risk_suppliers: 'High-risk suppliers',
  average_score: 'Average supplier ESG score', open_actions: 'Open corrective actions', local_spend_pct: 'Local spend %',
  scope3_cat1_tonnes: 'Scope 3 Category 1 from supplier spend',
};
// no-mock-gate: config — compliance variable measures
export const COMPLIANCE_MEASURES = { completeness: 'completeness %', met: 'requirements met', total: 'requirements' };

const VAR = /\{\{\s*([a-z]+):([A-Za-z0-9_]+(?::[a-z]+)?)\s*\}\}/g;

/** Variables in the text that VariableEngine wouldn't know (same rules as the server). */
export const unknownVariables = (text, { metricKeys = [], frameworks = [] } = {}) => {
  const unknown = [];
  for (const [, source, key] of (text ?? '').matchAll(VAR)) {
    const ok = (source === 'metric' && metricKeys.includes(key))
      || (source === 'carbon' && key in CARBON_VARIABLES)
      || (source === 'governance' && key in GOVERNANCE_VARIABLES)
      || (source === 'social' && key in SOCIAL_VARIABLES)
      || (source === 'risk' && key in RISK_VARIABLES)
      || (source === 'supply' && key in SUPPLY_VARIABLES)
      || (source === 'compliance' && key.split(':').length === 2 && frameworks.includes(key.split(':')[0]) && key.split(':')[1] in COMPLIANCE_MEASURES)
      || (source === 'report' && ['title', 'period'].includes(key))
      || (source === 'tenant' && key === 'name');
    if (!ok && !unknown.includes(`${source}:${key}`)) unknown.push(`${source}:${key}`);
  }
  return unknown;
};

// ---------------------------------------------------------------- create / details

export const emptyReport = { templateId: '', title: '', description: '', reportingPeriodStart: '', reportingPeriodEnd: '' };

const period = (v, ctx) => {
  if (v.reportingPeriodStart && v.reportingPeriodEnd && v.reportingPeriodEnd < v.reportingPeriodStart) {
    ctx.addIssue({ code: 'custom', path: ['reportingPeriodEnd'], message: 'Must be on or after the start date' });
  }
};

const reportBase = z.object({
  templateId: z.string().min(1, 'Choose a template'),
  title: z.string().trim().min(1, 'Title is required').max(500, 'At most 500 characters'),
  description: z.string().trim().max(1000, 'At most 1000 characters'),
  reportingPeriodStart: z.string().min(1, 'Start date is required'),
  reportingPeriodEnd: z.string().min(1, 'End date is required'),
});

export const reportSchema = reportBase.superRefine(period);

export const detailsSchema = reportBase.omit({ templateId: true }).superRefine(period);

export const toReportBody = (v) => ({
  templateId: v.templateId, title: v.title.trim(), description: v.description.trim() || null,
  reportingPeriodStart: v.reportingPeriodStart, reportingPeriodEnd: v.reportingPeriodEnd,
});

export const toDetailsBody = (v) => ({
  title: v.title.trim(), description: v.description.trim() || null, reportingPeriodStart: v.reportingPeriodStart, reportingPeriodEnd: v.reportingPeriodEnd,
});

export const toReportForm = (r) => ({
  templateId: r.templateId ?? '', title: r.title, description: r.description ?? '',
  reportingPeriodStart: r.reportingPeriodStart ?? '', reportingPeriodEnd: r.reportingPeriodEnd ?? '',
});

// ---------------------------------------------------------------- sections

export const sectionsSchema = (ctx) => z.object({
  sections: z.array(z.object({
    id: z.string().optional(),
    title: z.string().trim().min(1, 'Section title is required').max(255, 'At most 255 characters'),
    type: z.enum(['narrative', 'data']),
    content: z.string().max(20000, 'At most 20,000 characters'),
  }).superRefine((s, issue) => {
    const unknown = unknownVariables(s.content, ctx);
    if (unknown.length) issue.addIssue({ code: 'custom', path: ['content'], message: `Unknown variable: {{${unknown.join('}}, {{')}}}` });
  })).min(1, 'A report needs at least one section').max(50, 'At most 50 sections'),
});

export const toSectionsBody = (v, version) => ({
  version, sections: v.sections.map((s) => ({ ...(s.id ? { id: s.id } : {}), title: s.title.trim(), type: s.type, content: s.content })),
});

// ---------------------------------------------------------------- approval decision

export const decisionSchema = z.object({
  action: z.enum(['approve', 'reject', 'request_changes']),
  comments: z.string().trim().max(2000, 'At most 2000 characters'),
}).superRefine((v, ctx) => {
  if (v.action !== 'approve' && !v.comments) ctx.addIssue({ code: 'custom', path: ['comments'], message: 'Say what needs to change' });
});

// ---------------------------------------------------------------- version comparison

/** Line diff (LCS): [{ type: 'same' | 'added' | 'removed', text }]. */
export const diffLines = (before, after) => {
  const a = (before ?? '').split('\n');
  const b = (after ?? '').split('\n');
  const n = a.length;
  const m = b.length;
  const lcs = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i -= 1) {
    for (let j = m - 1; j >= 0; j -= 1) lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
  }
  const out = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (a[i] === b[j]) { out.push({ type: 'same', text: a[i] }); i += 1; j += 1; } else if (lcs[i + 1][j] >= lcs[i][j + 1]) { out.push({ type: 'removed', text: a[i] }); i += 1; } else { out.push({ type: 'added', text: b[j] }); j += 1; }
  }
  while (i < n) { out.push({ type: 'removed', text: a[i] }); i += 1; }
  while (j < m) { out.push({ type: 'added', text: b[j] }); j += 1; }
  return out;
};
