import { z } from 'zod';

// Mirrors IntegrationDtos (WP-46, D44): the same limits and messages as the server's Bean Validation.

// no-mock-gate: config — option labels for the request form and statuses
export const DATA_CATEGORIES = {
  energy_fuel: 'Energy and fuel', utilities: 'Utilities (electricity, water)', procurement_spend: 'Procurement spend (Scope 3)',
  workforce: 'Workforce', training: 'Training', waste: 'Waste', production: 'Production', other: 'Other',
};
export const FREQUENCIES = { daily: 'Daily', weekly: 'Weekly', monthly: 'Monthly', quarterly: 'Quarterly', annually: 'Annually' };
export const REQUEST_STATUS = {
  requested: { label: 'Requested', color: 'info' },
  in_review: { label: 'In review', color: 'info' },
  scheduled: { label: 'Scheduled', color: 'warning' },
  active: { label: 'Active', color: 'success' },
  declined: { label: 'Declined', color: 'error' },
  withdrawn: { label: 'Withdrawn', color: 'default' },
};
export const PLATFORM_STATUSES = { in_review: 'In review', scheduled: 'Scheduled', active: 'Active (connector live)', declined: 'Declined' };

export const emptyRequest = { systemDetails: '', dataNeeded: [], frequency: 'monthly', contactName: '', contactEmail: '', contactPhone: '', notes: '' };

export const requestSchema = z.object({
  systemDetails: z.string().trim().min(1, 'Describe the system (product, version, hosted or on-premise)').max(500, 'At most 500 characters'),
  dataNeeded: z.array(z.string()).min(1, 'choose at least one kind of data').max(8),
  frequency: z.enum(Object.keys(FREQUENCIES), { message: 'must be daily, weekly, monthly, quarterly or annually' }),
  contactName: z.string().trim().min(1, 'Contact name is required').max(200, 'At most 200 characters'),
  contactEmail: z.string().trim().min(1, 'Contact email is required').max(255).email('must be an email address'),
  contactPhone: z.string().trim().regex(/^$|^\+?[0-9 ()-]{7,30}$/, 'must be a phone number'),
  notes: z.string().trim().max(2000, 'At most 2000 characters'),
});

export const toRequestBody = (connectorKey, v) => ({
  connectorKey, systemDetails: v.systemDetails.trim(), dataNeeded: v.dataNeeded, frequency: v.frequency, contactName: v.contactName.trim(),
  contactEmail: v.contactEmail.trim(), contactPhone: v.contactPhone.trim() || null, notes: v.notes.trim() || null,
});

export const emptyApiKey = { name: '', scopes: [], expiresOn: '' };

/** expiresOn: a date (yyyy-mm-dd); empty = one year (server default); at most two years ahead (server). */
export const apiKeySchema = (today = new Date()) => {
  const iso = (d) => d.toISOString().slice(0, 10);
  const max = new Date(today.getTime() + 730 * 86400000);
  return z.object({
    name: z.string().trim().min(1, 'Name is required').max(100, 'At most 100 characters'),
    scopes: z.array(z.string()).min(1, 'choose at least one permission').max(30),
    expiresOn: z.string().refine((v) => !v || v > iso(today), 'must be in the future').refine((v) => !v || v <= iso(max), 'at most two years from today'),
  });
};

export const toApiKeyBody = (v) => ({
  name: v.name.trim(), scopes: v.scopes, expiresAt: v.expiresOn ? new Date(`${v.expiresOn}T23:59:59Z`).toISOString() : null,
});

export const emptyWebhook = { name: '', url: '', events: [], active: true };

export const webhookSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(100, 'At most 100 characters'),
  url: z.string().trim().min(1, 'URL is required').max(500, 'At most 500 characters')
    .refine((v) => { try { const u = new URL(v); return ['https:', 'http:'].includes(u.protocol); } catch { return false; } }, 'is not a valid URL'),
  events: z.array(z.string()).min(1, 'choose at least one event').max(20),
  active: z.boolean(),
});

export const toWebhookBody = (v) => ({ name: v.name.trim(), url: v.url.trim(), events: v.events, active: v.active });

export const statusSchema = z.object({
  status: z.enum(Object.keys(PLATFORM_STATUSES), { message: 'must be in_review, scheduled, active or declined' }),
  note: z.string().trim().max(1000, 'At most 1000 characters'),
}).refine((v) => v.status !== 'declined' || v.note.length > 0, { path: ['note'], message: 'Say why the request is declined' });

export const when = (iso) => (iso ? new Date(iso).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : '—');
