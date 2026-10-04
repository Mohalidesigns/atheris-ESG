import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { authenticated, renderWithStore, userWith } from '../../test/renderWithStore';
import { fakeApi, ok } from '../../test/fakeApi';
import { db } from './offlineDb';
import {
  MAX_PER_REQUEST, applyResults, cacheList, cachedList, enqueue, forgetSession, lastProfile, outboxFor, rememberProfile, syncOutbox, toOperation,
} from './outbox';
import DataPointDialog from '../dataHub/components/DataPointDialog';
import OfflineTab from './components/OfflineTab';
import OfflineStatus from './components/OfflineStatus';
import AppRoutes from '../../routes/AppRoutes';

const user = userWith(['data_points:create', 'data_points:update', 'data_points:read', 'social:create']);
const metrics = [
  { metricKey: 'diesel_litres', pillar: 'E', category: 'Energy', name: 'Diesel consumed', unit: 'litre', dataType: 'decimal', description: '', requirements: [] },
];
const orgs = [{ id: 'site-1', name: 'Port Harcourt site', parentOrgId: null }];
const body = (value, day = '2026-09-01') => ({
  orgId: 'site-1', metricKey: 'diesel_litres', subCategory: null, numericValue: value, textValue: null, currency: null, sourceId: null,
  ownerUserId: null, evidenceFileIds: [], reportingPeriodStart: day, reportingPeriodEnd: day, notes: null,
});
const offline = () => vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);

beforeEach(async () => {
  await Promise.all([db.outbox.clear(), db.cache.clear(), db.profile.clear()]);
});

