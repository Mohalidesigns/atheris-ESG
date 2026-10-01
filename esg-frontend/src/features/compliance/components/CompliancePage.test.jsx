import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CompliancePage from './CompliancePage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { assessSchema, attestationSchema, changeSchema, deadlineSchema, emptyChange, emptyDeadline, submissionSchema, waiverSchema } from '../schemas';

const YEAR = new Date().getFullYear();
const frameworks = [
  { code: 'CBN_NSBP', name: 'CBN Nigerian Sustainable Banking Principles', jurisdiction: 'Nigeria', frameworkType: 'mandatory', contentStatus: 'seeded', source: 'CBN (2012)', requirementCount: 9 },
  { code: 'ISSB_S2', name: 'IFRS S2 Climate-related Disclosures', jurisdiction: 'Global', frameworkType: 'mandatory', contentStatus: 'seeded', source: 'IFRS S2', requirementCount: 2 },
  { code: 'SEC_NG', name: 'SEC Nigeria Sustainability Reporting Guidelines', jurisdiction: 'Nigeria', frameworkType: 'mandatory', contentStatus: 'pending_sme', description: 'Requirements to be supplied and verified by the ESG SME (WP-17).', requirementCount: 0 },
];
const s2 = [
  { id: 'r1', code: 'S2-29(a)', title: 'Greenhouse gas emissions', parentCode: null, metricKeys: [], mappedTo: [] },
  { id: 'r2', code: 'S2-29(a)(i)(1)', title: 'Absolute gross Scope 1 GHG emissions', parentCode: 'S2-29(a)', metricKeys: ['scope1_tco2e'], mappedTo: [{ framework: 'GRI', code: '305-1', title: 'Direct (Scope 1) GHG emissions' }] },
];
const status = [
  { code: 'CBN_NSBP', name: 'CBN Nigerian Sustainable Banking Principles', jurisdiction: 'Nigeria', frameworkType: 'mandatory', contentStatus: 'seeded', active: true, totalRequirements: 9, met: 2, partial: 1, missing: 6, notApplicable: 0, completeness: 27.8, highPriority: 6, nextDeadline: { id: 'd1', description: 'CBN NSBP annual report', deadlineDate: `${YEAR + 1}-03-31`, daysLeft: 45, status: 'upcoming' } },
  { code: 'SEC_NG', name: 'SEC Nigeria Sustainability Reporting Guidelines', jurisdiction: 'Nigeria', frameworkType: 'mandatory', contentStatus: 'pending_sme', active: true, totalRequirements: 0, met: 0, partial: 0, missing: 0, notApplicable: 0, completeness: 0, highPriority: 0, nextDeadline: null },
  { code: 'ISSB_S2', name: 'IFRS S2 Climate-related Disclosures', jurisdiction: 'Global', frameworkType: 'mandatory', contentStatus: 'seeded', active: false, totalRequirements: 1, met: 1, partial: 0, missing: 0, notApplicable: 0, completeness: 100, highPriority: 0, nextDeadline: null },
];
const gap = (over = {}) => ({
  framework: 'CBN_NSBP', frameworkName: 'CBN Nigerian Sustainable Banking Principles', frameworkType: 'mandatory', contentStatus: 'seeded', reportingYear: YEAR,
  totalRequirements: 2, met: 0, partial: 1, missing: 1, notApplicable: 0, overallCompleteness: 25, highPriority: 1,
  requirements: [
    { id: 'p1', code: 'P1', title: 'Principle 1: E&S risk management', category: 'GEN', parentCode: null, leaf: true, quantitative: false, status: 'missing', basis: 'none', completeness: 0, metricKeys: [], missingMetrics: [], satisfiedBy: null, priorityScore: 70, priority: 'high', recommendation: 'Prepare the disclosure and attest it with evidence, or mark it not applicable with the reason', attestation: null },
    { id: 'p4', code: 'P4', title: "Principle 4: Women's Economic Empowerment", category: 'GEN', parentCode: null, leaf: true, quantitative: false, status: 'partial', basis: 'supporting_data', completeness: 50, metricKeys: ['employees_female'], missingMetrics: [], satisfiedBy: null, priorityScore: 55, priority: 'medium', recommendation: 'The supporting data is approved: write the disclosure and attest it complete with evidence', attestation: { status: 'in_progress', notes: 'Drafting', evidenceFileIds: [], attestedBy: 'Kemi Compliance', attestedAt: '2026-10-01T10:00:00Z', version: 2 } },
  ],
  ...over,
});
const owners = [{ id: 'u-co', name: 'Kemi Compliance', email: 'compliance@esgpro.ng' }, { id: 'u-dir', name: 'Adaeze Usman', email: 'adaeze.usman@esgpro.ng' }];
const deadline = {
  id: 'd1', framework: 'CBN_NSBP', frameworkName: 'CBN Nigerian Sustainable Banking Principles', description: 'CBN NSBP annual report',
  deadlineDate: `${YEAR + 1}-03-31`, reportingYear: YEAR, ownerUserId: 'u-co', ownerName: 'Kemi Compliance', reminderDays: [90, 60, 30, 14, 7, 1],
  status: 'upcoming', daysLeft: 45, reminders: [{ kind: 'reminder', daysBefore: 60, sentOn: `${YEAR}-12-01`, recipient: 'Kemi Compliance' }], version: 4,
};
const change = {
  id: 'c1', framework: 'ISSB_S2', frameworkName: 'IFRS S2 Climate-related Disclosures', regulator: 'FRCN', title: 'ISSB adoption roadmap phase 2',
  summary: 'Mandatory for PIEs', sourceUrl: 'https://frcnigeria.gov.ng', publishedDate: '2026-03-01', effectiveDate: '2027-01-01', assessmentDue: '2026-04-01',
  ownerUserId: 'u-dir', ownerName: 'Adaeze Usman', status: 'assessing', version: 1,
};
const routes = (extra = {}) => ({
  'GET /compliance/frameworks': () => ok(frameworks),
  'GET /compliance/frameworks/CBN_NSBP/requirements': () => ok([]),
  'GET /compliance/frameworks/ISSB_S2/requirements': () => ok(s2),
  'GET /compliance/frameworks/SEC_NG/requirements': () => ok([]),
  'GET /compliance/status': () => ok(status),
  'GET /compliance/gaps': () => ok(gap()),
  'GET /compliance/deadlines': () => ok([deadline]),
  'GET /compliance/changes': () => ok([change]),
  'GET /compliance/owners': () => ok(owners),
  ...extra,
});
const READ = ['compliance:read'];
const OFFICER = ['compliance:read', 'compliance:create', 'compliance:update', 'compliance:configure', 'compliance:approve', 'files:create'];

