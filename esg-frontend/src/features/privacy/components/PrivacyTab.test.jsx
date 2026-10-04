import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PrivacyTab from './PrivacyTab';
import PrivacyNoticeGate from './PrivacyNoticeGate';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';
import { noticeSchema, requestSchema, retentionSchema, toRequestBody } from '../schemas';

const DPO = ['privacy:read', 'privacy:manage'];
const notice = { id: 'n1', version: 2, title: 'How we use your data', body: 'We process account data.', dpoContact: 'dpo@example.org',
  publishedAt: '2026-10-01T09:00:00Z', publishedByName: 'Dayo DPO', acknowledged: 3, activeUsers: 12 };
const request = (extra = {}) => ({ id: 'q1', reference: 'DSR-2026-0001', kind: 'erasure', subjectUserId: 'u9', subjectName: 'Ngozi Leaver',
  subjectEmail: 'ngozi@example.org', receivedOn: '2026-09-01', dueOn: '2026-10-01', overdue: true, status: 'received', version: 0, ...extra });

describe('Privacy notice gate', () => {
  it('asks once per version and records the acknowledgement', async () => {
    let acknowledged;
    fakeApi({
      'GET /privacy/notice': () => ok({ notice, acknowledged: acknowledged != null, acknowledgedAt: null }),
      'POST /privacy/notice/acknowledge': (body) => { acknowledged = body; return ok({ notice, acknowledged: true }); },
    });
    renderWithStore(<PrivacyNoticeGate />, { preloadedState: authenticated([]) });
    const dialog = await screen.findByRole('dialog', { name: 'How we use your data' });
    expect(dialog).toHaveTextContent('Version 2, published 2026-10-01. Data protection officer: dpo@example.org.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'I have read this notice' }));
    await waitFor(() => expect(acknowledged).toEqual({ version: 2 }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('shows nothing when no notice is published', async () => {
    fakeApi({ 'GET /privacy/notice': () => ok({ notice: null, acknowledged: true }) });
    renderWithStore(<PrivacyNoticeGate />, { preloadedState: authenticated([]) });
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('Privacy (NDPA) tab', () => {
  const base = (extra = {}) => fakeApi({
    'GET /privacy/notices': () => ok([notice, { ...notice, id: 'n0', version: 1 }]),
    'GET /privacy/requests': () => ok([request()]),
    'GET /privacy/retention': () => ok({ notificationsDays: 365, deliveryLogDays: 90, sessionsDays: 30, accessLogIpDays: 180, version: 0 }),
    'GET /privacy/people': () => ok([{ id: 'u9', name: 'Ngozi Leaver', email: 'ngozi@example.org', active: true }]),
    ...extra,
  });

  it('shows how many people have read the notice and flags overdue requests', async () => {
    base();
    renderWithStore(<PrivacyTab />, { preloadedState: authenticated(DPO) });
    expect(await screen.findByLabelText('Acknowledgements')).toHaveTextContent('3 of 12 active users have read it.');
    const table = await screen.findByRole('table', { name: 'Data subject requests' });
    expect(within(table).getByText('Overdue')).toBeInTheDocument();
    // An erasure is completed by erasing, not by marking it complete.
    expect(within(table).queryByRole('button', { name: 'Complete' })).not.toBeInTheDocument();
  });

  it('erasure needs the reference typed to confirm', async () => {
    let erased;
    base({ 'POST /privacy/requests/q1/erase': (_b, req) => { erased = new URL(req.url).searchParams.get('version'); return ok(request({ status: 'completed' })); } });
    renderWithStore(<PrivacyTab />, { preloadedState: authenticated(DPO) });
    await userEvent.click(await screen.findByRole('button', { name: 'Erase data for DSR-2026-0001' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent("This can't be undone");
    const erase = within(dialog).getByRole('button', { name: 'Erase' });
    expect(erase).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText('Type DSR-2026-0001 to confirm'), 'DSR-2026-0001');
    await userEvent.click(erase);
    await waitFor(() => expect(erased).toBe('0'));
  });

  it('read-only privacy viewers see no actions', async () => {
    base();
    renderWithStore(<PrivacyTab />, { preloadedState: authenticated(['privacy:read']) });
    await screen.findByRole('table', { name: 'Data subject requests' });
    expect(screen.queryByRole('button', { name: /Erase|Record request|Publish/ })).not.toBeInTheDocument();
  });
});

describe('Privacy form rules mirror the server', () => {
  const issues = (schema, value) => schema.safeParse(value).error?.issues.map((i) => i.path.join('.')) ?? [];

  it('notices, requests and retention', () => {
    expect(issues(noticeSchema, { title: '', body: ' ', dpoContact: '' })).toEqual(['title', 'body', 'dpoContact']);
    const ok_ = { kind: 'access', subjectUserId: '', subjectName: 'Ngozi', subjectEmail: '', details: '', receivedOn: '2026-10-03' };
    const schema = requestSchema('2026-10-03');
    expect(issues(schema, ok_)).toEqual([]);
    expect(issues(schema, { ...ok_, receivedOn: '2026-10-04', subjectEmail: 'nope' })).toEqual(['subjectEmail', 'receivedOn']);
    expect(issues(schema, { ...ok_, kind: 'erasure' })).toEqual(['subjectUserId']);
    expect(toRequestBody({ ...ok_, subjectName: ' Ngozi ' })).toEqual({ kind: 'access', subjectUserId: null, subjectName: 'Ngozi', subjectEmail: null,
      details: null, receivedOn: '2026-10-03' });
    expect(issues(retentionSchema, { notificationsDays: '29', deliveryLogDays: '6', sessionsDays: '366', accessLogIpDays: '30' }))
      .toEqual(['notificationsDays', 'deliveryLogDays', 'sessionsDays']);
  });
});
