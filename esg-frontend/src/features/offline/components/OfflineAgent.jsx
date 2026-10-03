import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { toastShown } from '../../../store/uiSlice';
import { useListOrganisationsQuery } from '../../admin/adminApi';
import { useMetricsQuery } from '../../reference/referenceApi';
import { useSyncOfflineMutation } from '../offlineApi';
import { cacheList, deviceId, outboxFor, rememberProfile, syncOutbox } from '../outbox';
import { canCapture } from '../hooks';

const EVERY_MINUTE = 60_000;

const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

/**
 * WP-43 (D43): runs inside the signed-in app. It remembers who is signed in and keeps the lists the forms need, so the
 * app can open and take data offline; and it sends what was saved offline — at sign-in, when the connection returns,
 * every minute while something waits, and on request ("Sync now"). Renders nothing.
 */
export default function OfflineAgent() {
  const dispatch = useDispatch();
  const user = useSelector((s) => s.auth.user);
  const capture = canCapture(user?.permissions);
  const { data: orgs } = useListOrganisationsQuery(undefined, { skip: !capture });
  const { data: metrics } = useMetricsQuery(undefined, { skip: !user?.permissions?.includes('data_points:create') });
  const [send] = useSyncOfflineMutation();

  useEffect(() => {
    if (user && capture) rememberProfile(user).catch(() => {});
  }, [user, capture]);
  useEffect(() => {
    if (user && orgs) cacheList(user, 'orgs', orgs).catch(() => {});
  }, [user, orgs]);
  useEffect(() => {
    if (user && metrics) cacheList(user, 'metrics', metrics).catch(() => {});
  }, [user, metrics]);

  useEffect(() => {
    if (!user || !capture) return undefined;
    let stopped = false;
    const run = async (announce) => {
      try {
        if (!navigator.onLine) return;
        const waiting = (await outboxFor(user)).filter((o) => o.status === 'pending');
        if (!waiting.length) {
          if (announce) dispatch(toastShown({ message: 'Nothing waiting to sync' }));
          return;
        }
        const result = await syncOutbox(user, await deviceId(), (body) => send(body).unwrap());
        if (stopped) return;
        const parts = [];
        if (result.applied) parts.push(`${plural(result.applied, 'record', 'records')} synced`);
        if (result.conflicts) parts.push(`${plural(result.conflicts, 'needs', 'need')} your decision`);
        if (result.rejected) parts.push(`${plural(result.rejected, 'needs', 'need')} correcting`);
        if (parts.length) {
          dispatch(toastShown({ message: parts.join(' · '), severity: result.conflicts || result.rejected ? 'warning' : 'success' }));
        } else if (result.offline && announce) {
          dispatch(toastShown({ message: "Can't reach the server — your records stay on this device", severity: 'warning' }));
        }
      } catch {
        // IndexedDB unavailable (private mode): nothing was kept offline, so there is nothing to send.
      }
    };
    const onRequest = () => run(true);
    const onOnline = () => run(false);
    run(false);
    const timer = setInterval(() => run(false), EVERY_MINUTE);
    window.addEventListener('online', onOnline);
    window.addEventListener('atheris:sync', onRequest);
    return () => {
      stopped = true;
      clearInterval(timer);
      window.removeEventListener('online', onOnline);
      window.removeEventListener('atheris:sync', onRequest);
    };
  }, [user, capture, send, dispatch]);

  return null;
}
