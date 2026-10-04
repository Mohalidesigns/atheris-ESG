// TRD §11.2.4: dashboard over 1M data points — < 3 s. Times the executive dashboard API (all cards the user may see).
import http from 'k6/http';
import { check } from 'k6';
import { BASE, signIn } from './lib.js';

export const options = {
  scenarios: { dashboard: { executor: 'constant-vus', vus: Number(__ENV.VUS || 10), duration: __ENV.HOLD || '30s' } },
  thresholds: { http_req_duration: ['p(95)<3000'], http_req_failed: ['rate<0.01'] },
};
export function setup() {
  return signIn();
}
export default function (params) {
  check(http.get(`${BASE}/dashboard/executive?granularity=${__ITER % 2 ? 'quarter' : 'year'}`, params), { 'dashboard 200': (r) => r.status === 200 });
  check(http.get(`${BASE}/data/points?page=0&size=50`, params), { 'data list 200': (r) => r.status === 200 });
}
