/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Backend context path is /api (esg-backend application.yml). Requests through the proxy are
// same-origin for the browser, so the Origin header is dropped instead of whitelisting every dev port.
// Cross-origin deployments still go through the backend CORS allow-list (CORS_ALLOWED_ORIGINS).
const proxy = {
  '/api': {
    target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
    changeOrigin: true,
    configure: (p) => p.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin')),
  },
};

// WP-52: the headers the production web server must send (docs/esg-completion/SECURITY.md). The preview server sends
// them too, so the E2E journeys and the ZAP scan run against the real policy. MUI injects <style> tags, hence
// 'unsafe-inline' for styles only; scripts are same-origin; the API is same-origin through /api.
export const securityHeaders = {
  'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
    + "font-src 'self' data:; connect-src 'self'; frame-src 'none'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'",
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
  'Cross-Origin-Opener-Policy': 'same-origin',
};

// WP-43 (D43): an installable app whose shell and every page's code are cached by a Workbox service worker, so it opens
// and takes data without a connection (what people save waits on the device: src/features/offline). API responses are
// never cached by the service worker — the offline copy of reference data is per user and cleared at sign-out.
const pwa = VitePWA({
  injectRegister: false, // registered after load in main.jsx (keeps the sign-in page's first load unchanged)
  registerType: 'autoUpdate',
  includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
  manifest: {
    name: 'Atheris ESG',
    short_name: 'Atheris ESG',
    description: 'ESG data collection, carbon accounting and reporting',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    theme_color: '#1A365D',
    background_color: '#F7FAFC',
    icons: [
      { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
      { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
      { src: 'pwa-maskable-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  },
  workbox: {
    globPatterns: ['**/*.{js,css,html,woff2,png,svg}'],
    navigateFallback: '/index.html',
    navigateFallbackDenylist: [/^\/api\//],
    maximumFileSizeToCacheInBytes: 3 * 1024 * 1024,
    cleanupOutdatedCaches: true,
  },
});

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), pwa],
  server: { proxy },
  preview: { proxy, headers: securityHeaders }, // E2E (WP-18) runs Cypress against the production build served by `vite preview`

  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
    // Absolute base so fetch Request objects work in jsdom; tests stub fetch (src/test/fakeApi.js).
    env: { VITE_API_BASE_URL: 'http://localhost/api/v1' },
    css: false,
    exclude: ['node_modules/**', 'cypress/**'],
    // Multi-step UI tests (wizards, editors) run 1–2 s alone but several times longer when every file runs in parallel.
    testTimeout: 15000,
  },
})
