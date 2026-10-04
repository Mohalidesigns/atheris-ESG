import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// WP-52: fonts are served with the app (no request to Google: visitors' IP addresses stay in the platform; CSP 'self').
import '@fontsource/inter/300.css'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/roboto-mono/400.css'
import '@fontsource/roboto-mono/500.css'
import '@fontsource/roboto-mono/600.css'
import './index.css'
import App from './App.jsx'
import { initialLanguage, setLanguage } from './i18n'

// WP-43: the service worker that lets the app open and take data offline. Registered after the page has loaded, so it
// never competes with the first visit; production builds only (the dev server serves modules fresh).
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    import('virtual:pwa-register').then(({ registerSW }) => registerSW({ immediate: true }));
  });
}

// WP-45: start in the language chosen on this device before (or the browser's); English needs nothing loaded.
const render = () => createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
setLanguage(initialLanguage()).catch(() => {}).finally(render)