describe('outbox', () => {
  it('keeps saves per person and sends them as sync operations', async () => {
    const item = await enqueue(user, { kind: 'data_point', payload: body(12), label: 'Diesel' });
    await enqueue(userWith([], { id: 'someone-else' }), { kind: 'data_point', payload: body(99), label: 'Theirs' });
    const mine = await outboxFor(user);
    expect(mine).toHaveLength(1);
    expect(toOperation(mine[0])).toEqual({
      opId: item.opId, clientId: item.clientId, kind: 'data_point', capturedAt: item.capturedAt, recordId: null, baseVersion: null, dataPoint: body(12),
    });
    // Editing a record that is still waiting replaces its save (one operation per record), with a new operation id.
    const edited = await enqueue(user, { kind: 'data_point', payload: body(15), label: 'Diesel', clientId: item.clientId });
    const after = await outboxFor(user);
    expect(after).toHaveLength(1);
    expect(after[0].payload.numericValue).toBe(15);
    expect(edited.opId).not.toBe(item.opId);
  });

  it('100 offline points sync; applied ones leave the device, conflicts and refusals stay', async () => {
    for (let i = 0; i < 100; i += 1) await enqueue(user, { kind: 'data_point', payload: body(i), label: `P${i}` });
    const items = await outboxFor(user);
    const sent = [];
    const summary = await syncOutbox(user, 'web-test', async (request) => {
      sent.push(request);
      const results = request.operations.map((op, i) => ({
        opId: op.opId, clientId: op.clientId, outcome: i === 0 ? 'conflict' : i === 1 ? 'rejected' : 'applied',
        message: i === 0 ? 'A value for this metric and period was saved on the server' : i === 1 ? 'Some values need correcting' : 'Saved',
        errors: i === 1 ? { numericValue: 'must be at least 0' } : {}, syncId: `s${i}`,
      }));
      return { received: results.length, applied: 98, conflicts: 1, rejected: 1, results };
    });
    expect(sent).toHaveLength(1);
    expect(sent[0].deviceId).toBe('web-test');
    expect(sent[0].operations).toHaveLength(100);
    expect(summary).toMatchObject({ sent: 100, applied: 98, conflicts: 1, rejected: 1, offline: false });
    const left = await outboxFor(user);
    expect(left.map((o) => o.status).sort()).toEqual(['conflict', 'rejected']);
    expect(left.find((o) => o.status === 'rejected').errors).toEqual({ numericValue: 'must be at least 0' });
    expect(left.find((o) => o.status === 'conflict').syncId).toBe('s0');
    expect(left.map((o) => o.opId).sort()).toEqual([items[0].opId, items[1].opId].sort());
  });

  it('sends at most 500 per request and stops quietly while the server is unreachable', async () => {
    for (let i = 0; i < MAX_PER_REQUEST + 1; i += 1) await enqueue(user, { kind: 'data_point', payload: body(i), label: `P${i}` });
    const sizes = [];
    const unreachable = await syncOutbox(user, 'web-test', async (request) => {
      sizes.push(request.operations.length);
      throw { status: 'FETCH_ERROR', error: 'TypeError: Failed to fetch' };
    });
    expect(unreachable.offline).toBe(true);
    expect(sizes).toEqual([500]);
    expect(await outboxFor(user)).toHaveLength(501);

    const done = await syncOutbox(user, 'web-test', async (request) => {
      sizes.push(request.operations.length);
      return { received: request.operations.length, applied: request.operations.length, conflicts: 0, rejected: 0,
        results: request.operations.map((op) => ({ opId: op.opId, outcome: 'applied' })) };
    });
    expect(sizes.slice(1)).toEqual([500, 1]);
    expect(done.applied).toBe(501);
    expect(await outboxFor(user)).toHaveLength(0);
  });

  it('remembers who signed in and their lists until sign-out; waiting records survive sign-out', async () => {
    await rememberProfile({ ...user, accessToken: 'never-stored' });
    await cacheList(user, 'orgs', orgs);
    await enqueue(user, { kind: 'data_point', payload: body(1), label: 'Kept' });
    expect(await lastProfile()).toMatchObject({ id: 'u-1', tenantId: 't-1', permissions: user.permissions });
    expect(await lastProfile()).not.toHaveProperty('accessToken');
    expect(await cachedList(user, 'orgs')).toEqual(orgs);
    await forgetSession();
    expect(await lastProfile()).toBeNull();
    expect(await cachedList(user, 'orgs')).toEqual([]);
    expect(await outboxFor(user)).toHaveLength(1);
  });

  it('a result for an operation no longer on the device is ignored', async () => {
    await applyResults([{ opId: 'gone', outcome: 'conflict', message: 'x' }]);
    expect(await db.outbox.count()).toBe(0);
  });
});

