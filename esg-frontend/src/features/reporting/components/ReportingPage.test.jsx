import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ReportingPage from './ReportingPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { decisionSchema, detailsSchema, diffLines, reportSchema, sectionsSchema, unknownVariables } from '../schemas';

const template = {
  id: 't-gri', key: 'gri_core', name: 'GRI sustainability report', frameworkCode: 'GRI', description: 'Annual report on GRI', contentStatus: 'pending_sme', platform: true,
  sections: [{ title: 'About this report', type: 'narrative', content: 'Describe…' }, { title: 'Energy', type: 'data', content: 'Electricity consumed: {{metric:electricity_kwh}}' }],
};
const snapshot = {
  'metric:electricity_kwh': { display: '1,750 kWh', value: 1750, unit: 'kWh', status: 'ok', basis: '3 approved data points' },
  'metric:water_withdrawal_m3': { display: '[not reported]', status: 'missing', basis: 'no approved data in the period' },
  'carbon:scope1': { display: '2.68 tCO2e', value: 2.68, unit: 'tCO2e', status: 'ok', basis: '1 emission record',
    provisional: '1 of 1 record (2.68 tCO2e) use emission factors pending expert review' },
};
const report = (over = {}) => ({
  id: 'r1', title: 'Sustainability report 2026', description: null, frameworkCode: 'GRI', templateId: 't-gri', templateName: 'GRI sustainability report',
  status: 'draft', versionLabel: 'v0.2', reportingPeriodStart: '2026-01-01', reportingPeriodEnd: '2026-12-31',
  sections: [
    { id: 's1', title: 'About this report', type: 'narrative', content: 'We are a bank.', rendered: 'We are a bank.', sortOrder: 1 },
    { id: 's2', title: 'Energy', type: 'data', content: 'Electricity consumed: {{metric:electricity_kwh}}', rendered: 'Electricity consumed: 1,750 kWh', sortOrder: 2 },
  ],
  snapshot, snapshotAt: '2026-10-01T09:00:00Z', approval: null,
  versions: [{ id: 'v2', label: 'v0.2', event: 'saved', createdBy: 'Amina Bello', createdAt: '2026-10-01T09:05:00Z' },
    { id: 'v1', label: 'v0.1', event: 'created', createdBy: 'Amina Bello', createdAt: '2026-10-01T09:00:00Z' }],
  exports: [], createdBy: 'Amina Bello', createdAt: '2026-10-01T09:00:00Z', version: 3, ...over,
});
const inReview = report({
  status: 'in_review', versionLabel: 'v0.2',
  approval: { workflowId: 'w1', status: 'in_progress', currentStep: 1, totalSteps: 3, steps: [
    { id: 'st1', stepNumber: 1, label: 'ESG Director review', role: 'ESG_DIRECTOR', status: 'pending', dueAt: '2026-10-06T09:00:00Z', activatedAt: '2026-10-01T09:00:00Z', overdue: false, canAct: true },
    { id: 'st2', stepNumber: 2, label: 'Compliance review', role: 'COMPLIANCE_OFFICER', status: 'pending', overdue: false, canAct: false },
    { id: 'st3', stepNumber: 3, label: 'Board sign-off', role: 'BOARD_MEMBER', status: 'pending', overdue: false, canAct: false },
  ] },
});
const approved = report({
  status: 'approved', versionLabel: 'v1.0', versions: [{ id: 'v3', label: 'v1.0', event: 'approved', createdBy: 'Board Member', createdAt: '2026-10-02T09:00:00Z' }, ...report().versions],
});
const metrics = [{ metricKey: 'electricity_kwh', name: 'Electricity consumed', unit: 'kWh' }, { metricKey: 'water_withdrawal_m3', name: 'Water withdrawn', unit: 'm3' }];
const routes = (r, extra = {}) => ({
  'GET /reports': () => ok([{ id: 'r1', title: 'Sustainability report 2026', frameworkCode: 'GRI', templateName: 'GRI sustainability report', status: r.status,
    versionLabel: r.versionLabel, reportingPeriodStart: '2026-01-01', reportingPeriodEnd: '2026-12-31', nextApproval: r.status === 'in_review' ? 'ESG Director review' : null,
    createdBy: 'Amina Bello', updatedAt: '2026-10-01T09:00:00Z', version: r.version }]),
  'GET /reports/templates': () => ok([template]),
  'GET /reports/r1': () => ok(r),
  'GET /reference/metrics': () => ok(metrics),
  'GET /compliance/frameworks': () => ok([{ code: 'GRI', name: 'GRI', requirementCount: 118 }]),
  ...extra,
});
const MANAGER = ['reports:read', 'reports:create', 'reports:update', 'reports:export', 'reference_data:read', 'compliance:read', 'files:read'];

