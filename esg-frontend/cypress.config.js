import { createHmac } from 'node:crypto';
import { defineConfig } from 'cypress';

/** RFC 6238 TOTP (SHA-1, 30 s, 6 digits) — the same as TotpService.java — for the seeded dev-only MFA secrets. */
function totp(base32Secret, now = Date.now()) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const ch of base32Secret.replace(/=+$/, '').toUpperCase()) bits += alphabet.indexOf(ch).toString(2).padStart(5, '0');
  const key = Buffer.from(bits.match(/.{8}/g).map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(now / 1000 / 30)));
  const hmac = createHmac('sha1', key).update(counter).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const code = (hmac.readUInt32BE(offset) & 0x7fffffff) % 1_000_000;
  return String(code).padStart(6, '0');
}

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
      // WP-50: set by scripts/e2e.sh when Ollama serves the Granite model, so the extraction journey can run for real.
      aiLlm: process.env.E2E_AI_LLM === 'true',
      // Dev-only TOTP secrets of the seeded MFA-required users (db/seed/README.md; S1). Local seed only.
      mfaSecrets: {
        'adaeze.usman@esgpro.ng': 'ATHERISLOCALDEVDIRECTORTOTPAAAAA',
        'tenant.admin@esgpro.ng': 'ATHERISLOCALDEVTENANTADMINTOTPAA',
        'super.admin@esgpro.ng': 'ATHERISLOCALDEVSUPERADMINTOTPAAA',
        'ethics@esgpro.ng': 'ATHERISLOCALDEVETHICSOFFICERTOTP',
      },
    },
    setupNodeEvents(on) {
      on('task', { totp: (secret) => totp(secret) });
    },
  },
});
