#!/usr/bin/env node
// WP-53: performance budget for the first page (sign-in) on slow networks. Reads esg-frontend/dist after `npm run build`
// and fails when the JavaScript the browser must download before the sign-in form appears grows past the budget —
// e.g. a feature schema (and zod) creeping into the entry bundle, which happened once.
import { readFileSync, readdirSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { join } from 'node:path';

const DIST = join(import.meta.dirname, '..', 'esg-frontend', 'dist');
const BUDGET_KB = { entry: 95, initial: 190 }; // gzip; measured 86 / ~170 on 3 Oct 2026

const html = readFileSync(join(DIST, 'index.html'), 'utf8');
const files = [...html.matchAll(/(?:src|href)="\/assets\/([^"]+\.js)"/g)].map((m) => m[1]);
const entry = [...html.matchAll(/<script[^>]+src="\/assets\/([^"]+\.js)"/g)].map((m) => m[1])[0];
const gz = (f) => gzipSync(readFileSync(join(DIST, 'assets', f))).length / 1024;
const login = readdirSync(join(DIST, 'assets')).find((f) => /^LoginForm-.*\.js$/.test(f));
const initial = [...new Set([...files, login])].reduce((sum, f) => sum + gz(f), 0);
const entryKb = gz(entry);
console.log(`entry ${entry}: ${entryKb.toFixed(1)} KB gz (budget ${BUDGET_KB.entry}); sign-in JS ${initial.toFixed(1)} KB gz (budget ${BUDGET_KB.initial})`);
if (entryKb > BUDGET_KB.entry || initial > BUDGET_KB.initial) {
  console.error('Bundle budget exceeded: keep heavy libraries (zod, charts, feature code) out of the entry and sign-in chunks.');
  process.exit(1);
}
