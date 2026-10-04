// TRD §11.2.4: concurrent users submitting data points — P95 < 500 ms. Each iteration creates a draft and submits it
// (two requests). Every (site, metric, day) is unique, so no request is refused as a duplicate.
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { BASE, signIn } from './lib.js';

// RATE set: an open model at RATE submissions per second (each = create + submit), as many users as that needs.
// Otherwise a closed model: VUS users submitting back to back with no think time (a saturation test).
const VUS = Number(__ENV.VUS || 200);
const RATE = Number(__ENV.RATE || 0);
export const options = {
  scenarios: { submit: RATE
    ? { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: __ENV.HOLD || '60s', preAllocatedVUs: Math.max(50, RATE * 2), maxVUs: RATE * 10 }
    : { executor: 'ramping-vus', stages: [{ duration: '20s', target: VUS }, { duration: __ENV.HOLD || '60s', target: VUS }, { duration: '10s', target: 0 }] } },
  thresholds: { 'http_req_duration{step:create}': ['p(95)<500'], 'http_req_duration{step:submit}': ['p(95)<500'], http_req_failed: ['rate<0.01'] },
};
const METRICS = (__ENV.METRICS || 'diesel_litres,electricity_kwh,lpg_kg,natural_gas_m3,petrol_litres,water_consumption_m3,water_withdrawal_m3,waste_generated_t').split(',');

export function setup() {
  return signIn();
}

export default function (params) {
  // Unique across VUs within a run (iterationInTest) and across runs (OFFSET, set by perf.sh): no (site, metric, day)
  // is ever submitted twice, so every refusal would be a real error.
  const idx = Number(__ENV.OFFSET || 0) + exec.scenario.iterationInTest;
  const site = 1 + (idx % 50);
  const metric = METRICS[Math.floor(idx / 50) % METRICS.length];
  const perYear = 50 * METRICS.length * 365;
  const year = 2016 + (Math.floor(idx / perYear) % 5);
  const d = new Date(Date.UTC(year, 0, 1) + (Math.floor(idx / (50 * METRICS.length)) % 365) * 86400000).toISOString().slice(0, 10);
  const body = JSON.stringify({ orgId: `0c000000-0000-4000-8000-${String(site).padStart(12, '0')}`, metricKey: metric, numericValue: 10 + (idx % 90),
    reportingPeriodStart: d, reportingPeriodEnd: d });
  const r = http.post(`${BASE}/data/points`, body, { ...params, tags: { step: 'create' } });
  if (!check(r, { 'created 201': (x) => x.status === 201 })) return;
  const s = http.put(`${BASE}/data/points/${r.json('data.id')}/submit`, JSON.stringify({ version: r.json('data.version') }), { ...params, tags: { step: 'submit' } });
  check(s, { 'submitted 200': (x) => x.status === 200 });
}
