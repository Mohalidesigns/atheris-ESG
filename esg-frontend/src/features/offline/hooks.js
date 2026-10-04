import { useEffect, useState, useSyncExternalStore } from 'react';
import { liveQuery } from 'dexie';
import { useSelector } from 'react-redux';
import { db, userKey } from './offlineDb';

const subscribeOnline = (notify) => {
  window.addEventListener('online', notify);
  window.addEventListener('offline', notify);
  return () => {
    window.removeEventListener('online', notify);
    window.removeEventListener('offline', notify);
  };
};

/** The browser's view of the connection (false = certainly offline; true may still fail, so saves also fall back). */
export const useOnline = () => useSyncExternalStore(subscribeOnline, () => navigator.onLine, () => true);

/** This user's records waiting on the device, live from IndexedDB. */
export function useOutbox() {
  const key = userKey(useSelector((s) => s.auth.user));
  const [items, setItems] = useState({ key: null, list: [] });
  useEffect(() => {
    if (!key) return undefined;
    const sub = liveQuery(() => db.outbox.where('userKey').equals(key).sortBy('createdAt')).subscribe({
      next: (list) => setItems({ key, list }),
      error: () => setItems({ key, list: [] }),
    });
    return () => sub.unsubscribe();
  }, [key]);
  const list = items.key === key ? items.list : [];
  return {
    items: list,
    pending: list.filter((o) => o.status === 'pending').length,
    rejected: list.filter((o) => o.status === 'rejected').length,
    conflicts: list.filter((o) => o.status === 'conflict').length,
  };
}

/** Asks the sync agent (OfflineAgent) to send what is waiting now. */
export const requestSync = () => window.dispatchEvent(new Event('atheris:sync'));

/** Can this person capture anything offline? */
export const canCapture = (permissions = []) => permissions.includes('data_points:create') || permissions.includes('social:create');
