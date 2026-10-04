import { afterEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LANGUAGES, currentLanguage, formattingLocale, initialLanguage, languageMeta, setLanguage, tr } from './index';
import { formatCurrency, formatDate, formatNumber, formatPercent } from './format';
import STATUS from './locales/status.json';
import LanguagePicker from '../components/i18n/LanguagePicker';
import { anonymous, authenticated, renderWithStore } from '../test/renderWithStore';
import { fakeApi, ok } from '../test/fakeApi';

afterEach(async () => {
  await setLanguage('en');
  localStorage.clear();
});

describe('tr (WP-45, D45)', () => {
  it('returns the English text when the language is English or has no translation', () => {
    expect(tr('Save draft')).toBe('Save draft');
    expect(tr('A text no bundle has')).toBe('A text no bundle has');
    expect(tr(undefined)).toBeUndefined();
    expect(tr('')).toBe('');
  });

  it('returns the French text once French is loaded, and falls back to English for unknown texts', async () => {
    await setLanguage('fr');
    expect(currentLanguage()).toBe('fr');
    expect(tr('Settings')).toBe('Paramètres');
    expect(tr('A text no bundle has')).toBe('A text no bundle has');
    expect(languageMeta('fr')).toMatchObject({ status: 'draft', reviewedBy: null });
  });

  it('leaves {{…}} alone without values and interpolates with them', () => {
    expect(tr('Hello {{name}}')).toBe('Hello {{name}}');
    expect(tr('Hello {{name}}', { name: 'Ada' })).toBe('Hello Ada');
  });
});

describe('setLanguage', () => {
  it('sets lang and dir on the page, right-to-left for Arabic', async () => {
    await setLanguage('ar');
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    await setLanguage('sw');
    expect(document.documentElement.dir).toBe('ltr');
  });

  it('falls back to English for an unsupported code and remembers the choice on the device', async () => {
    expect(await setLanguage('de')).toBe('en');
    await setLanguage('ha');
    expect(localStorage.getItem('atheris.language')).toBe('ha');
    expect(initialLanguage()).toBe('ha');
  });
});

describe('locale bundles', () => {
  it('cover every supported language, all marked as unreviewed drafts', () => {
    for (const code of Object.keys(LANGUAGES).filter((c) => c !== 'en')) {
      expect(STATUS[code]).toMatchObject({ status: 'draft', coverage: 1 });
    }
  });
});

describe('formatters (BRD-5.2.1-06)', () => {
  it('format numbers, money, percentages and dates in the reader\'s locale', async () => {
    expect(formatNumber(1234567.891)).toBe('1,234,567.89');
    expect(formatCurrency(1500, 'NGN')).toBe('₦1,500.00');
    expect(formatPercent(0.25)).toBe('25%');
    expect(formatNumber(null)).toBe('—');
    await setLanguage('fr');
    expect(formattingLocale()).toBe('fr-FR');
    expect(formatNumber(1234.5).replace(/\s/g, ' ')).toBe('1 234,5');
    expect(formatDate('2026-03-05')).toMatch(/5 mars 2026/);
  });
});

describe('LanguagePicker', () => {
  it('signed in: switches the interface, saves the choice on the account and says the translation is a draft', async () => {
    const calls = fakeApi({ 'PUT /auth/me/language': () => ok({ preferredLanguage: 'fr' }) });
    renderWithStore(<LanguagePicker />, { preloadedState: authenticated([]) });
    await userEvent.click(screen.getByRole('button', { name: /Language: English/ }));
    const menu = screen.getByRole('menu');
    expect(within(menu).getAllByText(/Draft translation · 100%/)).toHaveLength(6);
    await userEvent.click(within(menu).getByText('Français'));
    await waitFor(() => expect(currentLanguage()).toBe('fr'));
    await waitFor(() => expect(calls.map((c) => c.key)).toContain('PUT /auth/me/language'));
    expect(calls.find((c) => c.key === 'PUT /auth/me/language').body).toEqual({ preferredLanguage: 'fr' });
    expect(await screen.findByText(/Cette traduction est un brouillon/)).toBeInTheDocument();
  });

  it('signed out: switches the interface without calling the API', async () => {
    const calls = fakeApi({});
    renderWithStore(<LanguagePicker />, { preloadedState: anonymous });
    await userEvent.click(screen.getByRole('button', { name: /Language/ }));
    await userEvent.click(within(screen.getByRole('menu')).getByText('العربية'));
    await waitFor(() => expect(document.documentElement.dir).toBe('rtl'));
    expect(calls).toHaveLength(0);
  });
});
