import { z } from 'zod';

/**
 * Carbon market form rules. Mirror the server exactly: CarbonMarketDtos (Bean Validation) + CreditService.checkLot /
 * request / act and PricingService.check. Keep them in step.
 */
// no-mock-gate: config — option labels (CarbonMarketDtos regexes, V033 checks)
export const STANDARDS = { verra_vcs: 'Verra (VCS)', gold_standard: 'Gold Standard', plan_vivo: 'Plan Vivo', cdm: 'CDM', article_6_4: 'Article 6.4',
  acmi: 'ACMI-endorsed', other: 'Other' };
export const PROJECT_TYPES = {
  reforestation_arr: 'Reforestation (ARR)', mangrove: 'Mangrove restoration', redd_plus: 'REDD+', soil_carbon: 'Soil carbon', blue_carbon: 'Blue carbon',
  agroforestry: 'Agroforestry', clean_cookstoves: 'Clean cookstoves', solar: 'Solar', wind: 'Wind', biogas: 'Biogas', methane_capture: 'Methane capture',
  water_purification: 'Water purification', other: 'Other',
};
export const CURRENCIES = ['NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP'];
export const PURPOSES = { scope_1: 'Scope 1', scope_2: 'Scope 2', scope_3: 'Scope 3', voluntary: 'Voluntary contribution' };
export const SCOPES = { scope_1: 'Scope 1', scope_2: 'Scope 2', scope_3: 'Scope 3' };
export const PRICE_TYPES = { shadow: 'Shadow price', fee: 'Internal fee' };
export const STATUS = { active: 'Active', archived: 'Archived' };
export const RETIREMENT_STATUS = { requested: { label: 'Requested', color: 'warning' }, retired: { label: 'Retired', color: 'success' },
  cancelled: { label: 'Cancelled', color: 'default' } };

const blank = (v) => (v == null ? '' : String(v));
const nul = (v) => (v === '' || v == null ? null : v);
const num = (v) => (v === '' || v == null ? null : Number(v));
const whole = /^\d+$/;

// ---------------------------------------------------------------- project (ProjectRequest)

export const emptyProject = { name: '', standard: 'verra_vcs', registry: '', registryProjectId: '', projectType: 'reforestation_arr', countryCode: 'NG', methodology: '',
  registryUrl: '', description: '', status: 'active' };
export const toProjectForm = (p) => Object.fromEntries(Object.keys(emptyProject).map((k) => [k, blank(p[k])]));
export const projectSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
  standard: z.enum(Object.keys(STANDARDS)),
  registry: z.string().trim().min(1, 'Name the registry').max(100, 'At most 100 characters'),
  registryProjectId: z.string().trim().min(1, "Give the registry's project ID").max(60, 'At most 60 characters'),
  projectType: z.enum(Object.keys(PROJECT_TYPES)),
  countryCode: z.string().trim().regex(/^[A-Z]{2}$/, 'must be a 2-letter country code'),
  methodology: z.string().trim().max(100, 'At most 100 characters'),
  registryUrl: z.string().trim().max(500, 'At most 500 characters').refine((v) => v === '' || /^https?:\/\/.+/.test(v), 'must start with http:// or https://'),
  description: z.string().trim().max(2000, 'At most 2000 characters'),
  status: z.enum(Object.keys(STATUS)),
});
export const toProjectBody = (v) => ({ name: v.name.trim(), standard: v.standard, registry: v.registry.trim(), registryProjectId: v.registryProjectId.trim(),
  projectType: v.projectType, countryCode: v.countryCode.trim(), methodology: nul(v.methodology.trim()), registryUrl: nul(v.registryUrl.trim()),
  description: nul(v.description.trim()), status: v.status });

// ---------------------------------------------------------------- credit lot (LotRequest)

export const emptyLot = { projectId: '', vintage: '', serialPrefix: '', serialFrom: '', serialTo: '', acquiredOn: '', pricePerTonne: '', currency: '', seller: '',
  transactionRef: '', notes: '' };
