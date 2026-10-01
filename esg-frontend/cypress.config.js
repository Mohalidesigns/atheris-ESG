import { defineConfig } from 'cypress';

/**
 * E2E (WP-18, D3). Runs against a real backend on the seeded local demo tenant (db/seed/local) and the
 * frontend served by Vite. ./scripts/e2e.sh starts both on a fresh database; CI does the same (ci.yml, e2e job).
 */
export default defineConfig({
  e2e: {
    baseUrl: process.env.E2E_BASE_URL || 'http://localhost:4173',
    specPattern: 'cypress/e2e/**/*.cy.js',
    supportFile: 'cypress/support/e2e.js',
    viewportWidth: 1440,
    viewportHeight: 900,
    defaultCommandTimeout: 10000,
    video: false,
    screenshotOnRunFailure: true,
    retries: { runMode: 1, openMode: 0 },
    env: {
      // Seeded local demo password (README "Run locally in 5 minutes"); a test value for the local seed only.
      password: process.env.E2E_PASSWORD || 'LocalDev!2026',
    },
  },
});