describe('Data point form without a connection', () => {
  const fill = async () => {
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByLabelText('Metric'));
    await userEvent.click(screen.getByRole('option', { name: /Diesel consumed/ }));
    await userEvent.click(within(dialog).getByLabelText('Organisation'));
    await userEvent.click(screen.getByRole('option', { name: 'Port Harcourt site' }));
    await userEvent.type(within(dialog).getByLabelText(/^Value/), '420');
    await userEvent.type(within(dialog).getByLabelText('Period start'), '2026-09-01');
    await userEvent.type(within(dialog).getByLabelText('Period end'), '2026-09-01');
    return dialog;
  };
  const routes = (extra = {}) => ({
    'GET /data/rules': () => ok([]), 'GET /data/sources': () => ok([]), 'GET /data/owners': () => ok([]), ...extra,
  });

  it('offline: the same validation, then the record is kept on the device (no API call)', async () => {
    const calls = fakeApi(routes());
    offline();
    const onClose = vi.fn();
    renderWithStore(<DataPointDialog open metrics={metrics} orgs={orgs} onClose={onClose} />, { preloadedState: authenticated(user.permissions) });
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent("You're offline. This will be saved on this device");
    expect(within(dialog).queryByText('Evidence')).not.toBeInTheDocument();
    // Client rules apply offline exactly as online.
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));
    expect(await within(dialog).findByText('Choose a metric')).toBeInTheDocument();
    await fill();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(calls.some((c) => c.key === 'POST /data/points')).toBe(false);
    const [item] = await outboxFor(user);
    expect(item).toMatchObject({ kind: 'data_point', status: 'pending', label: 'Diesel consumed · Port Harcourt site · 2026-09-01' });
    expect(item.payload).toEqual({ ...body(420), evidenceFileIds: [] });
    expect(onClose.mock.calls[0][0].queued.opId).toBe(item.opId);
  });

  it('server unreachable mid-save: kept on the device instead of failing', async () => {
    fakeApi(routes({ 'POST /data/points': () => { throw new TypeError('Failed to fetch'); } }));
    const onClose = vi.fn();
    renderWithStore(<DataPointDialog open metrics={metrics} orgs={orgs} onClose={onClose} />, { preloadedState: authenticated(user.permissions) });
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(await outboxFor(user)).toHaveLength(1);
  });

  it('a record the server refused opens with its errors and is queued again when corrected', async () => {
    fakeApi(routes());
    offline();
    const item = await enqueue(user, { kind: 'data_point', payload: body(-5), label: 'Diesel' });
    await applyResults([{ opId: item.opId, outcome: 'rejected', message: 'Some values need correcting', errors: { numericValue: 'must be at least 0' } }]);
    const [refused] = await outboxFor(user);
    renderWithStore(<DataPointDialog open metrics={metrics} orgs={orgs} offlineItem={refused} onClose={() => {}} />,
      { preloadedState: authenticated(user.permissions) });
    const dialog = await screen.findByRole('dialog', { name: 'Correct record on this device' });
    expect(within(dialog).getByText('must be at least 0')).toBeInTheDocument();
    const valueField = within(dialog).getByLabelText(/^Value/);
    await userEvent.clear(valueField);
    await userEvent.type(valueField, '5');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));
    await waitFor(async () => expect((await outboxFor(user))[0].status).toBe('pending'));
    const [requeued] = await outboxFor(user);
    expect(requeued.payload.numericValue).toBe(5);
    expect(requeued.clientId).toBe(item.clientId);
    expect(requeued.opId).not.toBe(item.opId);
  });
});

