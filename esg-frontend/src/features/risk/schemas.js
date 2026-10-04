import { z } from 'zod';
import { formattingLocale } from '../../i18n';

/**
 * Risk & Materiality form rules. Mirror the server exactly: RiskDtos (Bean Validation) + MaterialityService.check,
 * RiskRegisterService.check and ClimateService.check. Keep them in step.
 */
// no-mock-gate: config — option labels (RiskDtos regexes)
export const GROUPS = {
  employees: 'Employees', communities: 'Communities', investors: 'Investors', lenders: 'Lenders', regulators: 'Regulators', customers: 'Customers',
  suppliers: 'Suppliers', civil_society: 'Civil society', other: 'Other',
};
export const ESG = { E: 'Environmental', S: 'Social', G: 'Governance' };
export const RISK_CATEGORIES = {
  environmental: 'Environmental', social: 'Social', governance: 'Governance', climate_physical: 'Climate — physical',
  climate_transition: 'Climate — transition', regulatory: 'Regulatory', reputational: 'Reputational', operational: 'Operational',
};
export const HORIZONS = { short: 'Short term', medium: 'Medium term', long: 'Long term' };
export const RISK_STATUS = {
  open: { label: 'Open', color: 'warning' }, mitigating: { label: 'Mitigating', color: 'info' }, accepted: { label: 'Accepted', color: 'default' },
  closed: { label: 'Closed', color: 'success' },
};
export const LIKELIHOOD = { 1: '1 · Rare', 2: '2 · Unlikely', 3: '3 · Possible', 4: '4 · Likely', 5: '5 · Almost certain' };
export const IMPACT = { 1: '1 · Minor', 2: '2 · Moderate', 3: '3 · Major', 4: '4 · Severe', 5: '5 · Critical' };
export const LEVEL = { 1: '1 · Very low', 2: '2 · Low', 3: '3 · Moderate', 4: '4 · High', 5: '5 · Very high' };
export const BANDS = {
  very_high: { label: 'Very high', color: 'error' }, high: { label: 'High', color: 'warning' }, medium: { label: 'Medium', color: 'info' },
  low: { label: 'Low', color: 'success' },
};
export const HAZARDS = {
  flooding: 'Flooding', drought_desertification: 'Drought and desertification', heat_stress: 'Heat stress', water_scarcity: 'Water scarcity',
  sea_level_rise: 'Sea level rise', storms: 'Storms', wildfire: 'Wildfire',
};
export const PATHWAYS = { 'RCP4.5': 'RCP 4.5', 'RCP8.5': 'RCP 8.5' };
export const QUADRANTS = {
  double: { label: 'Double material', color: 'error' }, impact: { label: 'Impact material', color: 'warning' },
  financial: { label: 'Financially material', color: 'info' }, not_material: { label: 'Not material', color: 'default' },
};
export const ASSESSMENT_STATUS = {
  draft: { label: 'Draft', color: 'default' }, in_review: { label: 'In review', color: 'warning' }, approved: { label: 'Approved', color: 'success' },
};
export const ASSESSMENT_ACTIONS = { submit: 'Submit for approval', approve: 'Approve', return: 'Send back', reopen: 'Reopen' };
export const SCENARIO_CATEGORIES = { orderly: 'Orderly', disorderly: 'Disorderly', hot_house: 'Hot house world' };

const str = (v) => (v == null ? '' : String(v));
const num = (v) => (v === '' || v == null ? null : Number(v));
const decimal = (min, max, label) => z.string().trim().min(1, `${label} is required`)
  .refine((v) => /^\d(\.\d{1,2})?$/.test(v) || /^0?\.\d{1,2}$/.test(v), 'A number with up to 2 decimals')
  .refine((v) => Number(v) >= min && Number(v) <= max, `Between ${min} and ${max}`);
const score = (label) => z.string().refine((v) => /^[1-5]$/.test(v), `Choose the ${label}`);

// ---------------------------------------------------------------- assessment (AssessmentRequest)

export const emptyAssessment = {
  name: '', reportingYear: String(new Date().getFullYear()), description: '', impactThreshold: '3', financialThreshold: '3', stakeholderBlend: '0',
  stakeholders: [],
};

export const toAssessmentForm = (a) => ({
  name: a.name ?? '', reportingYear: str(a.reportingYear), description: a.description ?? '', impactThreshold: str(a.impactThreshold),
  financialThreshold: str(a.financialThreshold), stakeholderBlend: str(a.stakeholderBlend),
  stakeholders: (a.stakeholders ?? []).map((s) => ({ group: s.group, weight: str(s.weight) })),
});

