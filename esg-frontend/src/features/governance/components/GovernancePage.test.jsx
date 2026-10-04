import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import GovernancePage from './GovernancePage';
import WhistleblowerPortal from './WhistleblowerPortal';
import MyPoliciesPage from './MyPoliciesPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { boardMemberSchema, caseUpdateSchema, emptyBoardMember, policySchema, emptyPolicy, submissionSchema } from '../schemas';

const member = {
  id: 'm1', name: 'Amaka Okoro', boardRole: 'independent_non_executive_director', independent: true, executive: false, gender: 'female',
  ageBand: '50_59', appointedOn: '2012-03-01', leftOn: null, tenureYears: 14.6, active: true, committees: ['audit'], skills: ['Finance'], notes: null, version: 2,
};
const composition = {
  asOf: '2026-10-02', directors: 6, executives: 1, nonExecutives: 5, independent: 3, independentPct: 50, female: 3, femalePct: 50,
  averageTenureYears: 5.2, byAgeBand: {}, committees: { audit: 3 }, skills: { Finance: 6, ESG: 4 }, companySecretary: true,
};
const policy = {
  id: 'p1', name: 'Anti-bribery policy', category: 'anti_bribery_corruption', policyVersion: '1.0', status: 'in_review', summary: 'No bribes',
  content: 'We do not offer or accept bribes.', ownerUserId: 'u1', ownerName: 'Kemi Compliance', reviewMonths: 12, nextReview: null, reviewDue: false,
  requiresAcknowledgement: true, acknowledged: 0, staff: 12, acknowledgedPct: null, submittedBy: 'Adaeze Usman', actions: ['send_back'],
  blockedActions: { approve: 'You submitted this policy — someone else must approve it' }, version: 3,
};
const DIRECTOR = ['governance:read', 'governance:create', 'governance:update', 'governance:approve'];

