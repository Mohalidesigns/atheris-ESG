import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SocialPage from './SocialPage';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { grievanceSchema, emptyGrievance, incidentSchema, emptyIncident, projectSchema, emptyProject, snapshotSchema, emptySnapshot } from '../schemas';

const ORGS = [{ id: 'o1', name: 'Bonny terminal', parentOrgId: null }];
const metrics = {
  headcount: 40, female: 15, male: 25, undisclosed: 0, femalePct: 37.5, management: 8, womenInManagementPct: 37.5, senior: 8, womenInSeniorPct: 37.5,
  disabled: 2, disabledPct: 5, ageBands: { under_30: 32, '30_50': 3, over_50: 5 }, ageBandPct: {},
  levels: [{ level: 'senior_management', headcount: 8, female: 3, femalePct: 37.5, payGapPct: null, payGapNote: 'withheld: fewer than 5 women or men with pay data' },
    { level: 'non_management', headcount: 32, female: 12, femalePct: 37.5, payGapPct: 14.3, payGapNote: null }],
  payGapPct: 21.9, payGapNote: null, turnoverPct: 10, turnoverBasis: 'leavers ÷ closing headcount (no previous approved period)',
};
const snapshot = {
  id: 's1', orgId: 'o1', orgName: 'Bonny terminal', reportingPeriodStart: '2025-01-01', reportingPeriodEnd: '2025-12-31', hoursWorked: 96000,
  newHires: 6, leavers: 4, payCurrency: 'NGN', status: 'submitted', submittedByName: 'Adaeze Usman', lines: [], metrics, actions: ['return'],
  blockedActions: { approve: 'You submitted these figures, so someone else must approve them.' }, version: 2,
};
const stats = {
  year: 2026, hoursWorked: 400000, sitesWithHours: 1, lostTimeInjuries: 1, recordableCases: 2, fatalities: 0, lostDays: 3, ltifr: 2.5, trir: 1,
  excludedIncidents: 1, contractorRecordable: 1, nearMisses: 1, openInvestigations: 2,
  trend: [{ year: 2026, ltifr: 2.5, trir: 1 }], byMonth: [],
};
const overview = {
  year: 2026, workforce: { snapshots: 1, metrics, hoursWorked: 96000 }, safety: stats,
  community: { activeProjects: 2, completedProjects: 0, investmentBase: 2500000, baseCurrency: 'NGN', beneficiaries: 3000, piaHostCommunityProjects: 1 },
  grievances: { open: 3, overdue: 1, escalated: 1, receivedInYear: 4, resolvedInYear: 1, averageDaysToResolve: 6, openByCategory: {} },
};
const grievance = {
  id: 'g1', reference: 'GRV-2026-0007', orgId: 'o1', orgName: 'Bonny terminal', title: 'Dust from haulage trucks', category: 'environment', severity: 'low',
  status: 'resolved', receivedOn: '2026-09-01', dueOn: '2026-10-16', overdue: false, escalationLevel: 2, anonymous: false, complainantName: 'Chief Amadi',
  channel: 'community_meeting', description: 'Dust near the school', resolution: 'Water bowser twice a day', resolvedOn: '2026-10-01', resolvedByName: 'Esg Manager',
  updates: [{ action: 'received', at: '2026-09-01T09:00:00Z' }, { action: 'escalated_automatically', escalationLevel: 2, note: 'Not resolved by its due date', at: '2026-09-20T05:45:00Z' }],
  actions: ['close', 'reopen'], blockedActions: {}, version: 7,
};
const ALL = ['social:read', 'social:create', 'social:update', 'social:approve', 'org_structure:read', 'files:read', 'files:create'];

const base = (extra = {}) => fakeApi({
  'GET /social/overview': () => ok(overview),
  'GET /organisations': () => ok(ORGS),
  'GET /social/people': () => ok([{ id: 'u1', name: 'Esg Manager' }]),
  ...extra,
});

