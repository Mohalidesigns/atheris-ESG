// TRD §11.2.4: emission calculation batch of 10,000 records — within 5 minutes. Re-runs factor selection and the
// emission arithmetic for every 2024 record (TRD POST /carbon/calculate).
import http from 'k6/http';
import { check } from 'k6';
import { BASE, signIn } from './lib.js';

export const options = {
  scenarios: { calculate: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '10m' } },
  thresholds: { http_req_duration: ['max<300000'], checks: ['rate==1'] },
};
export function setup() {
  return signIn();
}
export default function (params) {
  const r = http.post(`${BASE}/carbon/calculate`, JSON.stringify({ from: '2024-01-01', to: '2024-12-31' }), { ...params, timeout: '360s' });
  check(r, { 'recalculated 200': (x) => x.status === 200 });
  console.log(`recalculation: ${r.body}`);
}