describe('Governance: board and code checks', () => {
  it('shows composition, the register and the skills matrix; adds a member with the server rules', async () => {
    let created;
    fakeApi({
      'GET /governance/board-members': () => ok([member]),
      'GET /governance/board-composition': () => ok(composition),
      'POST /governance/board-members': (body) => { created = body; return [201, { success: true, data: { ...member, ...body, id: 'm2' } }]; },
    });
    renderWithStore(<GovernancePage />, { preloadedState: authenticated(DIRECTOR) });
    const comp = await screen.findByLabelText('Board composition');
    expect(within(comp).getByText('6')).toBeInTheDocument();
    expect(within(comp).getAllByText('50%')).toHaveLength(2);
    expect(within(screen.getByRole('table', { name: 'Board members' })).getByText('Independent NED')).toBeInTheDocument();
    expect(screen.getByLabelText('Skills matrix')).toHaveTextContent('Finance: 6');

    await userEvent.click(screen.getByRole('button', { name: 'Add board member' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Tunde MD');
    await userEvent.click(within(dialog).getByLabelText('Board role'));
    await userEvent.click(await screen.findByRole('option', { name: 'MD / CEO' }));
    await userEvent.type(within(dialog).getByLabelText('Appointed on'), '2021-01-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add member' }));
    expect(await within(dialog).findByText('this role is executive')).toBeInTheDocument(); // MD/CEO must be executive
    expect(created).toBeUndefined();
    await userEvent.click(within(dialog).getByLabelText('Executive'));
    await userEvent.type(within(dialog).getByLabelText('Skills'), 'Finance, Operations');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add member' }));
    await waitFor(() => expect(created).toMatchObject({ name: 'Tunde MD', boardRole: 'md_ceo', executive: true, independent: false,
      appointedOn: '2021-01-01', skills: ['Finance', 'Operations'], committees: [], gender: null, leftOn: null }));
  });

  it('lists each code provision with its result and evidence, and filters by code', async () => {
    const calls = fakeApi({
      'GET /governance/code-check': () => ok({ asOf: '2026-10-02', passed: 1, failed: 1, insufficientData: 0, results: [
        { code: 'CAMA-275', ruleSet: 'CAMA_2020', provision: 's.275(1)', title: 'A public company has at least three independent directors',
          appliesTo: 'public_company', result: 'pass', evidence: '3 independent directors (minimum 3)', verificationStatus: 'pending_sme' },
        { code: 'NCCG-7', ruleSet: 'NCCG_2018', provision: 'Principle 7', title: 'Independent non-executive directors serve no more than nine years',
          appliesTo: 'public_company', result: 'fail', evidence: 'Over 9 years: Amaka Okoro (14.6 years)', verificationStatus: 'pending_sme' }] }),
    });
    renderWithStore(<GovernancePage />, { preloadedState: authenticated(DIRECTOR), route: '/governance?tab=code-check' });
    const table = await screen.findByRole('table', { name: 'Code check results' });
    expect(within(table).getByText('Meets')).toBeInTheDocument();
    expect(within(table).getByText('Does not meet')).toBeInTheDocument();
    expect(within(table).getByText('Over 9 years: Amaka Okoro (14.6 years)')).toBeInTheDocument();
    expect(screen.getByText(/pending ESG expert verification/)).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Code'));
    await userEvent.click(await screen.findByRole('option', { name: 'King IV' }));
    await waitFor(() => expect(calls.some((c) => c.key === 'GET /governance/code-check' && c.query.ruleSet === 'KING_IV')).toBe(true));
  });
});

describe('Governance: policies', () => {
  it('shows the lifecycle step the user may take and explains the one segregation of duties forbids', async () => {
    let sent;
    fakeApi({
      'GET /governance/policies': () => ok([policy]),
      'GET /governance/policies/p1': () => ok(policy),
      'PUT /governance/policies/p1/send_back': (body) => { sent = body; return ok({ ...policy, status: 'draft', actions: ['edit', 'submit'], blockedActions: {} }); },
    });
    renderWithStore(<GovernancePage />, { preloadedState: authenticated(DIRECTOR), route: '/governance?tab=policies&policy=p1' });
    const drawer = await screen.findByRole('region', { name: 'Policy details' });
    expect(within(drawer).getByLabelText('Approve: You submitted this policy — someone else must approve it')).toBeInTheDocument();
    await userEvent.click(within(drawer).getByRole('button', { name: 'Send back' }));
    const dialog = await screen.findByRole('dialog', { name: 'Send back' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send back' }));
    expect(await within(dialog).findByText('A reason is required')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Add the gifts threshold');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send back' }));
    await waitFor(() => expect(sent).toEqual({ version: 3, reason: 'Add the gifts threshold' }));
  });

  it('a colleague reads and acknowledges a published policy', async () => {
    let acked = false;
    fakeApi({
      'GET /governance/my-policies': () => ok([{ id: 'p1', name: 'Code of conduct', category: 'code_of_conduct', policyVersion: '2.0', summary: 'How we work',
        publishedAt: '2026-10-01T09:00:00Z', acknowledgedAt: acked ? '2026-10-02T09:00:00Z' : null }]),
      'POST /governance/my-policies/p1/acknowledge': () => { acked = true; return ok({}); },
    });
    renderWithStore(<MyPoliciesPage />, { preloadedState: authenticated(['notifications:read']) });
    expect(await screen.findByText('1 to read and acknowledge')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Read and acknowledge' }));
    await userEvent.click(await screen.findByRole('button', { name: 'I have read and acknowledge it' }));
    expect(await screen.findByText(/Acknowledged/)).toBeInTheDocument();
  });
});

describe('Governance: whistleblowing', () => {
  it('ethics officers open a case, move it on and leave notes; the public link is shown', async () => {
    let updated;
    const kase = {
      id: 'c1', caseReference: 'WB-2026-0001', category: 'bribery_corruption', status: 'received', description: 'A cash payment was requested.',
      assignedTo: null, assignedName: null, publicUpdate: null, resolution: null, receivedAt: '2026-10-02T08:00:00Z', notes: [], version: 0,
    };
    fakeApi({
      'GET /governance/whistleblower/channel': () => ok({ slug: 'esg-demo', path: '/whistleblower/esg-demo' }),
      'GET /governance/whistleblower/statistics': () => ok({ since: '2025-10-02', total: 1, open: 1, byStatus: { received: 1 }, byCategory: { bribery_corruption: 1 } }),
      'GET /governance/whistleblower/cases': () => ok([{ id: 'c1', caseReference: 'WB-2026-0001', category: 'bribery_corruption', status: 'received', receivedAt: '2026-10-02T08:00:00Z' }]),
      'GET /governance/whistleblower/cases/c1': () => ok(kase),
      'GET /governance/whistleblower/investigators': () => ok([{ id: 'e1', name: 'Funmi Ethics' }]),
      'PUT /governance/whistleblower/cases/c1': (body) => { updated = body; return ok({ ...kase, ...body, version: 1 }); },
    });
    renderWithStore(<GovernancePage />, {
      preloadedState: authenticated(['governance:read', 'whistleblower:read', 'whistleblower:update', 'whistleblower:read_summary']),
      route: '/governance?tab=whistleblowing',
    });
    expect(await screen.findByLabelText('Reporting link')).toHaveTextContent('/whistleblower/esg-demo');
    expect(await screen.findByLabelText('Case statistics')).toHaveTextContent('Bribery or corruption: 1');
    await userEvent.click(await screen.findByRole('button', { name: 'Open WB-2026-0001' }));
    const drawer = await screen.findByRole('region', { name: 'Case details' });
    expect(within(drawer).getByText('A cash payment was requested.')).toBeInTheDocument();
    await userEvent.click(within(drawer).getByLabelText('Status'));
    // Only the allowed next steps are offered.
    const options = await screen.findAllByRole('option');
    expect(options.map((o) => o.textContent)).toEqual(['Received', 'Being assessed', 'Dismissed']);
    await userEvent.click(screen.getByRole('option', { name: 'Dismissed' }));
    await userEvent.click(within(drawer).getByRole('button', { name: 'Save case' }));
    expect(await within(drawer).findByText('Record the outcome before resolving or dismissing the case')).toBeInTheDocument();
    await userEvent.type(within(drawer).getByLabelText('Outcome (confidential)'), 'Not a compliance matter');
    await userEvent.type(within(drawer).getByLabelText('Update for the reporter'), 'Reviewed; no further action.');
    await userEvent.click(within(drawer).getByRole('button', { name: 'Save case' }));
    await waitFor(() => expect(updated).toEqual({ version: 0, status: 'dismissed', assignedTo: null,
      publicUpdate: 'Reviewed; no further action.', resolution: 'Not a compliance matter' }));
  });

  it('a board member sees counts but no cases', async () => {
    fakeApi({
      'GET /governance/whistleblower/channel': () => ok({ slug: 'esg-demo', path: '/whistleblower/esg-demo' }),
      'GET /governance/whistleblower/statistics': () => ok({ since: '2025-10-02', total: 2, open: 1, byStatus: {}, byCategory: { fraud: 2 } }),
    });
    renderWithStore(<GovernancePage />, { preloadedState: authenticated(['governance:read', 'whistleblower:read_summary']), route: '/governance?tab=whistleblowing' });
    expect(await screen.findByLabelText('Case statistics')).toHaveTextContent('Fraud: 2');
    expect(screen.queryByRole('table', { name: 'Whistleblower cases' })).not.toBeInTheDocument();
  });

  it('the public portal takes an anonymous report, shows the reference and secret once, and checks status', async () => {
    let report;
    let check;
    fakeApi({
      'POST /public/whistleblower/esg-demo': (body) => { report = body; return [201, { success: true, data: { caseReference: 'WB-2026-0007', secret: 'ABCD-EFGH-JKLM-NPQR' } }]; },
      'POST /public/whistleblower/esg-demo/status': (body) => { check = body; return ok({ caseReference: 'WB-2026-0007', status: 'investigating', publicUpdate: 'Under way.', updatedAt: '2026-10-02T10:00:00Z' }); },
    });
    renderWithStore(<Routes><Route path="/whistleblower/:tenant" element={<WhistleblowerPortal />} /></Routes>, { route: '/whistleblower/esg-demo' });
    expect(screen.getByLabelText('Privacy')).toHaveTextContent("we don't record your name, email, IP address or device");
    await userEvent.click(screen.getByRole('button', { name: 'Send report' }));
    expect(await screen.findByText('Choose what it is about')).toBeInTheDocument();
    expect(report).toBeUndefined();
    await userEvent.click(screen.getByLabelText('What is it about?'));
    await userEvent.click(await screen.findByRole('option', { name: 'Fraud' }));
    await userEvent.type(screen.getByLabelText('What happened?'), 'Invoices were split to avoid the approval limit.');
    await userEvent.click(screen.getByRole('button', { name: 'Send report' }));
    expect(await screen.findByLabelText('Case reference')).toHaveTextContent('WB-2026-0007');
    expect(screen.getByLabelText('Secret')).toHaveTextContent('ABCD-EFGH-JKLM-NPQR');
    expect(report).toEqual({ category: 'fraud', description: 'Invoices were split to avoid the approval limit.' });

    await userEvent.click(screen.getByRole('tab', { name: 'Check a report' }));
    await userEvent.type(screen.getByLabelText('Case reference'), 'WB-2026-0007');
    await userEvent.type(screen.getByLabelText('Secret'), 'ABCD-EFGH-JKLM-NPQR');
    await userEvent.click(screen.getByRole('button', { name: 'Check status' }));
    expect(await screen.findByLabelText('Case status')).toHaveTextContent('Under investigation');
    expect(check).toEqual({ caseReference: 'WB-2026-0007', secret: 'ABCD-EFGH-JKLM-NPQR' });
  });
});

describe('Governance schemas mirror the server', () => {
  const fields = (schema, v) => (schema.safeParse(v).error?.issues ?? []).map((i) => i.path[0]);
  it('board member (BoardMemberRequest + BoardService.check)', () => {
    const ok = { ...emptyBoardMember, name: 'A', boardRole: 'chair', appointedOn: '2020-01-01' };
    expect(fields(boardMemberSchema, ok)).toEqual([]);
    expect(fields(boardMemberSchema, { ...ok, name: '' })).toEqual(['name']);
    expect(fields(boardMemberSchema, { ...ok, leftOn: '2019-01-01' })).toEqual(['leftOn']);
    expect(fields(boardMemberSchema, { ...ok, independent: true, executive: true })).toEqual(['independent']);
    expect(fields(boardMemberSchema, { ...ok, boardRole: 'independent_non_executive_director' })).toEqual(['boardRole']);
    expect(fields(boardMemberSchema, { ...ok, skills: Array.from({ length: 16 }, (_, i) => `s${i}`).join(',') })).toEqual(['skills']);
  });
  it('policy (PolicyRequest + PolicyService.check)', () => {
    const ok = { ...emptyPolicy, name: 'P', category: 'other', ownerUserId: 'u1', content: 'Text' };
    expect(fields(policySchema, ok)).toEqual([]);
    expect(fields(policySchema, { ...ok, content: '' })).toEqual(['content']);
    expect(fields(policySchema, { ...ok, content: '', fileId: 'f1' })).toEqual([]);
    expect(fields(policySchema, { ...ok, reviewMonths: '61' })).toEqual(['reviewMonths']);
    expect(fields(policySchema, { ...ok, policyVersion: '1 0' })).toEqual(['policyVersion']);
  });
  it('whistleblower (Submission, CaseUpdate)', () => {
    expect(fields(submissionSchema, { category: 'fraud', description: 'too short' })).toEqual(['description']);
    expect(fields(caseUpdateSchema('received'), { status: 'resolved', assignedTo: '', publicUpdate: '', resolution: 'x' })).toEqual(['status']);
    expect(fields(caseUpdateSchema('investigating'), { status: 'resolved', assignedTo: '', publicUpdate: '', resolution: '' })).toEqual(['resolution']);
  });
});
