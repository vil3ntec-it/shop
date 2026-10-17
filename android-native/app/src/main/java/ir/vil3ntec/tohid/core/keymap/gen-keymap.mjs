#!/usr/bin/env node
/**
 *  keymap.json ⇒ KeyMap.kt (کامپیوتر و اندروید) + license/keymap.js (وب) — MASTER-SPEC ۳.۱۶.
 *
 *    node gen-keymap.mjs           هر دو فایل را دوباره می‌سازد
 *    node gen-keymap.mjs --check   اگر کهنه باشند با کدِ ۱ بیرون می‌آید (CI و server/test/keymap.test.js)
 *
 *  این‌جا فقط درستیِ ساختار سنجیده می‌شود (نامِ کلید، دامنه، شناسهٔ تکراری).
 *  قاعده‌های تداخل (کلیدِ تکراری در یک دامنه، پوشاندنِ سراسری، کلیدهای رزرو)
 *  در KeyMapTest است تا در CIِ اندروید و کامپیوتر هم اجرا شود.
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));

/** ریشهٔ مخزن: نخستین پوشهٔ بالاتر که index.html و license/ دارد */
function repoRoot() {
  let d = HERE;
  for (let i = 0; i < 16; i++) {
    if (existsSync(join(d, 'index.html')) && existsSync(join(d, 'license'))) return d;
    d = dirname(d);
  }
  throw new Error('ریشهٔ مخزن پیدا نشد');
}

export const PATHS = (() => {
  const root = repoRoot();
  return {
    root,
    json: join(HERE, 'keymap.json'),
    kotlin: join(HERE, 'KeyMap.kt'),
    js: join(root, 'license', 'keymap.js'),
  };
})();

/**
 *  واژه‌نامهٔ کلیدها — نام ⇒ برچسب و KeyboardEvent.code (کدِ فیزیکی، مستقل از چیدمانِ فارسی و پشتو).
 *  نگاشتِ Compose در ui/screens/KeyBinding.kt است و `when`ِ کامل دارد: نامِ تازه بی نگاشت کامپایل نمی‌شود.
 */
export const KEYS = (() => {
  const k = [];
  for (const c of 'ABCDEFGHIJKLMNOPQRSTUVWXYZ') k.push({ name: c, label: c, codes: ['Key' + c] });
  for (let i = 0; i <= 9; i++) k.push({ name: 'D' + i, label: String(i), codes: i === 0 ? ['Digit0', 'Numpad0'] : ['Digit' + i] });
  for (let i = 1; i <= 12; i++) k.push({ name: 'F' + i, label: 'F' + i, codes: ['F' + i] });
  k.push(
    { name: 'Enter', label: 'Enter', codes: ['Enter', 'NumpadEnter'] },
    { name: 'Escape', label: 'Esc', codes: ['Escape'] },
    { name: 'Delete', label: 'Delete', codes: ['Delete'] },
    { name: 'Tab', label: 'Tab', codes: ['Tab'] },
    { name: 'Space', label: 'Space', codes: ['Space'] },
    { name: 'Home', label: 'Home', codes: ['Home'] },
    { name: 'End', label: 'End', codes: ['End'] },
    { name: 'PageUp', label: 'PgUp', codes: ['PageUp'] },
    { name: 'PageDown', label: 'PgDn', codes: ['PageDown'] },
    { name: 'Up', label: '↑', codes: ['ArrowUp'] },
    { name: 'Down', label: '↓', codes: ['ArrowDown'] },
    { name: 'Left', label: '←', codes: ['ArrowLeft'] },
    { name: 'Right', label: '→', codes: ['ArrowRight'] },
    { name: 'Plus', label: '+', codes: ['NumpadAdd'] },
    { name: 'Minus', label: '−', codes: ['Minus', 'NumpadSubtract'] },
    { name: 'Equals', label: '=', codes: ['Equal'] },
    { name: 'Slash', label: '/', codes: ['Slash'] },
  );
  return k;
})();
const KEY_BY_NAME = new Map(KEYS.map((k) => [k.name, k]));

const STATUSES = ['live', 'partial', 'planned'];
const KINDS = ['global', 'screen', 'window'];

/** «Mod+Shift+F5» ⇒ { key, mod, shift, alt } — همان Chord.parseِ Kotlin */
export function parseChord(s) {
  const parts = String(s).split('+');
  const name = parts.pop();
  const c = { key: /^[0-9]$/.test(name) ? 'D' + name : name, mod: false, shift: false, alt: false };
  if (!KEY_BY_NAME.has(c.key)) throw new Error(`کلیدِ ناشناخته «${name}» در «${s}»`);
  for (const m of parts) {
    const f = { Mod: 'mod', Shift: 'shift', Alt: 'alt' }[m];
    if (!f || c[f]) throw new Error(`پیرایهٔ نادرست «${m}» در «${s}»`);
    c[f] = true;
  }
  return c;
}

