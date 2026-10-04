// WP-45 (D45): wraps the interface's English text in tr() — JSX text, text props (label, title, helperText, …), label
// fields rendered from config maps (s.label), lookups into label maps (STATUS_LABELS[s] — UPPER_CASE objects whose values
// are all text), and confirm/toast messages. Edits text in place (formatting kept).
// Usage: node scripts/i18n-codemod.mjs [files…]   (default: every non-test .jsx under src/)
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from '@babel/parser';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(ROOT, 'src');
export const TEXT_ATTRS = new Set(['label', 'title', 'helperText', 'placeholder', 'aria-label', 'emptyTitle', 'emptyMessage', 'subtitle',
  'alt', 'ariaLabel', 'caption', 'description', 'tooltip', 'confirmLabel', 'actionLabel', 'heading', 'text', 'noOptionsText', 'loadingText',
  'closeText']);
export const MEMBER_PROPS = new Set(['label', 'title', 'description', 'hint', 'message', 'emptyTitle', 'detail']);
const CALL_OBJECT_KEYS = new Set(['title', 'message', 'confirmLabel', 'label']);
const CALLS = new Set(['confirm', 'toastShown']);

/** Human text: has a letter and isn't an identifier, path, key or code-like token. */
export const isText = (s) => {
  if (typeof s !== 'string') return false;
  const v = s.trim();
  if (!/[A-Za-z]/.test(v)) return false;
  if (/^(https?:|\/|\.\/|#|mailto:)/.test(v)) return false;
  if (/^[a-z0-9_.:/#-]+$/.test(v)) return false; // keys, permissions, enum values, ids
  if (/^[A-Z0-9_]+$/.test(v) && v.length > 1 && v.includes('_')) return false; // CONSTANT_CASE
  if (/^[a-z]{2,}(?:[A-Z][a-z0-9]*)+$/.test(v)) return false; // camelCase field names
  return true;
};

/** A label map: `const UPPER_CASE = { key: 'Text', … }` — every value a string literal, at least one of them human text. */
export const isLabelMap = (decl) => decl?.type === 'VariableDeclarator' && decl.id?.type === 'Identifier' && /^[A-Z][A-Z0-9_]+$/.test(decl.id.name)
  && decl.init?.type === 'ObjectExpression' && decl.init.properties.length > 0
  && decl.init.properties.every((p) => p.type === 'ObjectProperty' && p.value.type === 'StringLiteral')
  && decl.init.properties.some((p) => isText(p.value.value) && /[a-z]/.test(p.value.value));

/** Names of the label maps declared anywhere under src/ (module scope). */
export function labelMapNames(dir = SRC) {
  const names = new Set();
  const all = (d) => readdirSync(d).flatMap((f) => {
    const p = join(d, f);
    if (statSync(p).isDirectory()) return f === 'test' || f === 'locales' ? [] : all(p);
    return /\.jsx?$/.test(p) && !p.includes('.test.') ? [p] : [];
  });
  for (const file of all(dir)) {
    let ast;
    try { ast = parse(readFileSync(file, 'utf8'), { sourceType: 'module', plugins: ['jsx'] }); } catch { continue; }
    for (const stmt of ast.program.body) {
      const decl = stmt.type === 'ExportNamedDeclaration' ? stmt.declaration : stmt;
      if (decl?.type === 'VariableDeclaration') decl.declarations.filter(isLabelMap).forEach((d) => names.add(d.id.name));
    }
  }
  return names;
}

function walk(node, visit, parent = null) {
  if (!node || typeof node.type !== 'string') return;
  if (visit(node, parent) === false) return;
  for (const k of Object.keys(node)) {
    if (['loc', 'start', 'end', 'extra', 'leadingComments', 'trailingComments', 'innerComments'].includes(k)) continue;
    const v = node[k];
    if (Array.isArray(v)) v.forEach((c) => c && typeof c.type === 'string' && walk(c, visit, node));
    else if (v && typeof v.type === 'string') walk(v, visit, node);
  }
}

const lit = (s) => JSON.stringify(s);
const elementName = (el) => {
  const n = el?.openingElement?.name;
  return n?.type === 'JSXIdentifier' ? n.name : n?.type === 'JSXMemberExpression' ? n.property.name : '';
};
const isCodeElement = (el) => ['code', 'pre', 'kbd'].includes(elementName(el))
  || el?.openingElement?.attributes?.some((a) => a.name?.name === 'component' && ['code', 'pre'].includes(a.value?.value));
const isTr = (expr) => expr?.type === 'CallExpression' && expr.callee?.type === 'Identifier' && expr.callee.name === 'tr';

export function transform(code, labelMaps = new Set()) {
  const ast = parse(code, { sourceType: 'module', plugins: ['jsx'] });
  const edits = [];
  const isMapLookup = (e) => (e?.type === 'MemberExpression' || e?.type === 'OptionalMemberExpression') && e.computed
    && e.object?.type === 'Identifier' && labelMaps.has(e.object.name);
  const wrapExpr = (expr) => {
    if (!expr || isTr(expr)) return;
    switch (expr.type) {
      case 'StringLiteral':
        if (isText(expr.value)) edits.push([expr.start, expr.end, `tr(${lit(expr.value)})`]);
        break;
      case 'ConditionalExpression':
        wrapExpr(expr.consequent);
        wrapExpr(expr.alternate);
        break;
      case 'LogicalExpression':
        wrapExpr(expr.left);
        wrapExpr(expr.right);
        break;
      case 'TemplateLiteral':
        if (expr.expressions.length === 0 && isText(expr.quasis[0].value.cooked)) edits.push([expr.start, expr.end, `tr(${lit(expr.quasis[0].value.cooked)})`]);
        else expr.expressions.filter(isMapLookup).forEach(wrapExpr); // not s.title here: template parts are often user data
        break;
      case 'MemberExpression':
      case 'OptionalMemberExpression': {
        if (expr.computed && expr.object?.type === 'Identifier' && labelMaps.has(expr.object.name)) {
          edits.push([expr.start, expr.end, `tr(${code.slice(expr.start, expr.end)})`]);
          break;
        }
        const prop = expr.computed ? null : expr.property?.name;
        if (prop && MEMBER_PROPS.has(prop)) edits.push([expr.start, expr.end, `tr(${code.slice(expr.start, expr.end)})`]);
        break;
      }
      default:
    }
  };
  walk(ast.program, (node, parent) => {
    if (node.type === 'JSXElement' && isCodeElement(node)) return false;
    if (isTr(node)) return false;
    if (node.type === 'JSXText') {
      const raw = code.slice(node.start, node.end);
      const text = node.value.replace(/\s+/g, ' ').trim();
      if (!/[A-Za-z]/.test(text)) return undefined;
      const lead = raw.match(/^\s*/)[0];
      const trail = raw.match(/\s*$/)[0];
      edits.push([node.start, node.end, `${lead}{tr(${lit(text)})}${trail}`]);
      return undefined;
    }
    if (node.type === 'JSXExpressionContainer' && (parent?.type === 'JSXElement' || parent?.type === 'JSXFragment')) {
      wrapExpr(node.expression);
      return undefined;
    }
    if (node.type === 'JSXAttribute' && node.name?.type === 'JSXIdentifier' && TEXT_ATTRS.has(node.name.name)) {
      const v = node.value;
      if (v?.type === 'StringLiteral' && isText(v.value)) edits.push([v.start, v.end, `{tr(${lit(v.value)})}`]);
      else if (v?.type === 'JSXExpressionContainer') wrapExpr(v.expression);
      return undefined;
    }
    if ((node.type === 'MemberExpression' || node.type === 'OptionalMemberExpression') && node.computed
      && node.object?.type === 'Identifier' && labelMaps.has(node.object.name)
      && (parent?.type === 'TemplateLiteral' || (parent?.type === 'LogicalExpression' && parent.operator === '??' && parent.left === node))) {
      edits.push([node.start, node.end, `tr(${code.slice(node.start, node.end)})`]);
      return false;
    }
    if (node.type === 'CallExpression' && node.callee?.type === 'Identifier' && CALLS.has(node.callee.name)) {
      for (const arg of node.arguments) {
        if (arg.type !== 'ObjectExpression') continue;
        for (const p of arg.properties) {
          if (p.type === 'ObjectProperty' && !p.computed && CALL_OBJECT_KEYS.has(p.key.name ?? p.key.value)) wrapExpr(p.value);
        }
      }
    }
    return undefined;
  });
  if (!edits.length) return null;
  edits.sort((a, b) => a[0] - b[0] || b[1] - a[1]);
  const kept = [];
  for (const e of edits) {
    const last = kept[kept.length - 1];
    if (last && e[0] < last[1]) continue; // nested inside an outer edit
    kept.push(e);
  }
  let out = code;
  for (const [s, e, text] of [...kept].reverse()) out = out.slice(0, s) + text + out.slice(e);
  return { code: out, count: kept.length };
}

function addImport(code, file) {
  if (/import\s*\{[^}]*\btr\b[^}]*\}\s*from\s*['"][^'"]*i18n['"]/.test(code)) return code;
  let rel = relative(dirname(file), join(SRC, 'i18n')).replace(/\\/g, '/');
  if (!rel.startsWith('.')) rel = `./${rel}`;
  const line = `import { tr } from '${rel}';`;
  const ast = parse(code, { sourceType: 'module', plugins: ['jsx'] });
  const imports = ast.program.body.filter((n) => n.type === 'ImportDeclaration');
  const at = imports.length ? imports[imports.length - 1].end : 0;
  return at ? `${code.slice(0, at)}\n${line}${code.slice(at)}` : `${line}\n${code}`;
}

function files(dir) {
  return readdirSync(dir).flatMap((f) => {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) return f === 'test' || f === 'i18n' ? [] : files(p);
    return p.endsWith('.jsx') && !p.includes('.test.') ? [p] : [];
  });
}

if (process.argv[1] && process.argv[1].endsWith('i18n-codemod.mjs')) {
  const targets = process.argv.length > 2 ? process.argv.slice(2).map((f) => join(process.cwd(), f)) : files(SRC);
  const maps = labelMapNames();
  let total = 0;
  for (const file of targets) {
    const code = readFileSync(file, 'utf8');
    const result = transform(code, maps);
    if (!result) continue;
    writeFileSync(file, addImport(result.code, file));
    total += result.count;
    console.log(`${relative(ROOT, file)}: ${result.count}`);
  }
  console.log(`[i18n-codemod] ${total} strings wrapped`);
}