export const toLotForm = (l) => Object.fromEntries(Object.keys(emptyLot).map((k) => [k, blank(l[k])]));
export const lotSchema = (today) => z.object({
  projectId: z.string().min(1, 'Choose the project'),
  vintage: z.string().trim().refine((v) => /^\d{4}$/.test(v) && Number(v) >= 1990 && Number(v) <= 2100, 'A year from 1990 to 2100'),
  serialPrefix: z.string().trim().min(1, 'Give the issuance batch (serial prefix)').max(120, 'At most 120 characters'),
  serialFrom: z.string().trim().refine((v) => whole.test(v), 'A whole number from 0'),
  serialTo: z.string().trim().refine((v) => whole.test(v), 'A whole number from 0'),
  acquiredOn: z.string().min(1, 'Date is required').refine((d) => !d || d <= today, "can't be in the future"),
  pricePerTonne: z.string().trim().refine((v) => v === '' || /^\d{1,10}(\.\d{1,4})?$/.test(v), 'A price from 0, up to 4 decimals'),
  currency: z.string(),
  seller: z.string().trim().max(200, 'At most 200 characters'),
  transactionRef: z.string().trim().max(100, 'At most 100 characters'),
  notes: z.string().trim().max(1000, 'At most 1000 characters'),
}).superRefine((v, ctx) => {
  const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
  if (whole.test(v.serialFrom) && whole.test(v.serialTo) && Number(v.serialTo) < Number(v.serialFrom)) issue('serialTo', 'must be at or after the first serial');
  if ((v.pricePerTonne.trim() === '') !== (v.currency === '')) issue(v.pricePerTonne.trim() === '' ? 'pricePerTonne' : 'currency', 'give the price and its currency, or neither');
  if (/^\d{4}$/.test(v.vintage) && v.acquiredOn && Number(v.vintage) > Number(v.acquiredOn.slice(0, 4)) + 1) issue('vintage', "can't be later than the year after acquisition");
});
export const toLotBody = (v) => ({ projectId: v.projectId, vintage: Number(v.vintage), serialPrefix: v.serialPrefix.trim(), serialFrom: Number(v.serialFrom),
  serialTo: Number(v.serialTo), acquiredOn: v.acquiredOn, pricePerTonne: num(v.pricePerTonne.trim()), currency: nul(v.currency), seller: nul(v.seller.trim()),
  transactionRef: nul(v.transactionRef.trim()), notes: nul(v.notes.trim()) });

// ---------------------------------------------------------------- retirement (RetireRequest + approval)

export const retireSchema = (available, year, vintage) => z.object({
  quantity: z.string().trim().refine((v) => whole.test(v) && Number(v) >= 1, 'A whole number of credits from 1')
    .refine((v) => !whole.test(v) || Number(v) <= available, `only ${available} credits of this lot are still available`),
  purpose: z.enum(Object.keys(PURPOSES)),
  claimedYear: z.string().trim().refine((v) => /^\d{4}$/.test(v) && Number(v) >= 2000 && Number(v) <= year, `A year from 2000 to ${year}`)
    .refine((v) => !/^\d{4}$/.test(v) || Number(v) >= vintage - 5, `is more than five years before the credits' vintage (${vintage})`),
  beneficiary: z.string().trim().min(1, 'Name the beneficiary').max(200, 'At most 200 characters'),
  note: z.string().trim().max(1000, 'At most 1000 characters'),
});
export const approveSchema = (today) => z.object({
  registryReference: z.string().trim().min(1, "Give the registry's retirement reference").max(120, 'At most 120 characters'),
  retiredOn: z.string().min(1, 'Date is required').refine((d) => !d || d <= today, "can't be in the future"),
});

// ---------------------------------------------------------------- internal price (PriceRequest) and ceiling

export const emptyPrice = { priceType: 'shadow', price: '', currency: 'USD', effectiveFrom: '', scopes: ['scope_1', 'scope_2'], note: '' };
export const toPriceForm = (p) => ({ priceType: p.priceType, price: blank(p.price), currency: p.currency, effectiveFrom: blank(p.effectiveFrom), scopes: p.scopes ?? [],
  note: p.note ?? '' });
export const priceSchema = z.object({
  priceType: z.enum(Object.keys(PRICE_TYPES)),
  price: z.string().trim().refine((v) => /^\d{1,12}(\.\d{1,2})?$/.test(v) && Number(v) > 0, 'A price above 0, up to 2 decimals'),
  currency: z.enum(CURRENCIES),
  effectiveFrom: z.string().min(1, 'Date is required'),
  scopes: z.array(z.enum(Object.keys(SCOPES))).min(1, 'choose at least one scope').max(3),
  note: z.string().trim().max(1000, 'At most 1000 characters'),
});
export const toPriceBody = (v) => ({ priceType: v.priceType, price: Number(v.price), currency: v.currency, effectiveFrom: v.effectiveFrom,
  scopes: [...v.scopes].sort(), note: nul(v.note.trim()) });

export const ceilingSchema = z.object({
  offsetCeilingPct: z.string().trim().refine((v) => /^\d{1,3}(\.\d)?$/.test(v) && Number(v) <= 100, 'A percentage from 0 to 100 (one decimal)'),
});

export const lagosToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' });
export const fmt = (v, digits = 1) => (v == null ? '—' : Number(v).toLocaleString(undefined, { maximumFractionDigits: digits }));
export const problemText = (err) => err?.data?.detail ?? Object.values(err?.data?.errors ?? {})[0] ?? 'Something went wrong';
