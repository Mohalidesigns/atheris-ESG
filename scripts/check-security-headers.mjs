#!/usr/bin/env node
// WP-54: the production web server must send exactly the security headers the app is tested with (the preview server
// in E2E uses `securityHeaders` from esg-frontend/vite.config.js). Fails when deploy/nginx.conf drifts from it.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const root = join(import.meta.dirname, '..');
const vite = readFileSync(join(root, 'esg-frontend', 'vite.config.js'), 'utf8');
const block = vite.slice(vite.indexOf('export const securityHeaders'), vite.indexOf('};', vite.indexOf('export const securityHeaders')));
const expected = {};
for (const m of block.matchAll(/'([A-Za-z-]+)':\s*((?:"[^"]*"|'[^']*')(?:\s*\+\s*(?:"[^"]*"|'[^']*'))*)/g)) {
  expected[m[1]] = [...m[2].matchAll(/"([^"]*)"|'([^']*)'/g)].map((x) => x[1] ?? x[2]).join('');
}
const nginx = readFileSync(join(root, 'deploy', 'nginx.conf'), 'utf8');
const location = nginx.slice(nginx.indexOf('location / {'));
const problems = [];
for (const [name, value] of Object.entries(expected)) {
  for (const [where, text] of [['server', nginx.slice(0, nginx.search(/^\s*location /m))], ['location /', location]]) {
    const m = text.match(new RegExp(`add_header ${name} "([^"]*)" always;`));
    if (!m) problems.push(`${where}: ${name} missing`);
    else if (m[1] !== value) problems.push(`${where}: ${name} differs\n    nginx: ${m[1]}\n    app:   ${value}`);
  }
}
if (Object.keys(expected).length < 5) problems.push('could not read securityHeaders from vite.config.js');
console.log(`${Object.keys(expected).length} security headers compared`);
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
