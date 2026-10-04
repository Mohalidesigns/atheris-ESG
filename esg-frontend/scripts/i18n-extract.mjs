// WP-45 (D45): builds the translation catalogue from the source and keeps every language bundle in step with it.
//   node scripts/i18n-extract.mjs          — add new texts (untranslated), drop texts no longer used, update coverage
//   node scripts/i18n-extract.mjs --check  — CI: fail if a bundle is out of step with the source
// Texts: tr('…') calls, label/title/description/hint/message/text/header fields of config maps, the values of label maps
// (UPPER_CASE objects of text, looked up through tr()), and Zod validation messages.
import { readFileSync, writeFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from '@babel/parser';
import { isText, isLabelMap } from './i18n-codemod.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(ROOT, 'src');
const LOCALES = join(SRC, 'i18n', 'locales');
export const LANGS = ['fr', 'pt', 'ar', 'sw', 'ha', 'yo'];
const FIELD_KEYS = new Set(['label', 'title', 'description', 'hint', 'message', 'emptyTitle', 'detail', 'text', 'header', 'headerName']);
const ZOD = new Set(['min', 'max', 'length', 'regex', 'email', 'url', 'refine', 'int', 'positive', 'nonnegative', 'gte', 'lte', 'gt', 'lt',
  'nonempty', 'multipleOf', 'finite']);

function files(dir) {
  return readdirSync(dir).flatMap((f) => {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) return f === 'test' || f === 'locales' ? [] : files(p);
    return /\.(jsx?|mjs)$/.test(p) && !p.includes('.test.') ? [p] : [];
  });
}

function walk(node, visit) {
  if (!node || typeof node.type !== 'string') return;
  visit(node);
  for (const k of Object.keys(node)) {
    if (['loc', 'start', 'end', 'extra'].includes(k)) continue;
    const v = node[k];
    if (Array.isArray(v)) v.forEach((c) => c && typeof c.type === 'string' && walk(c, visit));
    else if (v && typeof v.type === 'string') walk(v, visit);
  }
}

const literal = (n) => (n?.type === 'StringLiteral' ? n.value
  : n?.type === 'TemplateLiteral' && n.expressions.length === 0 ? n.quasis[0].value.cooked : null);

export function extract() {
  const keys = new Map(); // text → first file
  const add = (text, file) => {
    if (text && isText(text) && !keys.has(text)) keys.set(text, relative(ROOT, file));
  };
  for (const file of files(SRC)) {
    const code = readFileSync(file, 'utf8');
    let ast;
    try {
      ast = parse(code, { sourceType: 'module', plugins: ['jsx'] });
    } catch {
      continue;
    }
    walk(ast.program, (n) => {
      if (n.type === 'CallExpression' && n.callee?.type === 'Identifier' && n.callee.name === 'tr') add(literal(n.arguments[0]), file);
      if (n.type === 'ObjectProperty' && !n.computed && FIELD_KEYS.has(n.key.name ?? n.key.value)) add(literal(n.value), file);
      if (isLabelMap(n)) n.init.properties.forEach((p) => add(p.value.value, file));
      if (n.type === 'CallExpression' && n.callee?.type === 'MemberExpression' && ZOD.has(n.callee.property?.name)) {
        n.arguments.forEach((a) => add(literal(a), file));
      }
    });
  }
  return keys;
}

const sorted = (obj) => Object.fromEntries(Object.entries(obj).sort(([a], [b]) => a.localeCompare(b)));

export function run({ check = false } = {}) {
  const keys = extract();
  const status = existsSync(join(LOCALES, 'status.json')) ? JSON.parse(readFileSync(join(LOCALES, 'status.json'), 'utf8')) : {};
  status.en = { status: 'source', coverage: 1, texts: keys.size };
  let outOfStep = [];
  for (const lang of LANGS) {
    const path = join(LOCALES, `${lang}.json`);
    const bundle = existsSync(path) ? JSON.parse(readFileSync(path, 'utf8'))
      : { meta: { language: lang, status: 'draft', translatedBy: '', reviewedBy: null, note: '' }, messages: {} };
    const messages = {};
    for (const k of keys.keys()) messages[k] = bundle.messages[k] ?? '';
    const missing = [...keys.keys()].filter((k) => !(k in bundle.messages));
    const obsolete = Object.keys(bundle.messages).filter((k) => !keys.has(k));
    if (missing.length || obsolete.length) outOfStep.push(`${lang}: ${missing.length} new, ${obsolete.length} obsolete`);
    const translated = Object.values(messages).filter((v) => v).length;
    status[lang] = { status: bundle.meta.reviewedBy ? 'reviewed' : 'draft', coverage: keys.size ? Number((translated / keys.size).toFixed(3)) : 0 };
    if (!check) writeFileSync(path, `${JSON.stringify({ meta: bundle.meta, messages: sorted(messages) }, null, 2)}\n`);
  }
  if (!check) writeFileSync(join(LOCALES, 'status.json'), `${JSON.stringify(status, null, 2)}\n`);
  console.log(`[i18n] ${keys.size} texts; ${LANGS.map((l) => `${l} ${Math.round(status[l].coverage * 100)}%`).join(', ')}`);
  if (check && outOfStep.length) {
    console.error(`[i18n] bundles out of step with the source — run npm run i18n:extract\n  ${outOfStep.join('\n  ')}`);
    process.exit(1);
  }
}

if (process.argv[1] && process.argv[1].endsWith('i18n-extract.mjs')) run({ check: process.argv.includes('--check') });
