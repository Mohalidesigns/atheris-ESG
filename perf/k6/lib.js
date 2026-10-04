import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE = `${__ENV.BASE_URL || 'http://localhost:18180'}/api/v1`;
export const PERF_USER = __ENV.PERF_USER || 'amina.bello@esgpro.ng';

/** Signs in once (seeded local demo user; dev-only password from the environment) and returns request params. */
export function signIn() {
  const r = http.post(`${BASE}/auth/login`, JSON.stringify({ email: PERF_USER, password: __ENV.PERF_PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
  });
  if (!check(r, { 'signed in': (x) => x.status === 200 && x.json('data.accessToken') })) fail(`login failed: ${r.status} ${r.body}`);
  return { headers: { Authorization: `Bearer ${r.json('data.accessToken')}`, 'Content-Type': 'application/json' } };
}
