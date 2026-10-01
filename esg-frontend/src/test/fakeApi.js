import { vi } from 'vitest';

/**
 * Stubs global fetch with route handlers: { 'POST /auth/login': (body, request) => [status, json] }.
 * Paths are relative to /api/v1. Unmatched requests fail the test loudly. Returns the list of calls made.
 */
export function fakeApi(routes) {
  const calls = [];
  vi.stubGlobal('fetch', vi.fn(async (input) => {
    const request = input instanceof Request ? input : new Request(input);
    const url = new URL(request.url);
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const key = `${request.method} ${path}`;
    const text = await request.text();
    const body = text ? JSON.parse(text) : undefined;
    calls.push({ key, body, query: Object.fromEntries(url.searchParams), headers: request.headers });
    const handler = routes[key];
    if (!handler) throw new Error(`Unexpected request ${key}`);
    const [status, json] = await handler(body, request);
    return new Response(JSON.stringify(json), { status, headers: { 'Content-Type': 'application/json' } });
  }));
  return calls;
}

export const ok = (data) => [200, { success: true, data }];
