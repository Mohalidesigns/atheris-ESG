import { Fragment, useEffect, useMemo, useState } from 'react';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
import { CacheProvider } from '@emotion/react';
import createCache from '@emotion/cache';
import { prefixer } from 'stylis';
import rtlPlugin from '@mui/stylis-plugin-rtl';
import lightTheme from '../theme/lightTheme';
import { LANGUAGES, currentLanguage, onLanguageChanged } from './index';

const caches = {
  ltr: createCache({ key: 'mui', stylisPlugins: [prefixer] }),
  rtl: createCache({ key: 'muirtl', stylisPlugins: [prefixer, rtlPlugin] }),
};

/**
 * WP-45 (D45): the interface follows the chosen language — Arabic lays the app out right to left (MUI direction plus
 * the RTL style plugin, which mirrors margins, paddings and positions). Changing language re-renders the app below
 * the store, so cached data and the session stay.
 */
export default function LanguageRoot({ children }) {
  const [lang, setLang] = useState(currentLanguage());
  useEffect(() => onLanguageChanged(setLang), []);
  const dir = LANGUAGES[lang]?.dir ?? 'ltr';
  const theme = useMemo(() => createTheme(lightTheme, { direction: dir }), [dir]);
  return (
    <CacheProvider value={caches[dir]}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Fragment key={lang}>{children}</Fragment>
      </ThemeProvider>
    </CacheProvider>
  );
}
