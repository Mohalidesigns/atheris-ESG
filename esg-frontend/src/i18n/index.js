import i18n from 'i18next';

/**
 * WP-45 (D45): the interface languages. The English text is the key (no separate English bundle): `tr('Save draft')`
 * returns the translation for the current language, or the English text when there is none. Other languages are JSON
 * bundles loaded on demand (src/i18n/locales/<code>.json, built by scripts/i18n-extract.mjs); each says whether it has
 * been reviewed by a professional translator — until then the UI marks it as a draft.
 */
// no-mock-gate: config — supported languages (TRD §7.1, BRD §7.6): name in the language itself, direction, formatting locale
export const LANGUAGES = {
  en: { name: 'English', dir: 'ltr', locale: 'en-NG' },
  fr: { name: 'Français', dir: 'ltr', locale: 'fr-FR' },
  pt: { name: 'Português', dir: 'ltr', locale: 'pt-PT' },
  ar: { name: 'العربية', dir: 'rtl', locale: 'ar-EG' },
  sw: { name: 'Kiswahili', dir: 'ltr', locale: 'sw-KE' },
  ha: { name: 'Hausa', dir: 'ltr', locale: 'ha-NG' },
  yo: { name: 'Yorùbá', dir: 'ltr', locale: 'yo-NG' },
};

const STORAGE_KEY = 'atheris.language';

i18n.init({
  lng: 'en',
  fallbackLng: 'en',
  resources: {},
  keySeparator: false,
  nsSeparator: false,
  returnEmptyString: false,
  interpolation: { escapeValue: false },
  initAsync: false,
});

/** Translate: the English text in, the current language's text out (English when there's no translation). */
export const tr = (text, options) => {
  if (text == null || text === '' || typeof text !== 'string') return text;
  // Without values nothing is interpolated: interface texts may show {{…}} literally (report variables).
  return i18n.t(text, options ?? { skipInterpolation: true });
};

/** Meta of the loaded bundles: { fr: { status: 'draft', … } }. */
const meta = {};

const loaders = import.meta.glob('./locales/*.json');

export const currentLanguage = () => i18n.language || 'en';
export const languageMeta = (code = currentLanguage()) => meta[code] ?? null;
export const formattingLocale = (code = currentLanguage()) => LANGUAGES[code]?.locale ?? 'en-NG';

/** Loads the language's bundle if needed, switches to it, and sets the page's lang and direction. */
export async function setLanguage(code) {
  const lng = LANGUAGES[code] ? code : 'en';
  if (lng !== 'en' && !i18n.hasResourceBundle(lng, 'translation')) {
    const load = loaders[`./locales/${lng}.json`];
    if (load) {
      const bundle = (await load()).default;
      meta[lng] = bundle.meta;
      i18n.addResourceBundle(lng, 'translation', bundle.messages, true, true);
    }
  }
  await i18n.changeLanguage(lng);
  if (typeof document !== 'undefined') {
    document.documentElement.lang = lng;
    document.documentElement.dir = LANGUAGES[lng].dir;
  }
  try {
    localStorage.setItem(STORAGE_KEY, lng);
  } catch {
    // private mode: the choice lasts for this visit
  }
  return lng;
}

/** The language to start in: the one chosen on this device before, else the browser's if we have it, else English. */
export function initialLanguage() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && LANGUAGES[saved]) return saved;
  } catch {
    // ignore
  }
  const browser = (typeof navigator !== 'undefined' && navigator.language ? navigator.language : 'en').slice(0, 2);
  return LANGUAGES[browser] ? browser : 'en';
}

export const onLanguageChanged = (listener) => {
  i18n.on('languageChanged', listener);
  return () => i18n.off('languageChanged', listener);
};

export default i18n;