describe('On this device', () => {
  const conflict = {
    id: 'c1', opId: 'op-c', clientId: 'cl-c', deviceId: 'web-a1b2', capturedAt: '2026-09-30T08:00:00Z', receivedAt: '2026-10-01T09:00:00Z',
    reason: 'A value for this metric and period was saved on the server on 30 Sep 2026 10:00, after you captured yours',
    mine: body(710), server: { id: 'p9', metricName: 'Diesel consumed', orgName: 'Port Harcourt site', numericValue: 700, unit: 'litre',
      validationStatus: 'draft', updatedAt: '2026-09-30T09:00:00Z', createdByName: 'Bola Analyst', version: 3 },
  };

  it('lists waiting records, shows conflicts side by side, and the person decides', async () => {
    await enqueue(user, { kind: 'data_point', payload: body(5), label: 'Diesel consumed · Port Harcourt site · 2026-09-01' });
    let resolved;
    fakeApi({
      'GET /data/sync/conflicts': () => ok(resolved ? [] : [conflict]),
      'POST /data/sync/conflicts/c1/resolve': (b) => { resolved = b; return ok({ opId: 'op-c', outcome: 'applied' }); },
    });
    renderWithStore(<OfflineTab metrics={metrics} orgs={orgs} />, { preloadedState: authenticated(user.permissions) });
    const table = await screen.findByRole('table', { name: 'Waiting on this device' });
    expect(within(table).getByText('Diesel consumed · Port Harcourt site · 2026-09-01')).toBeInTheDocument();
    expect(within(table).getByText('Waiting to sync')).toBeInTheDocument();
    const card = await screen.findByLabelText('Conflict: Diesel consumed');
    expect(card).toHaveTextContent('710 litre');
    expect(card).toHaveTextContent('700 litre');
    expect(card).toHaveTextContent('captured');
    expect(card).toHaveTextContent('web-a1b2');
    expect(card).toHaveTextContent('Bola Analyst');
    await userEvent.click(within(card).getByRole('button', { name: 'Use mine' }));
    await waitFor(() => expect(resolved).toEqual({ resolution: 'use_mine', version: 3 }));
    await waitFor(() => expect(screen.queryByLabelText('Conflict: Diesel consumed')).not.toBeInTheDocument());
  });

  it('without permission to edit, only "Keep server value" is possible; a record can be discarded', async () => {
    const item = await enqueue(user, { kind: 'data_point', payload: body(5), label: 'Diesel to drop' });
    fakeApi({ 'GET /data/sync/conflicts': () => ok([conflict]) });
    renderWithStore(<OfflineTab metrics={metrics} orgs={orgs} />, { preloadedState: authenticated(['data_points:create', 'data_points:read']) });
    // A collector-only account: a different user key, so their own outbox is empty.
    await screen.findByLabelText('Conflict: Diesel consumed');
    expect(screen.getByRole('button', { name: 'Use mine' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Keep server value' })).toBeEnabled();
    expect(await db.outbox.get(item.opId)).toBeTruthy();
  });

  it('discarding asks first', async () => {
    const item = await enqueue(user, { kind: 'data_point', payload: body(5), label: 'Diesel to drop' });
    fakeApi({ 'GET /data/sync/conflicts': () => ok([]) });
    renderWithStore(<OfflineTab metrics={metrics} orgs={orgs} />, { preloadedState: authenticated(user.permissions) });
    await userEvent.click(await screen.findByRole('button', { name: 'Discard Diesel to drop' }));
    const confirm = await screen.findByRole('dialog');
    expect(confirm).toHaveTextContent("It hasn't reached the server and will be deleted from this device.");
    await userEvent.click(within(confirm).getByRole('button', { name: /Discard|Confirm|Delete/ }));
    await waitFor(async () => expect(await db.outbox.get(item.opId)).toBeUndefined());
  });

  it('the top bar shows offline and what is waiting', async () => {
    await enqueue(user, { kind: 'data_point', payload: body(5), label: 'x' });
    offline();
    renderWithStore(<OfflineStatus />, { preloadedState: authenticated(user.permissions) });
    expect(await screen.findByLabelText('Offline records: Offline · 1 waiting')).toBeInTheDocument();
  });
});

describe('Opening the app without a connection', () => {
  it('opens offline capture for the person who signed in here last', async () => {
    await rememberProfile(user);
    await cacheList(user, 'metrics', metrics);
    await cacheList(user, 'orgs', orgs);
    fakeApi({ 'POST /auth/refresh': () => { throw new TypeError('Failed to fetch'); } });
    renderWithStore(<AppRoutes />, { preloadedState: { auth: { status: 'unknown', user: null, accessToken: null, mfa: null } } });
    expect(await screen.findByRole('heading', { name: 'Offline capture' })).toBeInTheDocument();
    expect(screen.getByText(/working as Ada Eze on this device/)).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Add data point' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Record an incident' })).toBeInTheDocument();
  });

  it('nobody remembered → the sign-in page; a refused session also forgets the device profile', async () => {
    fakeApi({ 'POST /auth/refresh': () => { throw new TypeError('Failed to fetch'); } });
    const first = renderWithStore(<AppRoutes />, { preloadedState: { auth: { status: 'unknown', user: null, accessToken: null, mfa: null } } });
    await waitFor(() => expect(first.store.getState().auth.status).toBe('anonymous'));
    first.unmount();

    await rememberProfile(user);
    fakeApi({ 'POST /auth/refresh': () => [401, { status: 401, title: 'Unauthorized' }] });
    const second = renderWithStore(<AppRoutes />, { preloadedState: { auth: { status: 'unknown', user: null, accessToken: null, mfa: null } } });
    await waitFor(() => expect(second.store.getState().auth.status).toBe('anonymous'));
    expect(await lastProfile()).toBeNull();
  });
});
