import { db, newId, userKey } from './offlineDb';

/**
 * WP-43 outbox: saves made without a connection, sent to POST /data/sync later (D43).
 * status: pending (waiting to be sent) | rejected (the server refused it: needs correcting) | conflict (the server already
 * had a value: the person decides in Data Hub → On this device). Applied records are removed.
 */
export const KINDS = { data_point: 'Data point', safety_incident: 'Incident' };

/** Keeps a save on the device. An edit of a record still waiting replaces its waiting save (one operation per record). */
export async function enqueue(user, { kind, payload, label, clientId, recordId = null, baseVersion = null }) {
  const key = userKey(user);
  if (!key) throw new Error('Not signed in');
  const now = new Date().toISOString();
  return db.transaction('rw', db.outbox, async () => {
    if (clientId) {
      const waiting = await db.outbox.where('clientId').equals(clientId).filter((o) => o.userKey === key && o.status !== 'conflict').first();
      if (waiting) {
        // A new operation id: the earlier one may already be recorded on the server (as rejected, say).
        await db.outbox.delete(waiting.opId);
        const replaced = { ...waiting, opId: newId(), payload, label, capturedAt: now, status: 'pending', message: null, errors: {} };
        await db.outbox.add(replaced);
        return replaced;
      }
    }
    const item = {
      opId: newId(), clientId: clientId ?? newId(), userKey: key, kind, payload, label, recordId, baseVersion,
      capturedAt: now, createdAt: now, status: 'pending', message: null, errors: {}, syncId: null,
    };
    await db.outbox.add(item);
    return item;
  });
}

export async function outboxFor(user) {
  const key = userKey(user);
  if (!key) return [];
  return db.outbox.where('userKey').equals(key).sortBy('createdAt');
}

export const discard = (opId) => db.outbox.delete(opId);

/** The server's answer for each operation: applied ones leave the device; the rest wait for the person. */
export async function applyResults(results) {
  await db.transaction('rw', db.outbox, async () => {
    for (const r of results) {
      if (r.outcome === 'applied' || r.outcome === 'discarded') {
        await db.outbox.delete(r.opId);
      } else {
        await db.outbox.update(r.opId, { status: r.outcome, message: r.message ?? null, errors: r.errors ?? {}, syncId: r.syncId ?? null });
      }
    }
  });
}

/** Conflicts resolved elsewhere (another device, or the server list) no longer wait here. */
export async function forgetResolvedConflicts(user, openOpIds) {
  const open = new Set(openOpIds);
  const items = await outboxFor(user);
  await Promise.all(items.filter((o) => o.status === 'conflict' && !open.has(o.opId)).map((o) => db.outbox.delete(o.opId)));
}

/** The operation as POST /data/sync takes it. */
export const toOperation = (item) => ({
  opId: item.opId,
  clientId: item.clientId,
  kind: item.kind,
  capturedAt: item.capturedAt,
  recordId: item.recordId,
  baseVersion: item.baseVersion,
  ...(item.kind === 'data_point' ? { dataPoint: item.payload } : { incident: item.payload }),
});

export const MAX_PER_REQUEST = 500;

let running = null;

/**
 * Sends everything waiting for this user, 500 at a time. `send(body)` is the sync mutation (unwrapped). Stops quietly
 * when the server can't be reached. One run at a time per page.
 */
export function syncOutbox(user, deviceId, send) {
  if (!running) {
    running = (async () => {
      const summary = { sent: 0, applied: 0, conflicts: 0, rejected: 0, offline: false };
      const waiting = (await outboxFor(user)).filter((o) => o.status === 'pending');
      for (let i = 0; i < waiting.length; i += MAX_PER_REQUEST) {
        const chunk = waiting.slice(i, i + MAX_PER_REQUEST);
        let response;
        try {
          response = await send({ deviceId, operations: chunk.map(toOperation) });
        } catch (err) {
          summary.offline = err?.status === 'FETCH_ERROR';
          if (!summary.offline) summary.error = err;
          break;
        }
        await applyResults(response.results);
        summary.sent += response.received;
        summary.applied += response.applied;
        summary.conflicts += response.conflicts;
        summary.rejected += response.rejected;
      }
      return summary;
    })().finally(() => {
      running = null;
    });
  }
  return running;
}

/** A stable id for this browser on this device (shown with conflicts: "captured on …"). */
export async function deviceId() {
  const saved = await db.profile.get('device');
  if (saved) return saved.value;
  const id = `web-${newId().slice(0, 8)}`;
  await db.profile.put({ id: 'device', value: id });
  return id;
}

// ---------------------------------------------------------------- offline sign-in and reference lists

const PROFILE_FIELDS = ['id', 'tenantId', 'tenantName', 'email', 'firstName', 'lastName', 'roles', 'permissions', 'modules',
  'tenantWide', 'orgScopeIds'];

/** Remembers who is signed in, so the app can open for offline capture (no token is stored). */
export async function rememberProfile(user) {
  const summary = Object.fromEntries(PROFILE_FIELDS.filter((f) => user?.[f] !== undefined).map((f) => [f, user[f]]));
  await db.profile.put({ id: 'last', user: summary, savedAt: new Date().toISOString() });
}

export async function lastProfile() {
  return (await db.profile.get('last'))?.user ?? null;
}

export async function cacheList(user, name, value) {
  const key = userKey(user);
  if (key) await db.cache.put({ key: `${key}|${name}`, value, savedAt: new Date().toISOString() });
}

export async function cachedList(user, name) {
  const key = userKey(user);
  return key ? (await db.cache.get(`${key}|${name}`))?.value ?? [] : [];
}

/** Sign-out: forget who was here and the lists; records still waiting stay for their owner. */
export async function forgetSession() {
  await db.profile.delete('last');
  await db.cache.clear();
}
