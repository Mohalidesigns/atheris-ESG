import { formattingLocale } from './index';

/**
 * WP-45 (D45): numbers, money and dates in the reader's language (BRD-5.2.1-06). Money uses the narrow symbol where
 * one exists (₦, KSh, R, GH₵, $, €, £); the ISO code otherwise.
 */
export const formatNumber = (value, options = {}) => (value == null || value === '' || Number.isNaN(Number(value)) ? '—'
  : new Intl.NumberFormat(formattingLocale(), { maximumFractionDigits: 2, ...options }).format(Number(value)));

export const formatCurrency = (value, currency = 'NGN', options = {}) => (value == null || value === '' ? '—'
  : new Intl.NumberFormat(formattingLocale(), { style: 'currency', currency, currencyDisplay: 'narrowSymbol', maximumFractionDigits: 2, ...options })
    .format(Number(value)));

const toDate = (value) => (value instanceof Date ? value : new Date(/^\d{4}-\d{2}-\d{2}$/.test(String(value)) ? `${value}T00:00:00` : value));

export const formatDate = (value, options = { dateStyle: 'medium' }) => (value ? new Intl.DateTimeFormat(formattingLocale(), options).format(toDate(value)) : '—');

export const formatDateTime = (value) => formatDate(value, { dateStyle: 'medium', timeStyle: 'short' });

export const formatPercent = (value, digits = 0) => (value == null ? '—'
  : new Intl.NumberFormat(formattingLocale(), { style: 'percent', maximumFractionDigits: digits }).format(Number(value)));
