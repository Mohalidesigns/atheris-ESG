import { z } from 'zod';
import { formattingLocale } from '../../i18n';

/**
 * Investor portal form rules. Mirror the server exactly: InvestorDtos (Bean Validation) + ScorecardService,
 * DataRoomService.check, BondService.check / checkAllocation and ImpactService.check. Keep them in step.
 */
// no-mock-gate: config — option labels (InvestorDtos regexes, V032 checks)
export const INSTRUMENTS = {
  green_bond: 'Green bond', social_bond: 'Social bond', sustainability_bond: 'Sustainability bond', sustainability_linked_bond: 'Sustainability-linked bond',
  green_sukuk: 'Green sukuk', sustainability_sukuk: 'Sustainability sukuk',
};
export const CURRENCIES = ['NGN', 'USD', 'EUR', 'GBP', 'KES', 'ZAR', 'GHS', 'XOF', 'EGP'];
export const BOND_STATUS = { active: 'Active', matured: 'Matured', redeemed: 'Redeemed' };
export const CATEGORIES = {
  renewable_energy: 'Renewable energy', energy_efficiency: 'Energy efficiency', pollution_prevention: 'Pollution prevention and control',
  sustainable_land_use: 'Sustainable land use', biodiversity: 'Biodiversity', clean_transport: 'Clean transport', sustainable_water: 'Sustainable water',
  climate_adaptation: 'Climate adaptation', circular_economy: 'Circular economy', green_buildings: 'Green buildings',
  affordable_infrastructure: 'Affordable basic infrastructure', essential_services: 'Access to essential services', affordable_housing: 'Affordable housing',
  employment_generation: 'Employment generation', food_security: 'Food security', socioeconomic_advancement: 'Socioeconomic advancement',
};
export const PURPOSES = { due_diligence: 'Due diligence', investor_relations: 'Investor relations', dfi_monitoring: 'DFI monitoring',
  bond_reporting: 'Bond reporting', other: 'Other' };
export const ROOM_STATUS = { open: 'Open', closed: 'Closed' };
export const SCORECARD_STATUS = { draft: { label: 'Draft', color: 'default' }, published: { label: 'Published', color: 'success' },
  withdrawn: { label: 'Withdrawn', color: 'warning' } };
export const SCORECARD_ACTIONS = { publish: 'Publish to investors', withdraw: 'Withdraw', discard: 'Discard draft' };
export const PILLARS = { E: 'Environmental', S: 'Social', G: 'Governance' };
export const SPT_STATUS = { met: { label: 'Target met', color: 'success' }, missed: { label: 'Target missed', color: 'error' },
  pending: { label: 'Pending', color: 'default' }, unknown: { label: 'Unknown', color: 'default' } };

const blank = (v) => (v == null ? '' : String(v));
const nul = (v) => (v === '' || v == null ? null : v);
const num = (v) => (v === '' || v == null ? null : Number(v));
const whole = (min, max, label) => z.string().trim().refine((v) => /^\d+$/.test(v) && Number(v) >= min && Number(v) <= max, `${label}: a whole number from ${min} to ${max}`);
const money = /^\d{1,18}(\.\d{1,2})?$/;

// ---------------------------------------------------------------- weights (SettingsRequest)

export const toWeightsForm = (s) => ({ weightE: blank(s.weightE), weightS: blank(s.weightS), weightG: blank(s.weightG) });
export const weightsSchema = z.object({ weightE: whole(0, 100, 'E weight'), weightS: whole(0, 100, 'S weight'), weightG: whole(0, 100, 'G weight') })
  .superRefine((v, ctx) => {
    if (Number(v.weightE) + Number(v.weightS) + Number(v.weightG) !== 100) {
      ctx.addIssue({ code: 'custom', path: ['weightE'], message: 'the E, S and G weights must add up to 100' });
    }
  });
export const toWeightsBody = (v) => ({ weightE: Number(v.weightE), weightS: Number(v.weightS), weightG: Number(v.weightG) });

export const generateSchema = z.object({
  title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
  commentary: z.string().trim().max(4000, 'At most 4000 characters'),
});

// ---------------------------------------------------------------- data room (RoomRequest)