const choose = async (container, label, option) => {
  await userEvent.click(within(container).getByRole('combobox', { name: label }));
  await userEvent.click(await screen.findByRole('option', { name: option }));
};
const openTab = async (name) => userEvent.click(await screen.findByRole('tab', { name }));

describe('CompliancePage overview, gaps and frameworks', () => {
  it('overview shows each framework\'s completeness, gaps and next filing, active frameworks first', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(READ) });
    const cbn = await screen.findByLabelText('Status CBN_NSBP');
    expect(cbn).toHaveTextContent('27.8%');
    expect(cbn).toHaveTextContent('2 met · 1 partial · 6 missing · 6 high priority');
    expect(cbn).toHaveTextContent('Next filing: CBN NSBP annual report');
    expect(cbn).toHaveTextContent('45 days');
    expect(screen.getByLabelText('Status SEC_NG')).toHaveTextContent('Requirements not loaded yet');
    expect(screen.getByLabelText('High-priority gaps')).toHaveTextContent('6');
    expect(screen.getByLabelText('Changes to assess')).toHaveTextContent('1');
    expect(calls.find((c) => c.key === 'GET /compliance/status').query).toEqual({ year: String(YEAR) });
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
  });

  it('gap checklist: status, basis, priority and next step; opening from the overview selects the framework', async () => {
    const calls = fakeApi(routes());
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(READ) });
    const cbn = await screen.findByLabelText('Status CBN_NSBP');
    await userEvent.click(within(cbn).getByRole('button', { name: 'Open gap analysis' }));
    const table = await screen.findByRole('table', { name: 'Requirement checklist' });
    expect(within(table).getByText('Missing')).toBeInTheDocument();
    expect(within(table).getByText('supporting data approved')).toBeInTheDocument();
    expect(within(table).getByText('high · 70')).toBeInTheDocument();
    expect(within(table).getByText(/write the disclosure and attest it complete/)).toBeInTheDocument();
    expect(screen.getByLabelText('Overall completeness')).toHaveTextContent('25% complete');
    expect(calls.filter((c) => c.key === 'GET /compliance/gaps').at(-1).query).toEqual({ framework: 'CBN_NSBP', year: String(YEAR) });
    // Read-only: no recording.
    expect(within(table).queryByRole('button', { name: 'Record status for P1' })).not.toBeInTheDocument();
  });

  it('attesting complete needs evidence; the body names the framework, requirement, year and version', async () => {
    let sent;
    fakeApi(routes({
      'PUT /compliance/attestations': (body) => { sent = body; return ok(gap()); },
      'GET /files/storage-info': () => ok({ storage: 'Local', maxBytes: 1000000, allowedExtensions: 'pdf', virusScanning: 'Not configured' }),
      'GET /files': () => ok([]),
    }));
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(OFFICER) });
    await openTab('Gap analysis');
    await userEvent.click(await screen.findByRole('button', { name: 'Record status for P4' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('Notes')).toHaveValue('Drafting');
    await choose(dialog, 'Status', 'Not applicable');
    await userEvent.clear(within(dialog).getByLabelText('Notes'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save status' }));
    expect(await within(dialog).findByText("Say why it doesn't apply")).toBeInTheDocument();
    await choose(dialog, 'Status', 'Complete');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save status' }));
    expect(await within(dialog).findByText('Attach the evidence for a complete requirement')).toBeInTheDocument();
    await choose(dialog, 'Status', 'Not applicable');
    await userEvent.type(within(dialog).getByLabelText('Notes'), 'We do not lend');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save status' }));
    await waitFor(() => expect(sent).toEqual({
      framework: 'CBN_NSBP', requirementCode: 'P4', year: YEAR, status: 'not_applicable', notes: 'We do not lend', evidenceFileIds: [], version: 2,
    }));
  });

  it('a quantitative requirement can\'t be set in progress by hand', async () => {
    const report = gap();
    report.requirements[0] = { ...report.requirements[0], quantitative: true, metricKeys: ['water_withdrawal_m3'], missingMetrics: ['water_withdrawal_m3'], basis: 'data' };
    fakeApi(routes({ 'GET /compliance/gaps': () => ok(report), 'GET /files/storage-info': () => ok({ maxBytes: 1000 }) }));
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(OFFICER) });
    await openTab('Gap analysis');
    await userEvent.click(await screen.findByRole('button', { name: 'Record status for P1' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/met by approved data in the Data Hub/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Status' }));
    expect(screen.queryByRole('option', { name: 'In progress' })).not.toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Not applicable' })).toBeInTheDocument();
  });

  it('frameworks: requirement hierarchy, metrics and mappings; content-less frameworks say so; activation sends the codes', async () => {
    let active;
    fakeApi(routes({ 'PUT /compliance/frameworks/active': (body) => { active = body; return ok(body.codes); } }));
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(OFFICER) });
    await openTab('Frameworks');
    const list = await screen.findByRole('list', { name: 'Frameworks' });
    await userEvent.click(within(list).getByText('IFRS S2 Climate-related Disclosures'));
    const reqs = await screen.findByRole('list', { name: 'ISSB_S2 requirements' });
    expect(within(reqs).getByText('scope1_tco2e')).toBeInTheDocument();
    expect(within(reqs).getByText('↔ GRI 305-1')).toBeInTheDocument();
    await userEvent.click(within(list).getByText('SEC Nigeria Sustainability Reporting Guidelines'));
    expect(await screen.findByText('No requirements loaded')).toBeInTheDocument();
    expect(screen.getByText('Content pending SME')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('We report against CBN_NSBP')).toBeChecked());
    await userEvent.click(screen.getByLabelText('We report against ISSB_S2'));
    await waitFor(() => expect(active).toEqual({ codes: ['CBN_NSBP', 'SEC_NG', 'ISSB_S2'] }));
  });

  it('read-only users can\'t change which frameworks they report against', async () => {
    fakeApi(routes());
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(READ) });
    await openTab('Frameworks');
    await screen.findByRole('list', { name: 'Frameworks' });
    expect(screen.queryByLabelText('We report against CBN_NSBP')).not.toBeInTheDocument();
  });

  it('cross-framework mapping shows what each source requirement answers', async () => {
    let sent;
    fakeApi(routes({
      'POST /compliance/map': (body) => {
        sent = body;
        return ok({ sourceFramework: 'GRI', targetFramework: 'ISSB_S2', year: YEAR, mappedRequirements: 1, metViaSource: 1,
          requirements: [{ code: 'S2-29(a)(i)(1)', title: 'Absolute gross Scope 1 GHG emissions', status: 'met', basis: 'data', satisfiedBy: null,
            sources: [{ framework: 'GRI', code: '305-1', title: 'Direct (Scope 1) GHG emissions', relation: 'equivalent', status: 'met' }] }] });
      },
    }));
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(READ) });
    await openTab('Cross-framework mapping');
    await userEvent.click(await screen.findByRole('button', { name: `Map ${YEAR}` }));
    expect(await screen.findByLabelText('Mapping summary')).toHaveTextContent('1 ISSB_S2 requirements map from GRI; 1 met through it');
    const table = screen.getByRole('table', { name: 'Mapped requirements' });
    expect(within(table).getByText('Equivalent')).toBeInTheDocument();
    expect(sent).toEqual({ sourceFramework: 'GRI', targetFramework: 'ISSB_S2', year: YEAR });
  });
});