describe('Social Impact page', () => {
  it('shows the year at a glance and workforce figures, with segregation of duties on approval and small groups withheld', async () => {
    base({ 'GET /social/workforce': () => ok([snapshot]), 'GET /social/workforce/s1': () => ok(snapshot) });
    renderWithStore(<SocialPage />, { preloadedState: authenticated(ALL) });
    const glance = await screen.findByLabelText('Social overview');
    expect(within(glance).getByText('NGN 2,500,000')).toBeInTheDocument();
    expect(within(glance).getByText('2.50')).toBeInTheDocument();
    expect(within(glance).getByText('1 overdue · 1 escalated')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: 'Open Bonny terminal 2025-12-31' }));
    const drawer = await screen.findByRole('region', { name: 'Workforce figures' });
    expect(within(drawer).getByLabelText('Approve: You submitted these figures, so someone else must approve them.')).toBeInTheDocument();
    expect(within(drawer).getByRole('button', { name: 'Send back' })).toBeEnabled();
    expect(within(drawer).getByText('withheld: fewer than 5 women or men with pay data')).toBeInTheDocument();
    expect(within(drawer).getByText('14.3%')).toBeInTheDocument();
  });

  it('records an incident with the server classification rules and shows the rates and what they leave out', async () => {
    let created;
    base({
      'GET /social/workforce': () => ok([]),
      'GET /social/safety/statistics': () => ok(stats),
      'GET /social/incidents': () => ok([]),
      'POST /social/incidents': (body) => { created = body; return [201, { success: true, data: { ...body, id: 'i1', status: 'reported', actions: [], blockedActions: {}, version: 0 } }]; },
      'GET /social/incidents/i1': () => ok({ ...created, id: 'i1', orgName: 'Bonny terminal', status: 'reported', recordable: true, lostTime: true,
        actions: ['start_investigation'], blockedActions: {}, version: 0 }),
      'GET /files': () => ok([]),
      'GET /files/storage-info': () => ok({ storage: 'Local filesystem', maxBytes: 1000, allowedExtensions: 'pdf', virusScanning: 'Not configured' }),
    });
    renderWithStore(<SocialPage />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Health & safety' }));
    const rates = await screen.findByLabelText('Safety rates');
    expect(within(rates).getByText('2.50')).toBeInTheDocument();
    expect(within(rates).getByText('1.00')).toBeInTheDocument();
    expect(screen.getByText(/1 employee recordable case\(s\) happened at sites with no approved hours/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Record incident' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('Organisation'));
    await userEvent.click(await screen.findByRole('option', { name: 'Bonny terminal' }));
    await userEvent.type(within(dialog).getByLabelText('Title'), 'Hand caught in valve');
    await userEvent.click(within(dialog).getByLabelText('Type'));
    await userEvent.click(await screen.findByRole('option', { name: 'Injury' }));
    await userEvent.click(within(dialog).getByLabelText('Outcome'));
    await userEvent.click(await screen.findByRole('option', { name: 'Lost time' }));
    await userEvent.type(within(dialog).getByLabelText('Date and time'), '2026-03-01T09:30');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record incident' }));
    expect(await within(dialog).findByText('say who was hurt')).toBeInTheDocument();
    expect(within(dialog).getByText('a lost-time injury has at least one lost day')).toBeInTheDocument();
    expect(created).toBeUndefined();
    await userEvent.click(within(dialog).getByLabelText('Person affected'));
    await userEvent.click(await screen.findByRole('option', { name: 'Employee' }));
    await userEvent.type(within(dialog).getByLabelText('Days lost or restricted'), '3');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record incident' }));
    await waitFor(() => expect(created).toMatchObject({ orgId: 'o1', title: 'Hand caught in valve', incidentType: 'injury', outcome: 'lost_time',
      personType: 'employee', occurredAt: '2026-03-01T09:30', lostDays: 3, location: null }));
  });

  it('records an anonymous grievance without personal details and closes one with the complainant’s view', async () => {
    let created;
    let action;
    base({
      'GET /social/workforce': () => ok([]),
      'GET /social/grievances': () => ok([{ ...grievance, assignedToName: 'Esg Manager' }]),
      'GET /social/grievances/g1': () => ok(grievance),
      'GET /social/projects': () => ok([]),
      'POST /social/grievances': (body) => { created = body; return [201, { success: true, data: { ...grievance, id: 'g2' } }]; },
      'POST /social/grievances/g1/actions': (body) => { action = body; return ok({ ...grievance, status: 'closed', actions: ['reopen'] }); },
    });
    renderWithStore(<SocialPage />, { preloadedState: authenticated(ALL) });
    await userEvent.click(await screen.findByRole('tab', { name: 'Grievances' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Open GRV-2026-0007' }));
    const drawer = await screen.findByRole('region', { name: 'Grievance details' });
    expect(within(drawer).getByLabelText('Escalation level')).toHaveTextContent('level 2');
    expect(within(drawer).getByRole('table', { name: 'Grievance history' })).toHaveTextContent('escalated automatically (level 2)');
    expect(within(drawer).getByRole('table', { name: 'Grievance history' })).toHaveTextContent('System');
    await userEvent.click(within(drawer).getByRole('button', { name: 'Close grievance' }));
    const closing = await screen.findByRole('dialog');
    await userEvent.click(within(closing).getByLabelText('Is the complainant satisfied?'));
    await userEvent.click(await screen.findByRole('option', { name: 'No' }));
    await userEvent.click(within(closing).getByRole('button', { name: 'Close grievance' }));
    await waitFor(() => expect(action).toEqual({ action: 'close', version: 7, complainantSatisfied: 'no' }));
    await userEvent.click(screen.getByRole('button', { name: 'Close' }));

    await userEvent.click(screen.getByRole('button', { name: 'Record grievance' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('Complainant name')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByLabelText('Anonymous'));
    expect(within(dialog).queryByLabelText('Complainant name')).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByLabelText('Organisation'));
    await userEvent.click(await screen.findByRole('option', { name: 'Bonny terminal' }));
    for (const [label, option] of [['Channel', 'SMS'], ['Category', 'Land and compensation'], ['Severity', 'Critical']]) {
      await userEvent.click(within(dialog).getByLabelText(label));
      await userEvent.click(await screen.findByRole('option', { name: option }));
    }
    await userEvent.type(within(dialog).getByLabelText('Title'), 'Farmland flooded');
    await userEvent.type(within(dialog).getByLabelText('Description'), 'Drainage blocked by the new road');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record grievance' }));
    await waitFor(() => expect(created).toMatchObject({ orgId: 'o1', anonymous: true, complainantName: null, complainantContact: null, channel: 'sms',
      category: 'land_compensation', severity: 'critical', projectId: null, assignedTo: null }));
  });
});

describe('Social form rules mirror the server', () => {
  it('workforce lines: unique groups, disabled within headcount, a currency for pay', () => {
    const line = { level: 'non_management', gender: 'female', ageBand: '30_50', headcount: '3', disabledHeadcount: '4', totalAnnualPay: '' };
    const r = snapshotSchema.safeParse({ ...emptySnapshot, orgId: 'o1', reportingPeriodStart: '2025-01-01', reportingPeriodEnd: '2024-12-31', payCurrency: '',
      lines: [line, { ...line, disabledHeadcount: '0', totalAnnualPay: '100' }] });
    const paths = r.error.issues.map((i) => i.path.join('.'));
    expect(paths).toEqual(expect.arrayContaining(['reportingPeriodEnd', 'lines.1.level', 'lines.0.disabledHeadcount', 'payCurrency']));
  });

  it('incidents: only injuries have outcomes; days for lost time and restricted work only; not in the future', () => {
    const now = new Date('2026-06-01T12:00');
    const base = { ...emptyIncident, orgId: 'o1', title: 'x', occurredAt: '2026-05-01T08:00' };
    const issues = (v) => incidentSchema(now).safeParse({ ...base, ...v }).error?.issues.map((i) => i.path[0]) ?? [];
    expect(issues({ incidentType: 'near_miss', outcome: 'first_aid' })).toContain('outcome');
    expect(issues({ incidentType: 'injury', outcome: 'first_aid', personType: 'employee', lostDays: '2' })).toContain('lostDays');
    expect(issues({ incidentType: 'injury', outcome: 'restricted_work', personType: 'employee', lostDays: '2' })).toEqual([]);
    expect(issues({ incidentType: 'injury', outcome: 'first_aid', personType: 'employee', occurredAt: '2026-07-01T08:00' })).toContain('occurredAt');
  });

  it('projects need both coordinates or neither, and beneficiaries to complete; grievances keep anonymity', () => {
    const p = projectSchema.safeParse({ ...emptyProject, orgId: 'o1', name: 'x', category: 'health', latitude: '4.8', status: 'completed' });
    expect(p.error.issues.map((i) => i.path[0])).toEqual(expect.arrayContaining(['longitude', 'actualBeneficiaries']));
    const g = grievanceSchema('2026-06-01').safeParse({ ...emptyGrievance, orgId: 'o1', anonymous: true, complainantName: 'A', channel: 'sms',
      category: 'other', severity: 'low', title: 't', description: 'd', receivedOn: '2026-07-01' });
    expect(g.error.issues.map((i) => i.path[0])).toEqual(expect.arrayContaining(['anonymous', 'receivedOn']));
  });
});