export const emptyRoom = { name: '', purpose: 'due_diligence', description: '', expiresOn: '', status: 'open', watermark: true, memberIds: [] };
export const toRoomForm = (r) => ({ name: r.name ?? '', purpose: r.purpose, description: r.description ?? '', expiresOn: blank(r.expiresOn), status: r.status,
  watermark: !!r.watermark, memberIds: r.memberIds ?? [] });
export const roomSchema = (today) => z.object({
  name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
  purpose: z.enum(Object.keys(PURPOSES)),
  description: z.string().trim().max(2000, 'At most 2000 characters'),
  expiresOn: z.string().min(1, 'Expiry date is required').refine((d) => !d || d >= today, "can't be in the past"),
  status: z.enum(Object.keys(ROOM_STATUS)),
  watermark: z.boolean(),
  memberIds: z.array(z.string()).max(200),
});
export const toRoomBody = (v) => ({ name: v.name.trim(), purpose: v.purpose, description: nul(v.description.trim()), expiresOn: v.expiresOn, status: v.status,
  watermark: v.watermark, memberIds: v.memberIds });

// ---------------------------------------------------------------- instrument (BondRequest)

export const emptyBond = {
  instrument: 'green_bond', name: '', isin: '', issueDate: '', maturityDate: '', amount: '', currency: 'NGN', frameworkReference: '', externalReviewer: '',
  shariahReviewer: '', shariahPronouncementOn: '', kpiId: '', sptValue: '', sptObservationYear: '', couponStepUpBps: '', visibleToInvestors: false, status: 'active',
};
export const toBondForm = (b) => ({ ...Object.fromEntries(Object.keys(emptyBond).map((k) => [k, blank(b[k])])), visibleToInvestors: !!b.visibleToInvestors });
export const isSlb = (i) => i === 'sustainability_linked_bond';
export const isSukuk = (i) => i?.endsWith('sukuk');
export const bondSchema = z.object({
  instrument: z.enum(Object.keys(INSTRUMENTS)),
  name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
  isin: z.string().trim().refine((v) => v === '' || /^[A-Z]{2}[A-Z0-9]{9}[0-9]$/.test(v), 'must be a 12-character ISIN'),
  issueDate: z.string().min(1, 'Issue date is required'),
  maturityDate: z.string().min(1, 'Maturity date is required'),
  amount: z.string().trim().refine((v) => money.test(v) && Number(v) > 0, 'An amount above 0, up to 2 decimals'),
  currency: z.enum(CURRENCIES),
  frameworkReference: z.string().trim().min(1, 'Name the framework (e.g. ICMA Green Bond Principles)').max(300, 'At most 300 characters'),
  externalReviewer: z.string().trim().max(200, 'At most 200 characters'),
  shariahReviewer: z.string().trim().max(200, 'At most 200 characters'),
  shariahPronouncementOn: z.string(),
  kpiId: z.string(),
  sptValue: z.string().trim().refine((v) => v === '' || /^-?\d{1,14}(\.\d{1,6})?$/.test(v), 'A number (up to 6 decimals)'),
  sptObservationYear: z.string().trim().refine((v) => v === '' || (/^\d{4}$/.test(v) && Number(v) >= 2000 && Number(v) <= 2100), 'A year from 2000 to 2100'),
  couponStepUpBps: z.string().trim().refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) <= 1000), 'A whole number from 0 to 1000'),
  visibleToInvestors: z.boolean(),
  status: z.enum(Object.keys(BOND_STATUS)),
}).superRefine((v, ctx) => {
  const issue = (path, message) => ctx.addIssue({ code: 'custom', path: [path], message });
  if (v.issueDate && v.maturityDate && v.maturityDate <= v.issueDate) issue('maturityDate', 'must be after the issue date');
  if (isSlb(v.instrument)) {
    if (!v.kpiId) issue('kpiId', 'a sustainability-linked bond needs its KPI');
    if (!v.sptValue) issue('sptValue', 'set the sustainability performance target');
    if (!v.sptObservationYear) issue('sptObservationYear', 'set the year the target is observed');
  }
  if (isSukuk(v.instrument) && !v.shariahReviewer.trim()) issue('shariahReviewer', 'a sukuk needs its Shariah reviewer');
});
export const toBondBody = (v) => {
  const slb = isSlb(v.instrument);
  const sukuk = isSukuk(v.instrument);
  return {
    instrument: v.instrument, name: v.name.trim(), isin: nul(v.isin.trim()), issueDate: v.issueDate, maturityDate: v.maturityDate, amount: Number(v.amount),
    currency: v.currency, frameworkReference: v.frameworkReference.trim(), externalReviewer: nul(v.externalReviewer.trim()),
    shariahReviewer: sukuk ? nul(v.shariahReviewer.trim()) : null, shariahPronouncementOn: sukuk ? nul(v.shariahPronouncementOn) : null,
    kpiId: slb ? nul(v.kpiId) : null, sptValue: slb ? num(v.sptValue.trim()) : null, sptObservationYear: slb ? num(v.sptObservationYear) : null,
    couponStepUpBps: slb ? num(v.couponStepUpBps) : null, visibleToInvestors: v.visibleToInvestors, status: v.status,
  };
};

