// TRD §11.2.4: report over 50,000 data points — PDF within 60 s. A report whose period holds ~50k approved points:
// saving its sections resolves every variable against the data, then the PDF is rendered (the draft preview; the final
// export renders the same way once approved).
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE, signIn } from './lib.js';

const total = new Trend('report_total_ms', true);
export const options = {
  scenarios: { report: { executor: 'per-vu-iterations', vus: 1, iterations: Number(__ENV.ITERATIONS || 3), maxDuration: '10m' } },
  thresholds: { report_total_ms: ['max<60000'], checks: ['rate==1'] },
};
export function setup() {
  return signIn();
}
// 13 days × 200 sites × 20 metrics ≈ 52,000 approved data points in the report period.
const YEAR = new Date().getFullYear();
const VARS = ['electricity_kwh', 'diesel_litres', 'natural_gas_m3', 'water_withdrawal_m3', 'waste_generated_t', 'lpg_kg', 'petrol_litres', 'energy_total_gj'];
export default function (params) {
  const template = http.get(`${BASE}/reports/templates`, params).json('data.0.id');
  const t0 = Date.now();
  const report = http.post(`${BASE}/reports`, JSON.stringify({ templateId: template, title: `Perf report ${__ITER}-${Date.now()}`,
    reportingPeriodStart: __ENV.REPORT_FROM || `${YEAR}-01-01`, reportingPeriodEnd: __ENV.REPORT_TO || `${YEAR}-01-13` }), params);
  check(report, { 'report created': (r) => r.status === 201 });
  const content = VARS.map((k) => `${k}: {{metric:${k}}}`).join('\n') + '\nEmissions {{carbon:total_location}}; completeness {{compliance:GRI:completeness}}';
  const saved = http.put(`${BASE}/reports/${report.json('data.id')}/sections`, JSON.stringify({ version: report.json('data.version'),
    sections: [{ title: 'Environmental data', type: 'data', content }] }), params);
  check(saved, { 'sections saved': (r) => r.status === 200 });
  const pdf = http.get(`${BASE}/reports/${report.json('data.id')}/preview`, { ...params, responseType: 'binary', timeout: '120s' });
  check(pdf, { 'pdf rendered': (r) => r.status === 200 && r.body.byteLength > 1000 });
  total.add(Date.now() - t0);
}