const choose = async (container, label, option) => {
  await userEvent.click(within(container).getByRole('combobox', { name: label }));
  await userEvent.click(await screen.findByRole('option', { name: option }));
};

describe('ReportingPage', () => {
  it('lists reports and creates one from a template with the exact body; the new report opens', async () => {
    let created;
    fakeApi(routes(report(), { 'POST /reports': (body) => { created = body; return [201, { success: true, data: report() }]; } }));
    renderWithStore(<ReportingPage />, { preloadedState: authenticated(MANAGER) });
    const table = await screen.findByRole('table', { name: 'Reports' });
    expect(within(table).getByText('Sustainability report 2026')).toBeInTheDocument();
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New report' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create report' }));
    expect(await within(dialog).findByText('Choose a template')).toBeInTheDocument();
    await choose(dialog, 'Template', 'GRI sustainability report (GRI)');
    expect(within(dialog).getByLabelText('Template preview')).toHaveTextContent('2. EnergyFilled from approved data');
    await userEvent.type(within(dialog).getByLabelText('Report title'), 'Sustainability report 2026');
    await userEvent.type(within(dialog).getByLabelText('Reporting period start'), '2026-12-31');
    await userEvent.type(within(dialog).getByLabelText('Reporting period end'), '2026-01-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create report' }));
    expect(await within(dialog).findByText('Must be on or after the start date')).toBeInTheDocument();
    await userEvent.clear(within(dialog).getByLabelText('Reporting period start'));
    await userEvent.type(within(dialog).getByLabelText('Reporting period start'), '2026-01-01');
    await userEvent.clear(within(dialog).getByLabelText('Reporting period end'));
    await userEvent.type(within(dialog).getByLabelText('Reporting period end'), '2026-12-31');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create report' }));
    await waitFor(() => expect(created).toEqual({
      templateId: 't-gri', title: 'Sustainability report 2026', description: null, reportingPeriodStart: '2026-01-01', reportingPeriodEnd: '2026-12-31',
    }));
    expect(await screen.findByLabelText('Report status')).toHaveTextContent('Draft');
  }, 15000); // many steps (template preview, form, create, open): slow under the full parallel run

  it('draft editor: unknown variables are refused, data can be inserted, sections save with their ids', async () => {
    let saved;
    fakeApi(routes(report(), { 'PUT /reports/r1/sections': (body) => { saved = body; return ok(report({ version: 4, versionLabel: 'v0.3' })); } }));
    renderWithStore(<ReportingPage />, { preloadedState: authenticated(MANAGER), route: '/?report=r1' });
    const second = await screen.findByLabelText('Section 2');
    const text = within(second).getByLabelText('Text');
    await userEvent.clear(text);
    await userEvent.type(text, 'Unicorns: {{{{metric:unicorns}}');
    await userEvent.click(screen.getByRole('button', { name: 'Save sections' }));
    expect(await within(second).findByText('Unknown variable: {{metric:unicorns}}')).toBeInTheDocument();
    await userEvent.clear(text);
    await userEvent.type(text, 'Water: ');
    await userEvent.click(within(second).getByRole('button', { name: 'Insert data' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Water withdrawn' }));
    expect(text).toHaveValue('Water: {{metric:water_withdrawal_m3}}');
    await userEvent.click(screen.getByRole('button', { name: 'Save sections' }));
    await waitFor(() => expect(saved).toEqual({
      version: 3, sections: [
        { id: 's1', title: 'About this report', type: 'narrative', content: 'We are a bank.' },
        { id: 's2', title: 'Energy', type: 'data', content: 'Water: {{metric:water_withdrawal_m3}}' },
      ],
    }));
  });

  it('data tab shows the snapshot with what is missing; refresh and submit send the version', async () => {
    let refreshed;
    let submitted;
    fakeApi(routes(report(), {
      'POST /reports/r1/refresh': (body) => { refreshed = body; return ok(report({ version: 4 })); },
      'POST /reports/r1/approve': (body) => { submitted = body; return ok(inReview); },
    }));
    renderWithStore(<ReportingPage />, { preloadedState: authenticated(MANAGER), route: '/?report=r1' });
    await userEvent.click(await screen.findByRole('tab', { name: 'Data' }));
    const table = screen.getByRole('table', { name: 'Data snapshot' });
    expect(within(table).getByText('1,750 kWh')).toBeInTheDocument();
    expect(within(table).getByText('Not reported')).toBeInTheDocument();
    // S7: values resting on factors pending expert review are listed and marked.
    expect(screen.getByLabelText('Provisional values')).toHaveTextContent('carbon:scope1');
    expect(within(table).getByText('Provisional')).toBeInTheDocument();
    expect(within(table).getByText(/use emission factors pending expert review/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Refresh data' }));
    await waitFor(() => expect(refreshed).toEqual({ version: 3 }));
    await userEvent.click(screen.getByRole('button', { name: 'Submit for approval' }));
    await waitFor(() => expect(submitted).toEqual({ version: 3 }));
  });

  it('approval: the current approver decides; sending back needs comments', async () => {
    let decided;
    fakeApi(routes(inReview, { 'PUT /reports/r1/approval-steps/st1': (body) => { decided = body; return ok(inReview); } }));
    renderWithStore(<ReportingPage />, { preloadedState: authenticated([...MANAGER, 'reports:approve']), route: '/?report=r1' });
    expect(await screen.findByText(/Awaiting approval/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save sections' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Approval' }));
    const panel = screen.getByLabelText('Approval');
    expect(panel).toHaveTextContent('step 1 of 3');
    await userEvent.click(within(panel).getByRole('button', { name: 'Request changes' }));
    expect(await within(panel).findByText('Say what needs to change')).toBeInTheDocument();
    expect(decided).toBeUndefined();
    await userEvent.type(within(panel).getByLabelText('Comments'), 'Figures checked');
    await userEvent.click(within(panel).getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(decided).toEqual({ version: 3, action: 'approve', comments: 'Figures checked' }));
  });

  it('approved: export generates from the approved version; reopen starts a revision; versions compare', async () => {
    let generated;
    let reopened;
    const exported = { ...approved, exports: [{ id: 'j1', versionLabel: 'v1.0', format: 'pdf', status: 'done', fileId: 'f1', createdBy: 'Amina Bello', createdAt: '2026-10-02T10:00:00Z' }] };
    fakeApi(routes(approved, {
      'POST /reports/r1/generate': (body) => { generated = body; return [201, { success: true, data: exported.exports[0] }]; },
      'POST /reports/r1/reopen': (body) => { reopened = body; return ok(report({ versionLabel: 'v1.1' })); },
      'GET /reports/r1/versions/v1': () => ok({ id: 'v1', label: 'v0.1', event: 'created', sections: [{ title: 'Energy', rendered: 'Electricity consumed: 1,500 kWh' }], snapshot: {} }),
      'GET /reports/r1/versions/v3': () => ok({ id: 'v3', label: 'v1.0', event: 'approved', sections: [{ title: 'Energy', rendered: 'Electricity consumed: 1,750 kWh' }], snapshot: {} }),
    }));
    renderWithStore(<ReportingPage />, { preloadedState: authenticated(MANAGER), route: '/?report=r1' });
    expect(await screen.findByLabelText('Report version')).toHaveTextContent('v1.0');
    expect(screen.getByRole('heading', { name: 'Energy' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Export PDF' }));
    await waitFor(() => expect(generated).toEqual({ format: 'pdf' }));
    expect(await screen.findByText('PDF generated from the approved version.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: 'Versions' }));
    await userEvent.click(screen.getByLabelText('Compare v1.0 approved'));
    await userEvent.click(screen.getByLabelText('Compare v0.1 created'));
    await userEvent.click(screen.getByRole('button', { name: 'Compare selected' }));
    const cmp = await screen.findByLabelText('Version comparison');
    expect(cmp).toHaveTextContent('v0.1 → v1.0');
    expect(within(cmp).getByText('− Electricity consumed: 1,500 kWh')).toBeInTheDocument();
    expect(within(cmp).getByText('+ Electricity consumed: 1,750 kWh')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Reopen for revision' }));
    await waitFor(() => expect(reopened).toEqual({ version: 3 }));
  });

  it('read-only users see the report without editing or approval actions', async () => {
    fakeApi(routes(inReview));
    renderWithStore(<ReportingPage />, { preloadedState: authenticated(['reports:read']), route: '/?report=r1' });
    expect(await screen.findByLabelText('Section Energy')).toHaveTextContent('Electricity consumed: 1,750 kWh');
    expect(screen.queryByRole('button', { name: 'Submit for approval' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Export PDF' })).not.toBeInTheDocument();
  });
});

// Mirrors ReportingDtos + ReportService / VariableEngine rules (ReportingApiTest; report round-trip spec).
describe('reporting schemas', () => {
  const ctx = { metricKeys: ['electricity_kwh'], frameworks: ['GRI'] };
  it('knows the same variables as the server', () => {
    expect(unknownVariables('{{metric:electricity_kwh}} {{carbon:scope1}} {{compliance:GRI:met}} {{report:period}} {{tenant:name}}', ctx)).toEqual([]);
    expect(unknownVariables('{{metric:unicorns}} {{carbon:scope9}} {{compliance:NOPE:met}} {{compliance:GRI:score}} {{report:author}}', ctx))
      .toEqual(['metric:unicorns', 'carbon:scope9', 'compliance:NOPE:met', 'compliance:GRI:score', 'report:author']);
  });
  const valid = { templateId: 't', title: 't', description: '', reportingPeriodStart: '2026-01-02', reportingPeriodEnd: '2026-12-31' };
  const fields = (schema, v) => {
    const r = schema.safeParse(v);
    return r.success ? [] : r.error.issues.map((i) => i.path.join('.'));
  };
  it.each([
    ['templateId', { templateId: '' }], ['title', { title: '' }], ['title', { title: 'x'.repeat(501) }], ['description', { description: 'x'.repeat(1001) }],
    ['reportingPeriodStart', { reportingPeriodStart: '' }], ['reportingPeriodEnd', { reportingPeriodEnd: '' }], ['reportingPeriodEnd', { reportingPeriodEnd: '2026-01-01' }],
  ])('report %s: %j', (field, v) => expect(fields(reportSchema, { ...valid, ...v })).toContain(field));
  it('report details don\'t need a template; the same day is a valid period', () => {
    expect(fields(detailsSchema, { ...valid, templateId: undefined, reportingPeriodEnd: '2026-01-02' })).toEqual([]);
  });
  it.each([
    ['sections.0.title', { title: '' }], ['sections.0.content', { content: 'x'.repeat(20001) }], ['sections.0.content', { content: '{{metric:nope}}' }],
    ['sections.0.type', { type: 'chart' }],
  ])('section %s: %j', (field, v) => {
    expect(fields(sectionsSchema(ctx), { sections: [{ title: 'A', type: 'data', content: '', ...v }] })).toContain(field);
  });
  it('a report needs at least one section', () => expect(fields(sectionsSchema(ctx), { sections: [] })).toContain('sections'));
  it('sending back needs comments', () => {
    expect(fields(decisionSchema, { action: 'request_changes', comments: '' })).toContain('comments');
    expect(fields(decisionSchema, { action: 'approve', comments: '' })).toEqual([]);
  });
  it('diffs lines', () => {
    expect(diffLines('a\nb\nc', 'a\nc\nd')).toEqual([
      { type: 'same', text: 'a' }, { type: 'removed', text: 'b' }, { type: 'same', text: 'c' }, { type: 'added', text: 'd' },
    ]);
  });
});