describe('CompliancePage filings and regulatory change', () => {
  it('deadline: calendar and tracker; adding validates and posts the exact body', async () => {
    let created;
    fakeApi(routes({ 'POST /compliance/deadlines': (body) => { created = body; return [201, { success: true, data: { ...deadline, id: 'd2' } }]; } }));
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(OFFICER) });
    await openTab('Filing deadlines');
    const tracker = await screen.findByRole('table', { name: 'Filing tracker' });
    expect(within(tracker).getByText('CBN NSBP annual report')).toBeInTheDocument();
    expect(within(tracker).getByText('Reminded: 60d')).toBeInTheDocument();
    expect(screen.getByRole('grid', { name: /^Deadlines / })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add deadline' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add deadline' }));
    expect(await within(dialog).findByText('Description is required')).toBeInTheDocument();
    expect(within(dialog).getByText('Choose who owns this filing')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Description'), 'NGX sustainability report');
    await userEvent.type(within(dialog).getByLabelText('Due date'), `${YEAR + 1}-06-30`);
    await userEvent.type(within(dialog).getByLabelText('Reporting year'), String(YEAR));
    await choose(dialog, 'Owner', 'Kemi Compliance (compliance@esgpro.ng)');
    await userEvent.click(within(dialog).getByLabelText('90'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add deadline' }));
    await waitFor(() => expect(created).toEqual({
      framework: null, description: 'NGX sustainability report', deadlineDate: `${YEAR + 1}-06-30`, reportingYear: YEAR, ownerUserId: 'u-co',
      reminderDays: [60, 30, 14, 7, 1], notes: null,
    }));
  });

  it('mark submitted needs a reference; waive needs a reason; reminders can be run now', async () => {
    let submitted;
    let waived;
    fakeApi(routes({
      'PUT /compliance/deadlines/d1/submit': (body) => { submitted = body; return ok({ ...deadline, status: 'submitted' }); },
      'PUT /compliance/deadlines/d1/waive': (body) => { waived = body; return ok({ ...deadline, status: 'waived' }); },
      'POST /compliance/deadlines/reminders/run': () => ok({ date: '2026-10-01', tenants: 1, remindersSent: 2, overdueMarked: 1 }),
      'GET /files/storage-info': () => ok({ maxBytes: 1000 }),
    }));
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(OFFICER) });
    await openTab('Filing deadlines');
    await userEvent.click(await screen.findByRole('button', { name: 'Mark CBN NSBP annual report submitted' }));
    let dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Submitted on'), '2026-09-30');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark submitted' }));
    expect(await within(dialog).findByText('Reference is required')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Submission reference'), 'CBN/2026/014');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark submitted' }));
    await waitFor(() => expect(submitted).toEqual({ version: 4, submittedOn: '2026-09-30', submissionRef: 'CBN/2026/014', evidenceFileIds: [] }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Waive CBN NSBP annual report' }));
    dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Waive filing' }));
    expect(await within(dialog).findByText('Say why it is waived')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Exempted by CBN letter');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Waive filing' }));
    await waitFor(() => expect(waived).toEqual({ version: 4, reason: 'Exempted by CBN letter' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Send due reminders now' }));
    expect(await screen.findByText('Reminders sent: 2; marked overdue: 1.')).toBeInTheDocument();
  });

  it('regulatory change: record with owner, assess impact, sign off', async () => {
    let created;
    let assessed;
    fakeApi(routes({
      'POST /compliance/changes': (body) => { created = body; return [201, { success: true, data: { ...change, id: 'c2', status: 'new' } }]; },
      'PUT /compliance/changes/c1/assess': (body) => { assessed = body; return ok({ ...change, status: 'assessed' }); },
      'GET /compliance/changes': () => ok([change, { ...change, id: 'c3', title: 'NGX guidance update', status: 'assessed', impactLevel: 'low', impactAssessment: 'Template wording only', assessedBy: 'Adaeze Usman', version: 3 }]),
    }));
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(OFFICER) });
    await openTab('Regulatory changes');
    const assessedCard = await screen.findByLabelText('Change NGX guidance update');
    expect(within(assessedCard).getByText('Low impact')).toBeInTheDocument();
    expect(within(assessedCard).getByRole('button', { name: 'Sign off & close' })).toBeInTheDocument();
    expect(screen.getByText('No regulator feed is connected yet: record changes as they are published.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Record change' }));
    let dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Regulator'), 'SEC Nigeria');
    await userEvent.type(within(dialog).getByLabelText('Title'), 'New ESG templates');
    await userEvent.type(within(dialog).getByLabelText('Source link'), 'ftp://sec');
    await userEvent.type(within(dialog).getByLabelText('Published'), '2026-05-01');
    await userEvent.type(within(dialog).getByLabelText('Effective'), '2026-04-01');
    await choose(dialog, 'Owner', 'Adaeze Usman (adaeze.usman@esgpro.ng)');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record change' }));
    expect(await within(dialog).findByText('Must be an http(s) link')).toBeInTheDocument();
    expect(within(dialog).getByText('Must be on or after the published date')).toBeInTheDocument();
    await userEvent.clear(within(dialog).getByLabelText('Source link'));
    await userEvent.clear(within(dialog).getByLabelText('Effective'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record change' }));
    await waitFor(() => expect(created).toEqual({
      framework: null, regulator: 'SEC Nigeria', title: 'New ESG templates', summary: null, sourceUrl: null, publishedDate: '2026-05-01',
      effectiveDate: null, assessmentDue: null, ownerUserId: 'u-dir',
    }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    const card = screen.getByLabelText('Change ISSB adoption roadmap phase 2');
    await userEvent.click(within(card).getByRole('button', { name: 'Assess impact' }));
    dialog = await screen.findByRole('dialog');
    await choose(dialog, 'Impact', 'High');
    await userEvent.type(within(dialog).getByLabelText('Assessment'), 'Mandatory climate disclosures from FY2027');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save assessment' }));
    await waitFor(() => expect(assessed).toEqual({ version: 1, impactLevel: 'high', impactAssessment: 'Mandatory climate disclosures from FY2027', requiredActions: null }));
  });

  it('read-only users see filings and changes without actions', async () => {
    fakeApi(routes());
    renderWithStore(<CompliancePage />, { preloadedState: authenticated(READ) });
    await openTab('Filing deadlines');
    await screen.findByRole('table', { name: 'Filing tracker' });
    expect(screen.queryByRole('button', { name: 'Add deadline' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mark CBN NSBP annual report submitted' })).not.toBeInTheDocument();
    await openTab('Regulatory changes');
    await screen.findByLabelText('Change ISSB adoption roadmap phase 2');
    expect(screen.queryByRole('button', { name: 'Record change' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Assess impact' })).not.toBeInTheDocument();
  });
});

// Mirrors ComplianceDtos + service rules (ComplianceApiTest validation cases, round-trip spec `invalid` lists).
const fieldsOf = (schema, valid) => (v) => {
  const r = schema.safeParse({ ...valid, ...v });
  return r.success ? [] : r.error.issues.map((i) => i.path[0]);
};

describe('compliance schemas', () => {
  const deadlineOk = { ...emptyDeadline, description: 'x', deadlineDate: '2049-03-31', ownerUserId: 'u' };
  it.each([
    ['description', { description: '' }], ['description', { description: 'x'.repeat(501) }], ['deadlineDate', { deadlineDate: '' }],
    ['ownerUserId', { ownerUserId: '' }], ['reminderDays', { reminderDays: [0] }], ['reminderDays', { reminderDays: [400] }],
    ['reminderDays', { reminderDays: [7, 7] }], ['reportingYear', { reportingYear: '1999' }], ['notes', { notes: 'x'.repeat(1001) }],
  ])('deadline %s: %j', (field, v) => expect(fieldsOf(deadlineSchema, deadlineOk)(v)).toContain(field));
  it('deadline accepts the spec values', () => expect(fieldsOf(deadlineSchema, deadlineOk)({ reportingYear: '2049', reminderDays: [60, 30, 7] })).toEqual([]));

  const changeOk = { ...emptyChange, regulator: 'FRCN', title: 't', publishedDate: '2026-02-01', ownerUserId: 'u' };
  it.each([
    ['regulator', { regulator: '' }], ['regulator', { regulator: 'x'.repeat(101) }], ['title', { title: '' }], ['title', { title: 'x'.repeat(256) }],
    ['summary', { summary: 'x'.repeat(4001) }], ['sourceUrl', { sourceUrl: 'ftp://x' }], ['publishedDate', { publishedDate: '' }],
    ['effectiveDate', { effectiveDate: '2026-01-01' }], ['assessmentDue', { assessmentDue: '2026-01-01' }], ['ownerUserId', { ownerUserId: '' }],
  ])('change %s: %j', (field, v) => expect(fieldsOf(changeSchema, changeOk)(v)).toContain(field));
  it('change accepts the spec values', () => expect(fieldsOf(changeSchema, changeOk)({ sourceUrl: 'https://frcnigeria.gov.ng', effectiveDate: '2026-06-01' })).toEqual([]));

  it.each([
    ['impactLevel', { impactLevel: 'severe' }], ['impactAssessment', { impactAssessment: '' }], ['requiredActions', { requiredActions: 'x'.repeat(4001) }],
  ])('assessment %s: %j', (field, v) => expect(fieldsOf(assessSchema, { impactLevel: 'high', impactAssessment: 'x', requiredActions: '' })(v)).toContain(field));

  it.each([
    ['submittedOn', { submittedOn: '2999-01-01' }], ['submittedOn', { submittedOn: '' }], ['submissionRef', { submissionRef: '' }],
    ['submissionRef', { submissionRef: 'x'.repeat(101) }],
  ])('submission %s: %j', (field, v) => expect(fieldsOf(submissionSchema, { submittedOn: '2026-01-15', submissionRef: 'r', evidenceFileIds: [] })(v)).toContain(field));
  it('waiver needs a reason', () => expect(fieldsOf(waiverSchema, { reason: '' })({})).toContain('reason'));

  it.each([
    [false, 'evidenceFileIds', { status: 'complete' }], [false, 'notes', { status: 'not_applicable' }], [false, 'status', { status: 'done' }],
    [true, 'status', { status: 'in_progress' }], [false, 'notes', { notes: 'x'.repeat(2001) }],
  ])('attestation (quantitative %s) %s: %j', (quantitative, field, v) => {
    expect(fieldsOf(attestationSchema(quantitative), { status: 'not_started', notes: '', evidenceFileIds: [] })(v)).toContain(field);
  });
  it('attestation complete with evidence is valid', () => {
    expect(fieldsOf(attestationSchema(true), { status: 'complete', notes: '', evidenceFileIds: ['f'] })({})).toEqual([]);
  });
});