/** keymap.json را می‌خواند و ساختارش را می‌سنجد */
export function load(text = readFileSync(PATHS.json, 'utf8')) {
  const src = JSON.parse(text);
  const scopes = src.scopes.map((s) => ({ id: s.id, kind: s.kind, title: s.title, parent: s.parent ?? null }));
  const scopeIds = new Set();
  for (const s of scopes) {
    if (!/^[a-z][a-zA-Z0-9]*(\.[a-z][a-zA-Z0-9]*)*$/.test(s.id)) throw new Error(`شناسهٔ دامنهٔ نادرست: ${s.id}`);
    if (scopeIds.has(s.id)) throw new Error(`دامنهٔ تکراری: ${s.id}`);
    if (!KINDS.includes(s.kind)) throw new Error(`نوعِ دامنهٔ نادرست: ${s.id} ${s.kind}`);
    scopeIds.add(s.id);
  }
  const ids = new Set();
  const actions = src.actions.map((a) => {
    if (!scopeIds.has(a.scope)) throw new Error(`دامنهٔ ناشناخته برای ${a.id}: ${a.scope}`);
    if (!a.id.startsWith(a.scope + '.') || !/^[a-z][a-zA-Z0-9]*$/.test(a.id.slice(a.scope.length + 1))) throw new Error(`شناسه باید «${a.scope}.نام» باشد: ${a.id}`);
    if (ids.has(a.id)) throw new Error(`شناسهٔ تکراری: ${a.id}`);
    ids.add(a.id);
    if (!STATUSES.includes(a.status)) throw new Error(`وضعیتِ نادرست برای ${a.id}: ${a.status}`);
    if (!Array.isArray(a.keys) || !a.keys.length) throw new Error(`${a.id} کلید ندارد`);
    return {
      id: a.id, scope: a.scope, title: a.title, status: a.status, page: a.page ?? null,
      keys: a.keys.map(parseChord), keysRaw: a.keys,
      web: (a.web ?? []).map(parseChord), webRaw: a.web ?? [],
      overrides: a.overrides ?? [],
    };
  });
  for (const a of actions) for (const o of a.overrides) if (!ids.has(o)) throw new Error(`${a.id} چیزی را می‌پوشاند که نیست: ${o}`);
  const reserved = (list) => list.map((r) => ({ chord: parseChord(r.key), raw: r.key, why: r.why }));
  return { scopes, actions, reserved: { native: reserved(src.reserved.native), web: reserved(src.reserved.web) } };
}

const constName = (id) => id.split('.').map((p) => p.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toUpperCase()).join('_');
const ktStr = (s) => '"' + String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$') + '"';
const ktChord = (c) => {
  const args = [`KeyName.${c.key}`];
  if (c.mod) args.push('mod = true');
  if (c.shift) args.push('shift = true');
  if (c.alt) args.push('alt = true');
  return `Chord(${args.join(', ')})`;
};
const ktList = (xs, f) => (xs.length ? `listOf(${xs.map(f).join(', ')})` : 'emptyList()');

const HEADER = (by) => `⚠️ تولیدشده از keymap.json با ${by} — دستی ویرایش نشود.`;
const GEN = 'android-native/app/src/main/java/ir/vil3ntec/tohid/core/keymap/gen-keymap.mjs';