export const assessmentSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(255, 'At most 255 characters'),
  reportingYear: z.string().refine((v) => /^\d{4}$/.test(v) && Number(v) >= 2000 && Number(v) <= 2100, 'A year between 2000 and 2100'),
  description: z.string().trim().max(2000, 'At most 2000 characters'),
  impactThreshold: decimal(0.2, 5, 'Impact threshold'),
  financialThreshold: decimal(0.2, 5, 'Financial threshold'),
  stakeholderBlend: decimal(0, 0.5, 'Blend'),
  stakeholders: z.array(z.object({
    group: z.enum(Object.keys(GROUPS), { message: 'Choose a group' }),
    weight: z.string().trim().refine((v) => /^\d{1,3}(\.\d{1,2})?$/.test(v) && Number(v) > 0 && Number(v) <= 100, 'More than 0, at most 100'),
  })).max(9, 'At most 9 groups'),
}).superRefine((v, ctx) => {
  const seen = new Set();
  v.stakeholders.forEach((s, i) => {
    if (seen.has(s.group)) ctx.addIssue({ code: 'custom', path: ['stakeholders', i, 'group'], message: 'this group is already listed' });
    seen.add(s.group);
  });
  if (Number(v.stakeholderBlend) > 0 && v.stakeholders.length === 0) {
    ctx.addIssue({ code: 'custom', path: ['stakeholderBlend'], message: 'add stakeholder groups before blending their ratings in' });
  }
});

export const toAssessmentBody = (v) => ({
  name: v.name.trim(), reportingYear: Number(v.reportingYear), description: v.description.trim() || null, impactThreshold: Number(v.impactThreshold),
  financialThreshold: Number(v.financialThreshold), stakeholderBlend: Number(v.stakeholderBlend),
  stakeholders: v.stakeholders.map((s) => ({ group: s.group, weight: Number(s.weight) })),
});

// ---------------------------------------------------------------- topic (TopicRequest)

export const emptyTopic = {
  topic: '', category: '', description: '', griTopic: '', impactType: 'actual', severityScale: '', severityScope: '', severityIrremediability: '',
  impactLikelihood: '', financialMagnitude: '', financialLikelihood: '', ratings: {},
};

export const toTopicForm = (t) => ({
  topic: t.topic ?? '', category: t.category ?? '', description: t.description ?? '', griTopic: t.griTopic ?? '', impactType: t.impactType ?? 'actual',
  severityScale: str(t.severityScale), severityScope: str(t.severityScope), severityIrremediability: str(t.severityIrremediability),
  impactLikelihood: str(t.impactLikelihood), financialMagnitude: str(t.financialMagnitude), financialLikelihood: str(t.financialLikelihood),
  ratings: Object.fromEntries(Object.entries(t.ratings ?? {}).map(([k, v]) => [k, str(v)])),
});

export const topicSchema = z.object({
  topic: z.string().trim().min(1, 'Topic is required').max(255, 'At most 255 characters'),
  category: z.enum(Object.keys(ESG), { message: 'Choose E, S or G' }),
  description: z.string().trim().max(2000, 'At most 2000 characters'),
  griTopic: z.string().trim().max(20, 'At most 20 characters'),
  impactType: z.enum(['actual', 'potential']),
  severityScale: score('scale'),
  severityScope: score('scope'),
  severityIrremediability: score('irremediability'),
  impactLikelihood: z.string(),
  financialMagnitude: score('magnitude'),
  financialLikelihood: score('likelihood'),
  ratings: z.record(z.string(), z.string().optional()),
}).superRefine((v, ctx) => {
  if (v.impactType === 'potential' && !/^[1-5]$/.test(v.impactLikelihood)) {
    ctx.addIssue({ code: 'custom', path: ['impactLikelihood'], message: 'a potential impact needs its likelihood' });
  }
});

export const toTopicBody = (v) => ({
  topic: v.topic.trim(), category: v.category, description: v.description.trim() || null, griTopic: v.griTopic.trim() || null, impactType: v.impactType,
  severityScale: Number(v.severityScale), severityScope: Number(v.severityScope), severityIrremediability: Number(v.severityIrremediability),
  impactLikelihood: v.impactType === 'potential' ? Number(v.impactLikelihood) : null, financialMagnitude: Number(v.financialMagnitude),
  financialLikelihood: Number(v.financialLikelihood),
  ratings: Object.fromEntries(Object.entries(v.ratings).filter(([, r]) => r != null && r !== '').map(([g, r]) => [g, Number(r)])),
});

// ---------------------------------------------------------------- risk (RiskRequest)

export const emptyRisk = {
  orgId: '', title: '', category: '', sectorCode: '', description: '', timeHorizon: '', likelihood: '', impact: '', controls: '', residualLikelihood: '',
  residualImpact: '', ownerUserId: '', status: 'open', reviewDate: '', topicId: '',
};