export const emptyAllocation = { orgId: '', category: '', project: '', amount: '', allocatedOn: '', impactNote: '' };
export const toAllocationForm = (a) => Object.fromEntries(Object.keys(emptyAllocation).map((k) => [k, blank(a[k])]));
export const allocationSchema = (bond, today, others) => z.object({
  orgId: z.string().min(1, 'Choose the site'),
  category: z.string().min(1, 'Choose the eligible category'),
  project: z.string().trim().min(1, 'Name the project').max(200, 'At most 200 characters'),
  amount: z.string().trim().refine((v) => money.test(v) && Number(v) > 0, 'An amount above 0, up to 2 decimals')
    .refine((v) => Number(v) + others <= Number(bond.amount), `would allocate more than the ${bond.currency} ${bond.amount} raised (${Number(bond.amount) - others} left)`),
  allocatedOn: z.string().min(1, 'Date is required').refine((d) => !d || d >= bond.issueDate, `can't be before the issue date (${bond.issueDate})`)
    .refine((d) => !d || d <= today, "can't be in the future"),
  impactNote: z.string().trim().max(1000, 'At most 1000 characters'),
});
export const toAllocationBody = (v) => ({ orgId: v.orgId, category: v.category, project: v.project.trim(), amount: Number(v.amount), allocatedOn: v.allocatedOn,
  impactNote: nul(v.impactNote.trim()) });

// ---------------------------------------------------------------- impact metric (ImpactRequest)

export const emptyImpact = { irisCode: '', name: '', metricKey: '', kpiId: '', note: '', visibleToInvestors: true };
export const toImpactForm = (i) => ({ ...Object.fromEntries(Object.keys(emptyImpact).map((k) => [k, blank(i[k])])), visibleToInvestors: !!i.visibleToInvestors });
export const impactSchema = z.object({
  irisCode: z.string().trim().regex(/^[A-Z]{2}[0-9]{4}$/, 'must be an IRIS+ code like PI4060'),
  name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
  metricKey: z.string(),
  kpiId: z.string(),
  note: z.string().trim().max(1000, 'At most 1000 characters'),
  visibleToInvestors: z.boolean(),
}).superRefine((v, ctx) => {
  if (!v.metricKey === !v.kpiId) ctx.addIssue({ code: 'custom', path: ['metricKey'], message: 'choose either a catalogue metric or a KPI' });
});
export const toImpactBody = (v) => ({ irisCode: v.irisCode.trim(), name: v.name.trim(), metricKey: nul(v.metricKey), kpiId: nul(v.kpiId), note: nul(v.note.trim()),
  visibleToInvestors: v.visibleToInvestors });

export const lagosToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lagos' });
export const fmt = (v, digits = 1) => (v == null ? '—' : Number(v).toLocaleString(formattingLocale(), { maximumFractionDigits: digits }));
export const problemText = (err) => err?.data?.detail ?? Object.values(err?.data?.errors ?? {})[0] ?? 'Something went wrong';
export const saveBlob = (blobData, name) => {
  const href = URL.createObjectURL(blobData);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  a.rel = 'noopener';
  a.click();
  setTimeout(() => URL.revokeObjectURL(href), 10_000);
};