export function kotlin(m) {
  const out = [];
  out.push(`//  ${HEADER(`\`node ${GEN}\``)}`);
  out.push('//  منبع: keymap.json در همین پوشه (MASTER-SPEC ۳.۱۶ و ۵.۲). آزمون: KeyMapTest.');
  out.push('package ir.vil3ntec.tohid.core.keymap', '');
  out.push('/** نامِ کلیدِ فیزیکی. `codes` همان KeyboardEvent.codeِ وب است؛ نگاشتِ Compose در ui/screens/KeyBinding.kt. */');
  out.push('enum class KeyName(val label: String, val codes: List<String>) {');
  for (const k of KEYS) out.push(`  ${k.name}(${ktStr(k.label)}, listOf(${k.codes.map(ktStr).join(', ')})),`);
  out.push('}', '');
  out.push('object KeyMap {');
  out.push('  val SCOPES: List<KeyScope> = listOf(');
  for (const s of m.scopes) out.push(`    KeyScope(${ktStr(s.id)}, KeyScope.Kind.${s.kind.toUpperCase()}, ${ktStr(s.title)}${s.parent ? `, parent = ${ktStr(s.parent)}` : ''}),`);
  out.push('  )', '');
  for (const a of m.actions) {
    out.push(`  val ${constName(a.id)} = KeyAction(`);
    out.push(`    ${ktStr(a.id)}, ${ktStr(a.scope)}, ${ktStr(a.title)}, KeyStatus.${a.status.toUpperCase()},`);
    out.push(`    keys = ${ktList(a.keys, ktChord)},`);
    out.push(`    web = ${ktList(a.web, ktChord)},`);
    if (a.overrides.length) out.push(`    overrides = listOf(${a.overrides.map(ktStr).join(', ')}),`);
    if (a.page) out.push(`    page = ${ktStr(a.page)},`);
    out.push('  )');
  }
  out.push('');
  out.push('  val ALL: List<KeyAction> = listOf(');
  for (const a of m.actions) out.push(`    ${constName(a.id)},`);
  out.push('  )', '');
  for (const [name, list] of [['RESERVED_NATIVE', m.reserved.native], ['RESERVED_WEB', m.reserved.web]]) {
    out.push(`  val ${name}: List<ReservedKey> = listOf(`);
    for (const r of list) out.push(`    ReservedKey(${ktChord(r.chord)}, ${ktStr(r.why)}),`);
    out.push('  )');
  }
  out.push('}', '');
  return out.join('\n');
}

export function js(m) {
  const codes = Object.fromEntries(KEYS.map((k) => [k.name, k.codes]));
  const labels = Object.fromEntries(KEYS.map((k) => [k.name, k.label]));
  const chord = (c) => [c.key, c.mod, c.shift, c.alt];
  const actions = {};
  for (const a of m.actions) {
    actions[a.id] = { scope: a.scope, title: a.title, status: a.status, page: a.page, web: a.web.map(chord), overrides: a.overrides };
  }
  const scopes = Object.fromEntries(m.scopes.map((s) => [s.id, { kind: s.kind, title: s.title, parent: s.parent }]));
  return `/* ${HEADER(`node ${GEN}`)}
 * نقشهٔ کلیدهای وب (ستونِ «وب»ِ جدولِ ۵.۲ و MASTER-SPEC ۳.۱۶). کلید با e.code خوانده می‌شود،
 * پس با چیدمانِ فارسی و پشتو هم کار می‌کند؛ Mod یعنی Ctrl یا Cmd.
 * هر کلید: [نام، Mod، Shift، Alt]. استفاده: TohidKeymap.matches(event, 'global.page1')
 */
(function (root) {
  'use strict';
  var CODES = ${JSON.stringify(codes)};
  var LABELS = ${JSON.stringify(labels)};
  var SCOPES = ${JSON.stringify(scopes, null, 1).replace(/\n\s*/g, ' ')};
  var ACTIONS = {
${Object.entries(actions).map(([id, a]) => `    ${JSON.stringify(id)}: ${JSON.stringify(a)}`).join(',\n')}
  };
  function chordMatches(e, c) {
    return CODES[c[0]].indexOf(e.code) >= 0 &&
      !!(e.ctrlKey || e.metaKey) === c[1] && !!e.shiftKey === c[2] && !!e.altKey === c[3];
  }
  function action(id) {
    var a = ACTIONS[id];
    if (!a) throw new Error('کلیدِ ناشناخته در keymap: ' + id);
    return a;
  }
  /** آیا این رویدادِ keydown کارِ id است؟ */
  function matches(e, id) {
    var w = action(id).web;
    for (var i = 0; i < w.length; i++) if (chordMatches(e, w[i])) return true;
    return false;
  }
  /** برچسبِ نخستین کلیدِ وب: «Alt+1»، روی مک «Cmd+K» */
  function label(id, mac) {
    var c = action(id).web[0];
    if (!c) return '';
    var p = [];
    if (c[1]) p.push(mac ? 'Cmd' : 'Ctrl');
    if (c[3]) p.push(mac ? 'Option' : 'Alt');
    if (c[2]) p.push('Shift');
    p.push(LABELS[c[0]]);
    return p.join('+');
  }
  var api = { CODES: CODES, SCOPES: SCOPES, ACTIONS: ACTIONS, matches: matches, label: label };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.TohidKeymap = api;
})(typeof window !== 'undefined' ? window : globalThis);
`;
}

export function generate() {
  const m = load();
  return { [PATHS.kotlin]: kotlin(m), [PATHS.js]: js(m) };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const check = process.argv.includes('--check');
  let stale = 0;
  for (const [file, text] of Object.entries(generate())) {
    const now = existsSync(file) ? readFileSync(file, 'utf8') : null;
    if (now === text) continue;
    if (check) { stale++; console.error(`کهنه: ${relative(PATHS.root, file)} — node ${GEN}`); }
    else { writeFileSync(file, text); console.log(`ساخته شد: ${relative(PATHS.root, file)}`); }
  }
  if (stale) process.exit(1);
}
