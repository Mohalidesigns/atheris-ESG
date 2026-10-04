import Dexie from 'dexie';

/**
 * WP-43 (D43): what the app keeps on this device, in IndexedDB.
 *  - outbox: records saved without a connection, one row per save, until the server has taken them;
 *  - cache: the reference lists the forms need offline (organisations, metrics), per user;
 *  - profile: the last signed-in user's name and permissions, so the app can open offline for capture only.
 * Sign-out clears cache and profile; unsynced records stay (they belong to the person who saved them and are only
 * shown and sent for that person).
 */
export const db = new Dexie('atheris-offline');
db.version(1).stores({
  outbox: '&opId, userKey, clientId, status, createdAt',
  cache: '&key',
  profile: '&id',
});

/** Whose records these are: one device may be shared by several people. */
export const userKey = (user) => (user?.id && user?.tenantId ? `${user.tenantId}:${user.id}` : null);

export const newId = () => (globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID()
  : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16);
    return (c === 'x' ? r : (r % 4) + 8).toString(16);
  }));