export const toRiskForm = (r) => ({
  orgId: r.orgId ?? '', title: r.title ?? '', category: r.category ?? '', sectorCode: r.sectorCode ?? '', description: r.description ?? '',
  timeHorizon: r.timeHorizon ?? '', likelihood: str(r.likelihood), impact: str(r.impact), controls: r.controls ?? '',
  residualLikelihood: str(r.residualLikelihood), residualImpact: str(r.residualImpact), ownerUserId: r.ownerUserId ?? '', status: r.status ?? 'open',
  reviewDate: r.reviewDate ?? '', topicId: r.topicId ?? '',
});

export const riskSchema = z.object({
  orgId: z.string().min(1, 'Choose the organisation'),
  title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
  category: z.enum(Object.keys(RISK_CATEGORIES), { message: 'Choose a category' }),
  sectorCode: z.string(),
  description: z.string().trim().max(4000, 'At most 4000 characters'),
  timeHorizon: z.enum(Object.keys(HORIZONS), { message: 'Choose the time horizon' }),
  likelihood: score('likelihood'),
  impact: score('impact'),
  controls: z.string().trim().max(4000, 'At most 4000 characters'),
  residualLikelihood: z.string(),
  residualImpact: z.string(),
  ownerUserId: z.string(),
  status: z.enum(Object.keys(RISK_STATUS)),
  reviewDate: z.string(),
  topicId: z.string(),
}).superRefine((v, ctx) => {
  const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
  if ((v.residualLikelihood === '') !== (v.residualImpact === '')) {
    issue(v.residualLikelihood === '' ? 'residualLikelihood' : 'residualImpact', 'give both residual scores, or neither');
  } else if (v.residualLikelihood && Number(v.residualLikelihood) * Number(v.residualImpact) > Number(v.likelihood) * Number(v.impact)) {
    issue('residualImpact', "the risk after controls can't score higher than before them");
  }
});

export const toRiskBody = (v) => ({
  orgId: v.orgId, title: v.title.trim(), category: v.category, sectorCode: v.sectorCode || null, description: v.description.trim() || null,
  timeHorizon: v.timeHorizon, likelihood: Number(v.likelihood), impact: Number(v.impact), controls: v.controls.trim() || null,
  residualLikelihood: num(v.residualLikelihood), residualImpact: num(v.residualImpact), ownerUserId: v.ownerUserId || null, status: v.status,
  reviewDate: v.reviewDate || null, topicId: v.topicId || null,
});

// ---------------------------------------------------------------- physical risk (PhysicalRequest), carbon price

export const emptyPhysical = {
  orgId: '', hazard: '', pathway: 'RCP4.5', horizonYear: '2050', exposure: '', vulnerability: '', source: '', notes: '', assessedOn: '',
};

export const toPhysicalForm = (p) => ({
  orgId: p.orgId ?? '', hazard: p.hazard ?? '', pathway: p.pathway ?? 'RCP4.5', horizonYear: str(p.horizonYear), exposure: str(p.exposure),
  vulnerability: str(p.vulnerability), source: p.source ?? '', notes: p.notes ?? '', assessedOn: p.assessedOn ?? '',
});

export const physicalSchema = (today) => z.object({
  orgId: z.string().min(1, 'Choose the site'),
  hazard: z.enum(Object.keys(HAZARDS), { message: 'Choose the hazard' }),
  pathway: z.enum(Object.keys(PATHWAYS), { message: 'Choose the pathway' }),
  horizonYear: z.enum(['2030', '2050'], { message: 'Choose 2030 or 2050' }),
  exposure: score('exposure'),
  vulnerability: score('vulnerability'),
  source: z.string().trim().min(1, 'Cite the data source').max(500, 'At most 500 characters'),
  notes: z.string().trim().max(2000, 'At most 2000 characters'),
  assessedOn: z.string().min(1, 'Date is required').refine((d) => !d || d <= today, "can't be in the future"),
});

export const toPhysicalBody = (v) => ({
  orgId: v.orgId, hazard: v.hazard, pathway: v.pathway, horizonYear: Number(v.horizonYear), exposure: Number(v.exposure),
  vulnerability: Number(v.vulnerability), source: v.source.trim(), notes: v.notes.trim() || null, assessedOn: v.assessedOn,
});

export const priceSchema = z.object({
  scenarioCode: z.string().min(1, 'Choose the scenario'),
  priceYear: z.string().refine((v) => /^\d{4}$/.test(v) && Number(v) >= 2020 && Number(v) <= 2100, 'A year between 2020 and 2100'),
  priceUsd: z.string().trim().refine((v) => /^\d{1,10}(\.\d{1,2})?$/.test(v), 'A price of 0 or more, up to 2 decimals'),
  source: z.string().trim().min(1, 'Cite the source of the price path').max(500, 'At most 500 characters'),
});

export const lagosToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' });
export const fmt = (v, digits = 2) => (v == null ? '—' : Number(v).toLocaleString(formattingLocale(), { maximumFractionDigits: digits }));
export const problemText = (err) => err?.data?.detail ?? Object.values(err?.data?.errors ?? {})[0] ?? 'Something went wrong';
