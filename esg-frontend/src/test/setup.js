import '@testing-library/jest-dom/vitest';
// WP-43: IndexedDB for the offline outbox (Dexie) in jsdom.
import 'fake-indexeddb/auto';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
