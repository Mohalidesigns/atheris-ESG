import { enqueue } from './outbox';

/**
 * WP-43: save online when possible, otherwise keep it on the device. `send()` is the normal API call; when the browser
 * knows it is offline, or the server can't be reached, the record goes to the outbox instead and the caller gets
 * { queued }. Anything else (validation, permissions) is thrown as usual so the form shows it.
 */
export async function saveOrQueue(send, user, item) {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return { queued: await enqueue(user, item) };
  }
  try {
    return { saved: await send() };
  } catch (err) {
    if (err?.status === 'FETCH_ERROR') return { queued: await enqueue(user, item) };
    throw err;
  }
}

export const QUEUED_MESSAGE = "Saved on this device. It will be sent when you're back online.";
