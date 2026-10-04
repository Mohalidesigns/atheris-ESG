/**
 * One place for turning codes into labels (Phase 2): forms and tables use the same words. Catalogue data (metrics,
 * activities, frameworks) carries its own names from the server; these helpers cover enum codes and fall-backs.
 */

// no-mock-gate: config — spelling of acronyms and local fuel names in generated labels
const WORDS = {
  lpg: 'LPG', pms: '(PMS)', ago: '(AGO)', dpk: '(DPK)', hfo: 'HFO', lpfo: 'LPFO', esg: 'ESG', iot: 'IoT', sms: 'SMS', ussd: 'USSD',
  csv: 'CSV', api: 'API', erp: 'ERP', pcaf: 'PCAF', rec: 'REC', ppa: 'PPA', ghg: 'GHG', co2: 'CO2', co2e: 'CO2e', kwh: 'kWh',
  mfa: 'MFA', sdg: 'SDG', ifrs: 'IFRS', gri: 'GRI', issb: 'ISSB', cbn: 'CBN', nsbp: 'NSBP', sec: 'SEC', ngx: 'NGX', tcfd: 'TCFD',
};

/** "petrol_pms" → "Petrol (PMS)", "lpg" → "LPG", "iot_sensor" → "IoT sensor". */
export function humanize(code) {
  if (code == null || code === '') return '';
  const words = String(code).toLowerCase().split(/[_\s]+/).filter(Boolean)
    .map((w) => WORDS[w] ?? w);
  const first = words[0];
  words[0] = Object.values(WORDS).includes(first) ? first : first.charAt(0).toUpperCase() + first.slice(1);
  return words.join(' ');
}

/** Role names keep their established form in the app: "ESG_ANALYST" → "ESG ANALYST". */
export const roleLabel = (name) => (name ?? '').replace(/_/g, ' ');
