#!/usr/bin/env node
// ابزارِ docs/spec — مشخصاتِ نسخهٔ ۳: شکستنِ MASTER-SPEC.md، manifest، نمایه، STATUS، BLOCKERS و سنجش.
//
//   node docs/spec/tools/spec.mjs split    ⇐ تکه‌ها + manifest.json + 00-index.md را از نو می‌سازد
//   node docs/spec/tools/spec.mjs status   ⇐ STATUS.md و BLOCKERS.md را می‌سازد/تازه می‌کند (ستون‌های دستی حفظ می‌شوند)
//   node docs/spec/tools/spec.mjs check    ⇐ بی‌نوشتن می‌سنجد؛ با خطا کدِ ۱ برمی‌گرداند
//   node docs/spec/tools/spec.mjs ids      ⇐ شمارِ واقعیِ شناسه‌ها به تفکیکِ خانواده (فقط خواندن)
//
// ⛔ منبعِ حقیقت MASTER-SPEC.md است (§۲۸.۲، §۳۹.۹). تکه‌ها دستی ویرایش نمی‌شوند؛ اصل را عوض کن و split بزن.
//    برش بر مرزِ خطِ بایت‌های UTF-8ِ خودِ فایل است؛ چسباندنِ تکه‌ها به ترتیبِ manifest.json باید همان SHA-256ِ
//    اصل شود، وگرنه همه‌چیز می‌ایستد. manifest.json و 00-index.md بیرون از این چسباندن‌اند.
// ⛔ بخش‌های اصلی از روی سرخط کشف می‌شوند (`# N.` یا `## N.`، رقمِ فارسی یا لاتین)، بی سقفِ شماره؛ زیربخش‌های
//    ۶ و ۷ (`### ۶.N` و `### ۷.N`) هم جدا می‌شوند. بخشِ تازه خودکار تکه و ردیفِ نمایه می‌گیرد.
//
// شکل‌های «تعریفِ» شناسه (هر چیزِ دیگری ارجاع است):
//   - ردیفِ جدولی که خانهٔ اولش شناسه است (F0 در بخش ۲؛ D-* در هر جدولِ تصمیم — ردیف‌های یک D در جدول‌های
//     مختلف یک تصمیم‌اند و با هم ادغام می‌شوند)
//   - `- **POS-01** · …` (بخش ۶)            - `**S01 · عنوان** (وابستگی‌ها) … خروج فاز N` (بخش ۷)
//   - `- **ID:**` · `- **ID — عنوان:**` · `- **ID (فاز N):**` · `` - `ID`: `` (بخش‌های ۱۶ تا ۳۸)
//   برچسبِ پررنگی که پس از شناسه واژه دارد (`- **F0-01 ذخیرهٔ ویندوز:**`) «اصلاحیه» است، نه تعریف: شناسه باید
//   جای دیگری تعریف شده باشد.
// ⛔ شناسهٔ دوبار تعریف‌شده خطاست، مگر در BLOCKERS.md بخشِ «شناسه‌های چندجاتعریف‌شده» رده گرفته باشد:
//   «هم‌معنا» (یک الزام، یک ردیف) یا «برخورد» (دو الزامِ متفاوت؛ قاعدهٔ ID@بخش: هر دو می‌مانند، متن شماره‌گذاریِ
//   دوباره نمی‌شود و تعریفِ دوم کلیدِ `ID@<بخش>` می‌گیرد). ارجاع به شناسهٔ تعریف‌نشده از خانواده‌ای که تعریف دارد هم خطاست.
// ⛔ در STATUS.md ستون‌های وضعیت، تأیید، اتصال بیرونی، مانع و commit/PR، و در BLOCKERS.md ستون‌های وضعیت/رده/
//    توضیح دستی پر می‌شوند و status آن‌ها را دور نمی‌ریزد. بخشِ «یادداشت‌ها»ِ STATUS و «موانعِ دیگر»ِ BLOCKERS
//    دست‌نخورده می‌مانند.

import { readFileSync, writeFileSync, existsSync, readdirSync, unlinkSync, statSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const DIR = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(DIR, 'MASTER-SPEC.md');
const MANIFEST = join(DIR, 'manifest.json');
const INDEX = join(DIR, '00-index.md');
const STATUS = join(DIR, 'STATUS.md');
const BLOCKERS = join(DIR, 'BLOCKERS.md');
const OBSOLETE_DIR = join(DIR, 'obsolete');
// پرونده‌های دیگری که در docs/spec مجازند (بیرون از تکه‌ها).
const ALLOWED = new Set(['MASTER-SPEC.md', 'manifest.json', '00-index.md', 'STATUS.md', 'BLOCKERS.md', 'OWNER-ACTIONS-fa.md', 'ADMIN-CONTROL-PLANE.md', 'tools', 'bench', 'obsolete']);

const FA = '۰۱۲۳۴۵۶۷۸۹';
export const toLatin = (s) => String(s).replace(/[۰-۹]/g, (d) => String(FA.indexOf(d)));
export const toFa = (n) => String(n).replace(/[0-9]/g, (d) => FA[d]);
export const sha = (buf) => createHash('sha256').update(buf).digest('hex');

export const STATES = ['IMPLEMENTED-VERIFIED', 'PARTIAL', 'MISSING', 'BROKEN', 'NOT-VERIFIED', 'BLOCKED-EXTERNAL'];
const LEGACY = { '✅': 'IMPLEMENTED-VERIFIED', '🟡': 'PARTIAL', '🔌': 'BLOCKED-EXTERNAL', '⛔': 'MISSING' };
const SAME = 'هم‌معنا';
const CLASH = 'برخورد';
const UNREG = 'ثبت‌نشده';

// نامِ پرونده برای بخش‌های اصلی؛ بخشی که این‌جا نیست `NN-section.md` می‌شود (هیچ بخشی جا نمی‌ماند).
const SLUG = {
  1: 'goals', 2: 'current', 3: 'architecture', 4: 'afghanistan', 5: 'ux',
  8: 'phases', 9: 'testing', 10: 'security', 11: 'operations', 12: 'report', 13: 'decisions', 14: 'sources',
  15: 'corrections', 16: 'acceptance', 17: 'release-checklist', 18: 'changelog', 19: 'final-requirements',
  20: 'product-decisions', 21: 'accounts-support', 22: 'execution-order', 23: 'marketplace-login',
  24: 'evidence-audit', 25: 'dev-instructions', 26: 'completion', 27: 'old-start', 28: 'safe-execution',
  29: 'auth-security-annex', 30: 'order-notify', 31: 'store-presence', 32: 'catalog-stock', 33: 'chat',
  34: 'product-images', 35: 'new-gate', 36: 'image-studio', 37: 'image-bank', 38: 'gap-closure', 39: 'disambiguation',
};
const fileFor = (n) => `${String(n).padStart(2, '0')}-${SLUG[n] ?? 'section'}`;

// ---------------------------------------------------------------- شکستن
const MAIN = /^#{1,2} ([0-9۰-۹]+)\.(?=\s|$)\s*(.*?)\s*$/;
const SUB = /^### ([0-9۰-۹]+)\.([0-9۰-۹]+)\.?\s+(.*?)\s*$/;

// خط‌ها با پایانِ خطشان و به بایت بریده می‌شوند تا چسباندنِ تکه‌ها بایت‌به‌بایت همان اصل شود.
export function splitParts(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(input, 'utf8');
  const text = buf.toString('utf8');
  if (!Buffer.from(text, 'utf8').equals(buf)) throw new Error('MASTER-SPEC.md UTF-8ِ معتبر نیست');
  const lines = text.match(/[^\n]*\n|[^\n]+$/g) ?? [];
  const parts = [];
  const warnings = [];
  let cur = null;
  let fence = false;
  let top = null; // شمارهٔ بخشِ اصلیِ جاری
  let off = 0;
  let lineNo = 0;
  const open = (name, title, section, extra = {}) => {
    if (cur) cur.end = off;
    cur = { name, file: `${name}.md`, title, section, start: off, startLine: lineNo, ...extra };
    parts.push(cur);
  };
  open('00-intro', 'مقدمه: این سند چیست و چطور استفاده شود', 0);
  for (const line of lines) {
    lineNo++;
    if (/^```/.test(line)) fence = !fence;
    else if (!fence) {
      const h = line.match(MAIN);
      const s = line.match(SUB);
      if (h) {
        const n = Number(toLatin(h[1]));
        if (top !== null && n <= top) throw new Error(`ترتیب یا تکرارِ بخشِ اصلی: «${line.trim()}» پس از بخشِ ${top}`);
        if (top !== null && n !== top + 1) warnings.push(`شمارهٔ بخش از ${top} به ${n} پریده است`);
        top = n;
        const title = `${h[1]}. ${h[2]}`;
        if (n === 6) open('06-intro', `${title} — راهنما`, 6);
        else if (n === 7) open('07-0', `${title} — قواعد`, 7);
        else open(fileFor(n), title, n, /منسوخ/.test(h[2]) ? { obsolete: true } : {});
      } else if (top === null && /^#{1,2} .*پیام شروع/.test(line) && !parts.some((p) => p.name === '00-start')) {
        open('00-start', line.replace(/^#+\s*/, '').trim(), 0);
      } else if (s && (top === 6 || top === 7) && Number(toLatin(s[1])) === top) {
        const sub = Number(toLatin(s[2]));
        if (top === 6) {
          const g = s[3].match(/\(([A-Z]+)\)/);
          if (!g) throw new Error(`گروهِ بی‌کد در بخش ۶: ${line.trim()}`);
          open(`06-${g[1]}`, `${s[1]}.${s[2]} ${s[3]}`, 6);
        } else open(`07-${sub}`, `${s[1]}.${s[2]} ${s[3]}`, 7);
      }
    }
    off += Buffer.byteLength(line, 'utf8');
  }
  if (fence) throw new Error('یک ``` بسته نشده است');
  cur.end = off;
  const seen = new Set();
  for (const p of parts) {
    if (seen.has(p.name)) throw new Error(`نامِ تکراری: ${p.name}`);
    seen.add(p.name);
    p.bytes = buf.subarray(p.start, p.end);
    p.text = p.bytes.toString('utf8');
    p.lineCount = (p.text.match(/\n/g) ?? []).length + (p.text.endsWith('\n') || !p.text ? 0 : 1);
    p.endLine = p.startLine + p.lineCount - 1;
  }
  // تکهٔ نخست ممکن است خالی باشد اگر سند با سرخطِ بخش آغاز شود؛ چنین تکه‌ای نوشته نمی‌شود.
  return { parts: parts.filter((p) => p.bytes.length > 0), warnings };
}

// ---------------------------------------------------------------- شناسه‌ها
const IDP = 'S[0-9]+[a-z]?|[A-Z][A-Z0-9]*(?:-[A-Z][A-Z0-9]*)*-[0-9]+[a-z]?';
const TOKEN = new RegExp(`(?<![A-Za-z0-9_-])(${IDP})(?![A-Za-z0-9_])`, 'g');
const BUL = '^\\s*(?:[-*]|[0-9]+\\.)\\s+';
const RE_ROW = new RegExp(`^\\|\\s*((?:${IDP})(?:\\s*/\\s*(?:${IDP}))*)\\s*\\|`);
const RE_FEATURE = new RegExp(`${BUL}\\*\\*(${IDP})\\*\\* · (.*)$`);
const RE_LABEL = new RegExp(`${BUL}(?:\\*\\*(${IDP})(?:\\s*[—–]\\s*([^*]*?)|\\s*\\(([^)]*)\\))?:\\*\\*|\`(${IDP})\`:)\\s*(.*)$`);
const RE_SCEN = /^\*\*(S[0-9]+[a-z]?)(?![0-9a-z])[^*·]*·\s*([^*]+)\*\*\s*(.*)$/;
const RE_AMEND = new RegExp(`${BUL}\\*\\*(${IDP})\\s[^*]*\\*\\*`);
export const KEY = new RegExp(`^(?:${IDP})(?:@[0-9]+)?$`);

export const familyOf = (id) => (/^S[0-9]/.test(id) ? 'S' : id.replace(/@.*$/, '').replace(/-[0-9]+[a-z]?$/, ''));
const phaseIn = (s, re = /فاز ([0-9۰-۹]+)/g) => {
  const all = [...String(s ?? '').matchAll(re)];
  return all.length ? Number(toLatin(all.at(-1)[1])) : null;
};
const cellsOf = (l) => l.split('|').slice(1, -1).map((c) => c.trim());

export function tokens(s) {
  return [...String(s).matchAll(TOKEN)].map((m) => m[1]);
}

// «X-01 تا X-16»، «X-01 تا 16» و «S51–S140» به همهٔ شناسه‌های میانه باز می‌شوند.
const RANGE = /([A-Z][A-Z0-9-]*?-|S)([0-9]+)\s*(?:تا|–|—|\.\.)\s*(?:\1)?([0-9]+)(?![0-9])/g;
export function expandRefs(s) {
  const out = new Set();
  const rest = String(s).replace(RANGE, (all, g, a, b) => {
    const w = a.length;
    if (Number(b) > Number(a) && Number(b) - Number(a) < 500) {
      for (let i = Number(a); i <= Number(b); i++) out.add(`${g}${String(i).padStart(w, '0')}`);
      return ' ';
    }
    return all;
  });
  for (const t of tokens(rest)) out.add(t);
  return [...out];
}

export function brief(s, n = 90) {
  s = String(s).replace(/\*\*/g, '').replace(/`/g, '').replace(/\|/g, '¦').replace(/\s+/g, ' ').trim();
  return s.length > n ? s.slice(0, n - 1).trimEnd() + '…' : s;
}

// همهٔ تعریف‌ها، اصلاحیه‌ها، ردیف‌های تصمیم و ارجاع‌ها را از تکه‌ها بیرون می‌کشد.
export function scan(parts) {
  const defs = [];
  const amends = [];
  const decisionRows = [];
  const refs = [];
  for (const p of parts) {
    const ls = p.text.split('\n');
    if (ls.at(-1) === '') ls.pop();
    let fence = false;
    let sub = String(p.section);
    let header = null;
    let table = null;
    const block = (i, stop) => {
      const out = [ls[i]];
      for (let j = i + 1; j < ls.length && stop(ls[j]); j++) out.push(ls[j]);
      return out.join('\n');
    };
    const indented = (l) => /^\s{2,}\S/.test(l);
    for (let i = 0; i < ls.length; i++) {
      const l = ls[i];
      const line = p.startLine + i;
      for (const t of tokens(l)) refs.push({ id: t, line, section: p.section });
      if (/^```/.test(l)) { fence = !fence; continue; }
      if (fence) continue;
      const hm = l.match(/^#{1,4}\s+([0-9۰-۹]+(?:\.[0-9۰-۹]+)*)\.?(?=\s|$)/);
      if (hm) sub = toLatin(hm[1]);
      if (/^\|\s*:?-{3,}/.test(l)) { header = cellsOf(ls[i - 1] ?? ''); table = `${p.file}:${line}`; continue; }
      if (!l.startsWith('|')) { header = null; table = null; }
      const at = { file: p.file, section: p.section, sub, line };
      let m;
      if (header && (m = l.match(RE_ROW))) {
        const cells = cellsOf(l);
        for (const id of m[1].split('/').map((x) => x.trim())) {
          if (familyOf(id) === 'D') decisionRows.push({ id, header, cells, table, ...at });
          else defs.push({
            id, kind: 'row', title: brief(cells[id.startsWith('F0-') ? 2 : 1] ?? ''),
            phase: id.startsWith('F0-') ? 0 : null, prio: id.startsWith('F0-') ? cells[1] : '—', block: l, ...at,
          });
        }
      } else if ((m = l.match(RE_FEATURE))) {
        const b = block(i, indented);
        const pr = b.match(/· (P[0-2]) ·/);
        defs.push({ id: m[1], kind: 'feature', title: brief(m[2].split(' · ')[0]), phase: phaseIn(b), prio: pr ? pr[1] : '?', block: b, ...at });
      } else if ((m = l.match(RE_LABEL))) {
        const id = m[1] ?? m[4];
        const b = block(i, indented);
        defs.push({
          id, kind: familyOf(id) === 'S' ? 'scenario' : 'item', title: brief(m[2] || m[5] || ''),
          phase: m[3] ? phaseIn(m[3]) : null, prio: '—', block: b, ...at,
        });
      } else if ((m = l.match(RE_SCEN))) {
        const b = block(i, (x) => x.trim() !== '' && !RE_SCEN.test(x) && !/^#/.test(x));
        const dm = m[3].match(/^\(([^)]*)\)/);
        defs.push({
          id: m[1], kind: 'scenario', exit: true, title: brief(m[2]), deps: expandRefs(dm ? dm[1] : ''),
          phase: phaseIn(m[3], /خروج فاز ([0-9۰-۹]+)/g), prio: '—', block: b, ...at,
        });
      } else if ((m = l.match(RE_AMEND))) {
        amends.push({ id: m[1], block: block(i, indented), ...at });
      }
    }
  }
  return { defs, amends, decisionRows, refs };
}

// تعریف‌ها را با ردهٔ ثبت‌شده در BLOCKERS.md به ردیف‌های STATUS (کلید) تبدیل می‌کند و همه‌چیز را می‌سنجد.
export function collect(parts, reg = new Map()) {
  const { defs, amends, decisionRows, refs } = scan(parts);
  const errors = [];
  const byId = new Map();
  for (const d of defs) (byId.get(d.id) ?? byId.set(d.id, []).get(d.id)).push(d);

  const entries = [];
  const dups = [];
  for (const [id, ds] of byId) {
    const loc = (d) => ({ file: d.file, section: d.section, sub: d.sub, line: d.line });
    if (ds.length === 1) { entries.push({ ...ds[0], key: id, locs: [loc(ds[0])] }); continue; }
    const cls = reg.get(id) ?? UNREG;
    dups.push({ id, cls, locs: ds.map(loc) });
    if (cls === UNREG) errors.push(`شناسهٔ تکراری ثبت‌نشده: ${id} (${ds.map((d) => `§${d.sub} خط ${d.line}`).join('، ')}) — در BLOCKERS.md رده بده (${SAME} یا ${CLASH})`);
    if (cls === SAME) {
      const ph = [...new Set(ds.map((d) => d.phase).filter((x) => x !== null))];
      if (ph.length > 1) errors.push(`${id}: «${SAME}» ثبت شده ولی فازها ناهم‌خوان‌اند (${ph.join('، ')})`);
      entries.push({
        ...ds[0], key: id, phase: ph[0] ?? null, exit: ds.some((d) => d.exit),
        block: ds.map((d) => d.block).join('\n'), locs: ds.map(loc),
      });
    } else {
      const keys = new Set();
      ds.forEach((d, i) => {
        const key = i === 0 ? id : `${id}@${d.section}`;
        if (keys.has(key)) errors.push(`${id}: دو تعریف در یک بخش (${d.section}) — کلیدِ جدا نمی‌شود ساخت`);
        keys.add(key);
        entries.push({ ...d, key, clashOf: i === 0 ? null : id, locs: [loc(d)] });
      });
    }
  }
  for (const id of reg.keys()) if (!byId.has(id) || byId.get(id).length < 2) errors.push(`BLOCKERS.md: ${id} دیگر چندجا تعریف نشده — ردیفش را بردار (status بزن)`);
  entries.sort((a, b) => a.line - b.line);

  // اصلاحیه‌ها به همان شناسه (تعریفِ نخست) می‌چسبند.
  const byKey = new Map(entries.map((e) => [e.key, e]));
  for (const a of amends) {
    const e = byKey.get(a.id);
    if (!e) { errors.push(`اصلاحیهٔ شناسهٔ تعریف‌نشده: ${a.id} (§${a.sub} خط ${a.line})`); continue; }
    e.amends = [...(e.amends ?? []), { sub: a.sub, line: a.line, file: a.file }];
    e.block += '\n' + a.block;
  }

  // تصمیم‌ها: ردیف‌های یک D در جدول‌های مختلف ادغام می‌شوند؛ در یک جدول دو بار خطاست.
  const decisions = new Map();
  for (const r of decisionRows) {
    const d = decisions.get(r.id) ?? { id: r.id, rows: [] };
    if (d.rows.some((x) => x.table === r.table)) errors.push(`${r.id}: دو بار در یک جدولِ تصمیم (خط ${r.line})`);
    d.rows.push(r);
    decisions.set(r.id, d);
  }
  for (const d of decisions.values()) {
    const col = (re) => {
      for (const r of d.rows) {
        const k = r.header.findIndex((h) => re.test(h));
        if (k > 0 && r.cells[k]) return r.cells[k];
      }
      return '';
    };
    d.title = brief(d.rows[0].cells[1] ?? '', 120);
    d.recommendation = col(/پیشنهاد|توصیه/);
    d.condition = col(/شرط/);
    d.sections = [...new Set(d.rows.map((r) => r.sub))];
  }

  // فاز: قابلیت‌های بخش ۶ و سناریوهای خروجِ بخش ۷ باید فاز داشته باشند (§۲۸.۳).
  for (const e of entries) {
    if (e.kind === 'feature' && e.phase === null) errors.push(`${e.key}: برچسبِ فاز ندارد (§${e.sub} خط ${e.line})`);
    if (e.exit && e.phase === null) errors.push(`${e.key}: سناریوی خروج بی «خروج فاز» (§${e.sub} خط ${e.line})`);
  }
  // وابستگی‌های سناریوهای خروج: شناخته و هم‌فاز یا پیشین.
  for (const s of entries.filter((e) => e.exit)) {
    for (const d of s.deps ?? []) {
      if (familyOf(d) === 'D') { if (!decisions.has(d)) errors.push(`${s.key}: تصمیمِ ناشناخته ${d}`); continue; }
      const t = byKey.get(d);
      if (!t) errors.push(`${s.key}: وابستگیِ ناشناخته ${d}`);
      else if (t.phase !== null && s.phase !== null && t.phase > s.phase) errors.push(`${s.key} (خروج فاز ${s.phase}) به ${d} (فاز ${t.phase}) وابسته است`);
    }
  }
  // ارجاعِ شکسته: شناسه‌ای از خانواده‌ای که تعریف دارد ولی خودش هیچ‌جا تعریف نشده.
  const families = new Set([...byId.keys()].map(familyOf).concat('D'));
  const broken = new Map();
  for (const r of refs) {
    if (!families.has(familyOf(r.id)) || byId.has(r.id) || decisions.has(r.id)) continue;
    (broken.get(r.id) ?? broken.set(r.id, []).get(r.id)).push(r.line);
  }
  for (const [id, ls] of broken) errors.push(`ارجاعِ شکسته: ${id} تعریف نشده (خط ${ls.slice(0, 5).join('، ')}${ls.length > 5 ? '، …' : ''})`);

  // پیوندِ معیارِ پذیرش: هر قابلیتِ بخش ۶ دست‌کم در یک سناریو/آزمون نام برده شود (§۳۹.۹ ج).
  const mentioned = new Set();
  for (const e of entries) if (e.kind !== 'feature') for (const t of [...expandRefs(e.block), ...(e.deps ?? [])]) mentioned.add(t);
  for (const e of entries) {
    e.mentions = [...new Set(expandRefs(e.block))].filter((t) => t !== e.id);
    e.blockers = e.mentions.filter((t) => decisions.has(t));
    if (e.kind === 'feature') e.accepted = mentioned.has(e.id);
  }
  // هر تصمیم چه چیزی را می‌بندد: ردیف‌هایی که در تعریف یا اصلاحیه‌شان نامش آمده، و بخش‌هایی که از آن نام برده‌اند.
  for (const d of decisions.values()) {
    d.blocks = entries.filter((e) => e.blockers.includes(d.id)).map((e) => e.key);
    d.mentionedIn = [...new Set(refs.filter((r) => r.id === d.id).map((r) => r.section))].sort((a, b) => a - b);
  }
  return { entries, decisions, dups, errors, byId };
}

export function counts(data) {
  const fam = new Map();
  for (const [id, ds] of data.byId) {
    const f = familyOf(id);
    const c = fam.get(f) ?? { family: f, ids: 0, defs: 0 };
    c.ids++;
    c.defs += ds.length;
    fam.set(f, c);
  }
  fam.set('D', { family: 'D', ids: data.decisions.size, defs: [...data.decisions.values()].reduce((n, d) => n + d.rows.length, 0) });
  return [...fam.values()];
}

// ---------------------------------------------------------------- manifest.json
export function buildManifest(parts, src) {
  return JSON.stringify({
    note: 'ساختهٔ spec.mjs split؛ دستی ویرایش نکن. 00-index.md و همین فایل بیرون از چسباندن‌اند.',
    source: 'MASTER-SPEC.md',
    bytes: src.length,
    sha256: sha(src),
    parts: parts.map((p) => ({
      file: p.file, section: p.section, title: p.title, lines: [p.startLine, p.endLine],
      bytes: p.bytes.length, sha256: sha(p.bytes), ...(p.obsolete ? { obsolete: true } : {}),
    })),
  }, null, 2) + '\n';
}

// ---------------------------------------------------------------- 00-index.md
const START = [15, 26, 28, 39]; // «بخش‌های ۱۵، ۲۶، ۲۸ و ۳۹ نقطهٔ شروع‌اند» (پیام شروع رسمی)
const EXTRA = {
  0: [2, 9, 10, 11], 1: [1, 3, 4, 9], 2: [1, 5, 9], 3: [3, 9], 4: [3, 4, 9, 10, 11], 5: [3], 6: [3],
};
const ALWAYS = [8, 12, 13, 15, 28, 39];

export function buildIndex(parts, src, data) {
  const files = (secs) => parts.filter((p) => secs.includes(p.section) && !p.name.startsWith('00-')).map((p) => p.file);
  const fa = (s) => toFa(s);
  const rows = parts.map((p) => `| \`${p.file}\` | ${p.obsolete ? '⚠️ **منسوخ — فقط تاریخچه** · ' : ''}${brief(p.title, 80)} | ${fa(p.startLine)}–${fa(p.endLine)} | ${fa(Math.round(p.bytes.length / 102.4) / 10)} KB |`);
  const phased = data.entries.filter((e) => e.phase !== null);
  const phases = [...new Set(phased.map((e) => e.phase))].sort((a, b) => a - b);
  const prow = phases.map((ph) => {
    const its = data.entries.filter((e) => e.phase === ph && e.kind !== 'scenario');
    const scs = data.entries.filter((e) => e.phase === ph && e.kind === 'scenario');
    const want = new Set([...files([...(EXTRA[ph] ?? []), ...ALWAYS]), ...its.flatMap((i) => i.locs.map((l) => l.file)), ...scs.flatMap((s) => s.locs.map((l) => l.file))]);
    const order = parts.map((p) => p.file).filter((f) => want.has(f));
    return `| ${fa(ph)} | ${fa(its.length)} | ${scs.length ? scs.map((s) => s.key).join('، ') : '—'} | ${order.map((f) => `\`${f}\``).join('، ')} |`;
  });
  const famRows = counts(data).map((c) => {
    const where = new Set();
    const amended = new Set();
    for (const e of data.entries) if (familyOf(e.key) === c.family) {
      e.locs.forEach((l) => where.add(l.file));
      (e.amends ?? []).forEach((a) => amended.add(a.file));
    }
    if (c.family === 'D') for (const d of data.decisions.values()) d.rows.forEach((r) => where.add(r.file));
    const order = (set) => parts.map((p) => p.file).filter((f) => set.has(f)).map((f) => `\`${f}\``).join('، ') || '—';
    return `| ${c.family} | ${fa(c.ids)} | ${order(where)} | ${order(amended)} |`;
  });
  const total = data.byId.size + data.decisions.size;
  const unphased = data.entries.filter((e) => e.phase === null);
  const noAcc = data.entries.filter((e) => e.kind === 'feature' && !e.accepted);
  return `# مشخصاتِ جامع نسخهٔ ۳ — نمایه (docs/spec)

> این فایل را \`node docs/spec/tools/spec.mjs split\` می‌سازد؛ دستی ویرایش نکن. منبع فقط \`MASTER-SPEC.md\` (نسخهٔ ۳) است.

**قاعده:** برای هر کار اول همین فایل، بعد **فقط** تکه‌هایی را بخوان که جدول‌های زیر می‌گویند. \`MASTER-SPEC.md\` را
کامل نخوان؛ تکه‌ها بایت‌به‌بایت همان متن‌اند.

- منبعِ حقیقت: \`MASTER-SPEC.md\` (SHA-256 \`${sha(src)}\`، ${fa(src.length)} بایت). فهرستِ تکه‌ها و SHA-256ِ هر کدام در
  \`manifest.json\` است؛ \`check\` می‌سنجد که چسباندنِ تکه‌ها به ترتیبِ آن دقیقاً همان SHA-256ِ اصل است (§۲۸.۲). این
  نمایه و \`manifest.json\` بیرون از چسباندن‌اند.
- نقطهٔ شروع (پیامِ شروعِ رسمی): \`00-start.md\`، سپس ${files(START).map((f) => `\`${f}\``).join('، ')}. قراردادِ رفعِ ابهام و
  تحویل: بخش ۳۹.
- وضعیتِ هر شناسه در \`STATUS.md\`، تصمیم‌های باز و موانع در \`BLOCKERS.md\`. یافتنِ یک شناسه:
  \`grep -n 'POS-14' docs/spec/STATUS.md\` و \`grep -ln 'POS-14' docs/spec/[0-9]*.md\`.
- شمارِ واقعیِ این نسخه (استخراج از متن، نه عددِ تاریخیِ ۴۰۸ یا S01–S144): ${fa(total)} شناسهٔ یکتا در
  ${fa(counts(data).length)} خانواده، ${fa(data.entries.length)} ردیفِ STATUS؛ ${fa(data.entries.filter((e) => e.kind === 'feature').length)} قابلیتِ بخش ۶، ${fa(data.entries.filter((e) => e.kind === 'scenario').length)} سناریو،
  ${fa(data.decisions.size)} دروازهٔ تصمیم. ${fa(data.dups.length)} شناسه چندجا تعریف شده (BLOCKERS.md). ${fa(unphased.length)} ردیف در خودِ سند فاز ندارد؛
  ${fa(noAcc.length)} قابلیتِ بخش ۶ در هیچ سناریو/آزمونی نام برده نشده است.

## کدام تکه برای کدام فاز

| فاز | شناسه‌های غیرِ سناریو | سناریوهای این فاز | تکه‌هایی که باید خوانده شوند |
|---|---|---|---|
${prow.join('\n')}
| بی‌فاز | ${fa(unphased.length)} | — | جدولِ «کدام تکه برای کدام موضوع» |

## کدام تکه برای کدام موضوع (خانوادهٔ شناسه)

| خانواده | شمار | تعریف در | اصلاحیه در |
|---|---|---|---|
${famRows.join('\n')}

## همهٔ تکه‌ها به ترتیب

| پرونده | بخش | خط‌ها در اصل | حجم |
|---|---|---|---|
${rows.join('\n')}
`;
}

// ---------------------------------------------------------------- STATUS.md
const SHEAD = '| شناسه | عنوان | بخش | فاز | اولویت | وضعیت | تأیید: دستور و نتیجه | اتصال بیرونی | مانع | commit/PR |';
const DASH = '—';

export function readStatus(text) {
  const keep = new Map();
  const legacyDecisions = new Map();
  for (const l of String(text ?? '').split('\n')) {
    if (!l.startsWith('|')) continue;
    const c = cellsOf(l);
    if (c.length === 10 && KEY.test(c[0])) {
      keep.set(c[0], { state: c[5], verify: c[6], ext: c[7], blocker: c[8], pr: c[9] });
    } else if (c.length === 6 && KEY.test(c[0]) && LEGACY[c[4]]) {
      // STATUSِ نسخهٔ ۱: | شناسه | عنوان | فاز | اولویت | وضعیت | شواهد |
      const ev = c[5] || DASH;
      const prs = [...new Set(ev.match(/#[0-9]+/g) ?? [])];
      keep.set(c[0], {
        state: LEGACY[c[4]], verify: ev, ext: c[4] === '🔌' ? 'آماده اتصال' : DASH,
        blocker: c[4] === '🔌' ? 'اتصالِ بیرونی' : null, pr: prs.length ? prs.join('، ') : DASH,
      });
    } else if (c.length === 3 && /^D-[0-9]+$/.test(c[0]) && c[2] && c[2] !== 'در انتظار') {
      legacyDecisions.set(c[0], c[2]);
    }
  }
  return { keep, legacyDecisions };
}

function notesOf(text, heading) {
  const h = `\n## ${heading}\n`;
  const i = String(text ?? '').indexOf(h);
  return i < 0 ? DASH : text.slice(i + h.length).trim() || DASH;
}

const secLabel = (e) => [...new Set(e.locs.map((l) => `§${toFa(l.sub)}`))].join('، ');

// «مانع»ِ پیش‌فرض: تصمیم‌هایی که متنِ سند برای این شناسه نام برده و در BLOCKERS.md هنوز باز‌اند.
export const isOpen = (status) => !status || /^باز|هنوز باز/.test(status);

export function buildStatus(data, oldText, srcSha, decisionStatus = new Map()) {
  const { keep } = readStatus(oldText);
  const dflt = (e) => {
    const open = e.blockers.filter((d) => isOpen(decisionStatus.get(d)));
    return open.length ? open.join('، ') : DASH;
  };
  const st = (e) => {
    const k = keep.get(e.key);
    if (k) return k.blocker === null ? { ...k, blocker: dflt(e) } : k;
    return { state: 'NOT-VERIFIED', verify: DASH, ext: DASH, blocker: dflt(e), pr: DASH };
  };
  const fams = [...new Set(data.entries.map((e) => familyOf(e.key)))];
  const sumRow = (label, es) => `| ${label} | ${toFa(es.length)} | ${STATES.map((s) => toFa(es.filter((e) => st(e).state === s).length)).join(' | ')} |`;
  const phases = [...new Set(data.entries.map((e) => e.phase))].sort((a, b) => (a ?? 99) - (b ?? 99));
  let out = `# وضعیتِ شناسه‌ها (STATUS) — مشخصاتِ نسخهٔ ۳

> ساختار را \`node docs/spec/tools/spec.mjs status\` از \`MASTER-SPEC.md\` (SHA-256 \`${srcSha.slice(0, 12)}…\`) می‌سازد.
> ستون‌های **وضعیت**، **تأیید: دستور و نتیجه**، **اتصال بیرونی**، **مانع** و **commit/PR** دستی پر می‌شوند و با اجرای دوباره
> حفظ می‌مانند (§۲۸.۳، §۳۹.۵). وضعیت فقط یکی از: ${STATES.map((s) => `\`${s}\``).join(' · ')}.
> \`NOT-VERIFIED\` یعنی هنوز ممیزی نشده (نه «ندارد»)؛ \`BLOCKED-EXTERNAL\` قبول نیست. \`IMPLEMENTED-VERIFIED\` بی دستور/نتیجهٔ
> آزمون و بی commit/PR، و \`BLOCKED-EXTERNAL\` بی «مانع» ممنوع است. اتصال بیرونی: نامرتبط/آماده اتصال/sandbox آزموده/زنده تأییدشده.
> کلیدِ \`X@N\` یعنی شناسهٔ X که در بخش N با معنای دیگری دوباره تعریف شده (قاعدهٔ ID@بخش در BLOCKERS.md): هر دو
> نیازمندی جدا پیگیری می‌شوند و متنِ سند شماره‌گذاریِ دوباره نمی‌شود. تصمیم‌های D-* و موانع در \`BLOCKERS.md\`. «مانع»ِ پیش‌فرض همان D-هایی است که متنِ سند برای آن شناسه نام برده.

## خلاصه به تفکیکِ خانواده

| خانواده | شمار | ${STATES.join(' | ')} |
|---|---|${STATES.map(() => '---').join('|')}|
${fams.map((f) => sumRow(f, data.entries.filter((e) => familyOf(e.key) === f))).join('\n')}
${sumRow('**همه**', data.entries)}

## خلاصه به تفکیکِ فاز

| فاز | شمار | ${STATES.join(' | ')} |
|---|---|${STATES.map(() => '---').join('|')}|
${phases.map((ph) => sumRow(ph === null ? 'بی‌فاز در سند' : toFa(ph), data.entries.filter((e) => e.phase === ph))).join('\n')}

`;
  for (const f of fams) {
    out += `## ${f}\n\n${SHEAD}\n|---|---|---|---|---|---|---|---|---|---|\n`;
    for (const e of data.entries.filter((x) => familyOf(x.key) === f)) {
      const s = st(e);
      out += `| ${e.key} | ${e.title || DASH} | ${secLabel(e)} | ${e.phase === null ? DASH : toFa(e.phase)} | ${e.prio || DASH} | ${s.state} | ${s.verify} | ${s.ext} | ${s.blocker} | ${s.pr} |\n`;
    }
    out += '\n';
  }
  out += `## یادداشت‌ها\n\n${notesOf(oldText, 'یادداشت‌ها')}\n`;
  return out;
}

export function verifyStatus(data, text) {
  const errs = [];
  const { keep } = readStatus(text);
  const keys = new Set(data.entries.map((e) => e.key));
  for (const e of data.entries) {
    const k = keep.get(e.key);
    if (!k) { errs.push(`STATUS.md: ${e.key} نیست — status بزن`); continue; }
    if (!STATES.includes(k.state)) errs.push(`STATUS.md: ${e.key} وضعیتِ نامعتبر «${k.state}»`);
    if (k.state === 'IMPLEMENTED-VERIFIED' && (k.verify === DASH || !k.verify || k.pr === DASH || !k.pr)) errs.push(`STATUS.md: ${e.key} IMPLEMENTED-VERIFIED است ولی دستور/نتیجهٔ آزمون یا commit/PR ندارد`);
    if (k.state === 'BLOCKED-EXTERNAL' && (k.blocker === DASH || !k.blocker)) errs.push(`STATUS.md: ${e.key} BLOCKED-EXTERNAL بی «مانع»`);
  }
  for (const k of keep.keys()) if (!keys.has(k)) errs.push(`STATUS.md: ردیفِ ${k} در سند نیست — status بزن`);
  return errs;
}

// ---------------------------------------------------------------- BLOCKERS.md
const OTHER = 'موانعِ دیگر (دستی)';

// وضعیتِ هر شناسهٔ چندجاتعریف‌شده: «حل‌شده…» یا «باز…». «برخورد» با قاعدهٔ ID@بخش حل می‌شود: هر دو نیازمندی می‌مانند،
// متنِ سند شماره‌گذاریِ دوباره نمی‌شود و تعریفِ دوم در همه‌جا `ID@<بخش>` است.
export const RESOLVED_CLASH = 'حل‌شده بنا بر قاعدهٔ ID@بخش';
const DUP_STATE = /^(حل‌شده|باز)/;

export function readBlockers(text) {
  const decisions = new Map();
  const reg = new Map();
  const regNote = new Map();
  const regStatus = new Map();
  let sec = '';
  for (const l of String(text ?? '').split('\n')) {
    if (l.startsWith('## ')) sec = l;
    if (!l.startsWith('|')) continue;
    const c = cellsOf(l);
    if (sec.includes('دروازه‌های تصمیم') && c.length === 6 && /^D-[0-9]+$/.test(c[0])) decisions.set(c[0], c[5]);
    // | شناسه | محل‌ها | کلید در STATUS | رده | وضعیت | توضیح |  (شکلِ پنج‌ستونیِ پیشین بی «وضعیت» هم خوانده می‌شود)
    if (sec.includes('چندجا') && (c.length === 6 || c.length === 5) && KEY.test(c[0])) {
      if (c[3] === SAME || c[3] === CLASH) reg.set(c[0], c[3]);
      if (c.length === 6) regStatus.set(c[0], c[4]);
      regNote.set(c[0], c.at(-1));
    }
  }
  return { decisions, reg, regNote, regStatus };
}

export function buildBlockers(data, oldText, legacyDecisions = new Map()) {
  const old = readBlockers(oldText);
  const list = (xs, n = 14) => (xs.length > n ? `${xs.slice(0, n).join('، ')} و ${toFa(xs.length - n)} شناسهٔ دیگر` : xs.join('، ')) || DASH;
  const cell = (s) => brief(s || DASH, 400);
  const ds = [...data.decisions.values()].sort((a, b) => Number(a.id.slice(2)) - Number(b.id.slice(2)));
  let out = `# موانع و تصمیم‌های باز (BLOCKERS) — مشخصاتِ نسخهٔ ۳

> جدول‌ها را \`node docs/spec/tools/spec.mjs status\` از \`MASTER-SPEC.md\` می‌سازد؛ ستون‌های **وضعیت**، **رده** و
> **توضیح** دستی‌اند و حفظ می‌مانند. بخشِ «${OTHER}» دست‌نخورده می‌ماند. \`check\` خطا می‌دهد اگر تصمیمی از سند این‌جا
> نباشد یا شناسهٔ تکراری رده نگرفته باشد (§۳۹.۹ ب و د).
> §۳۹.۲–۳۹.۳: هر تصمیمِ باز مانعِ همان قابلیت است، نه مجوزِ حدس. صاحب محصول گفته «نپرس، توصیهٔ سند را بپذیر»: جایی که
> سند توصیه دارد «پذیرفته بنا بر توصیهٔ سند — قابل بازگشت» با تاریخ ثبت می‌شود؛ جایی که توصیه ندارد، یا خودِ توصیه عدد،
> حساب، قرارداد یا تأییدِ بیرونی می‌خواهد، آن بخش باز می‌ماند و فقط بخشِ مستقل و بی‌خطر (پرچمِ خاموش) ساخته می‌شود.

## ۱. دروازه‌های تصمیم (D-*) — از جدول‌های بخش ۱۳ و ۱۵.۱۱

| شناسه | تصمیم | توصیهٔ سند | شرطِ پیش از انتشارِ عمومی (§۱۵.۱۱) | چه چیزی را می‌بندد | وضعیت |
|---|---|---|---|---|---|
`;
  for (const d of ds) {
    const blocks = `${list(d.blocks)}${d.mentionedIn.length ? `؛ نام در ${d.mentionedIn.map((s) => `§${toFa(s)}`).join('، ')}` : ''}`;
    const status = old.decisions.get(d.id) ?? legacyDecisions.get(d.id) ?? 'باز — در انتظار';
    out += `| ${d.id} | ${cell(d.title)} | ${cell(d.recommendation)} | ${cell(d.condition)} | ${blocks} | ${status} |\n`;
  }
  out += `
## ۲. شناسه‌های چندجاتعریف‌شده (§۳۹.۹ ب)

رده: «${SAME}» = یک الزام که دو جا آمده (یک ردیف در STATUS)؛ «${CLASH}» = دو الزامِ متفاوت با یک شناسه. «${UNREG}» خطاست.

**قاعدهٔ ID@بخش (۱۴۰۵/۰۷/۲۵؛ صاحب محصول: «نپرس، بهترین را بردار»):** هر دو نیازمندی می‌مانند و متنِ سند شماره‌گذاریِ
دوباره نمی‌شود. تعریفِ نخست همان شناسه است و هر تعریفِ بعدی \`ID@<بخش>\` (مثلاً \`INV-01@32\`، \`OPS-01@29\`، \`INV-01@38\`)؛
در STATUS، PR و کامیت با همین کلید نام برده می‌شود. وضعیت فقط «حل‌شده…» یا «باز…» است.

| شناسه | محل‌ها | کلید در STATUS | رده | وضعیت | توضیح |
|---|---|---|---|---|---|
`;
  for (const d of data.dups) {
    const keys = data.entries.filter((e) => e.key === d.id || e.clashOf === d.id).map((e) => e.key);
    const state = old.regStatus.get(d.id) ?? 'باز — در انتظار';
    out += `| ${d.id} | ${d.locs.map((l) => `§${toFa(l.sub)} (خط ${toFa(l.line)})`).join('، ')} | ${keys.join('، ')} | ${d.cls} | ${state} | ${old.regNote.get(d.id) ?? DASH} |\n`;
  }
  out += `\n## ${OTHER}\n\n${notesOf(oldText, OTHER)}\n`;
  return out;
}

export function verifyBlockers(data, text) {
  const errs = [];
  const { decisions } = readBlockers(text);
  for (const id of data.decisions.keys()) if (!decisions.has(id)) errs.push(`BLOCKERS.md: تصمیمِ ${id} نیست — status بزن`);
  for (const [id, s] of decisions) if (!s || s === DASH) errs.push(`BLOCKERS.md: ${id} وضعیت ندارد`);
  const { reg, regStatus } = readBlockers(text);
  for (const d of data.dups) {
    const s = regStatus.get(d.id) ?? '';
    if (!DUP_STATE.test(s)) errs.push(`BLOCKERS.md: وضعیتِ ${d.id} باید «حل‌شده…» یا «باز…» باشد`);
    else if (reg.get(d.id) === CLASH && s.startsWith('حل‌شده') && !s.startsWith(RESOLVED_CLASH)) errs.push(`BLOCKERS.md: «${CLASH}»ِ ${d.id} فقط با «${RESOLVED_CLASH}» حل می‌شود`);
  }
  return errs;
}

// ---------------------------------------------------------------- پرونده‌های روی دیسک
function verifyDisk(parts, src) {
  const errs = [];
  if (!existsSync(MANIFEST)) return ['manifest.json نیست — split بزن'];
  let man;
  try { man = JSON.parse(readFileSync(MANIFEST, 'utf8')); } catch { return ['manifest.json خراب است — split بزن']; }
  const bufs = [];
  for (const e of man.parts ?? []) {
    const fp = join(DIR, e.file);
    if (!existsSync(fp)) { errs.push(`${e.file} نیست`); continue; }
    const b = readFileSync(fp);
    if (sha(b) !== e.sha256) errs.push(`${e.file}: SHA-256 با manifest.json نمی‌خواند — تکه دستی ویرایش شده`);
    bufs.push(b);
  }
  // شرطِ توقف (§۲۸.۲): چسباندنِ تکه‌های روی دیسک به ترتیبِ manifest = خودِ اصل.
  if (sha(Buffer.concat(bufs)) !== sha(src) || man.sha256 !== sha(src)) {
    return [`توقف: SHA-256ِ چسباندنِ ${bufs.length} تکهٔ manifest.json با MASTER-SPEC.md یکی نیست — split بزن`, ...errs];
  }
  if (readFileSync(MANIFEST, 'utf8') !== buildManifest(parts, src)) errs.push('manifest.json کهنه است — split بزن');
  // §۳۹.۹ و: پروندهٔ ناشناخته در docs/spec نباشد؛ هر چه در obsolete/ است برچسبِ «منسوخ» داشته باشد.
  const want = new Set(parts.map((p) => p.file));
  for (const f of readdirSync(DIR)) {
    if (!want.has(f) && !ALLOWED.has(f)) errs.push(`پروندهٔ ناشناخته در docs/spec: ${f} — اگر منسوخ است به docs/spec/obsolete/ با برچسبِ «منسوخ» ببر`);
  }
  if (existsSync(OBSOLETE_DIR) && statSync(OBSOLETE_DIR).isDirectory()) {
    for (const f of readdirSync(OBSOLETE_DIR)) {
      const head = readFileSync(join(OBSOLETE_DIR, f), 'utf8').split('\n').slice(0, 3).join('\n');
      if (!/منسوخ/.test(head)) errs.push(`obsolete/${f}: برچسبِ «منسوخ» در سه خطِ نخست نیست`);
    }
  }
  return errs;
}

// ---------------------------------------------------------------- اجرا
function load() {
  const src = readFileSync(SRC);
  const blockersText = existsSync(BLOCKERS) ? readFileSync(BLOCKERS, 'utf8') : '';
  const { parts, warnings } = splitParts(src);
  if (sha(Buffer.concat(parts.map((p) => p.bytes))) !== sha(src)) throw new Error('توقف: چسباندنِ تکه‌ها در حافظه با اصل یکی نیست');
  const data = collect(parts, readBlockers(blockersText).reg);
  return { src, parts, warnings, data, blockersText };
}

function main(cmd) {
  let L;
  try { L = load(); } catch (e) { console.error(e.message); process.exit(1); }
  const { src, parts, warnings, data, blockersText } = L;
  for (const w of warnings) console.warn(`هشدار: ${w}`);
  if (cmd === 'split') {
    const keepFiles = new Set(['MASTER-SPEC.md', 'STATUS.md', 'BLOCKERS.md', '00-index.md']);
    for (const f of readdirSync(DIR)) if (f.endsWith('.md') && !keepFiles.has(f) && /^[0-9]{2}-/.test(f)) unlinkSync(join(DIR, f));
    if (existsSync(join(DIR, 'parts.txt'))) unlinkSync(join(DIR, 'parts.txt')); // جایش را manifest.json گرفته
    for (const p of parts) writeFileSync(join(DIR, p.file), p.bytes);
    writeFileSync(MANIFEST, buildManifest(parts, src));
    writeFileSync(INDEX, buildIndex(parts, src, data));
    console.log(`${parts.length} تکه، SHA-256 ${sha(src)}${data.errors.length ? ` — ${data.errors.length} خطای شناسه (check بزن)` : ''}`);
  } else if (cmd === 'status') {
    const old = existsSync(STATUS) ? readFileSync(STATUS, 'utf8') : '';
    const { keep, legacyDecisions } = readStatus(old);
    const keys = new Set(data.entries.map((e) => e.key));
    for (const [k, v] of keep) if (!keys.has(k)) console.warn(`هشدار: ردیفِ ${k} دیگر در سند نیست و برداشته شد (${v.state}؛ ${v.verify})`);
    const blockersNew = buildBlockers(data, blockersText, legacyDecisions);
    writeFileSync(BLOCKERS, blockersNew);
    writeFileSync(STATUS, buildStatus(data, old, sha(src), readBlockers(blockersNew).decisions));
    console.log(`STATUS.md: ${data.entries.length} ردیف؛ BLOCKERS.md: ${data.decisions.size} تصمیم، ${data.dups.length} شناسهٔ چندجاتعریف‌شده`);
  } else if (cmd === 'check') {
    const disk = verifyDisk(parts, src);
    if (disk[0]?.startsWith('توقف')) { console.error(disk.join('\n')); process.exit(1); }
    const all = [...disk, ...data.errors];
    if (existsSync(INDEX) && readFileSync(INDEX, 'utf8') !== buildIndex(parts, src, data)) all.push('00-index.md کهنه است — split بزن');
    if (!existsSync(INDEX)) all.push('00-index.md نیست — split بزن');
    const statusText = existsSync(STATUS) ? readFileSync(STATUS, 'utf8') : '';
    all.push(...verifyStatus(data, statusText));
    if (statusText && statusText !== buildStatus(data, statusText, sha(src), readBlockers(blockersText).decisions)) all.push('STATUS.md با سند نمی‌خواند — status بزن');
    if (!blockersText) all.push('BLOCKERS.md نیست — status بزن');
    else {
      all.push(...verifyBlockers(data, blockersText));
      if (blockersText !== buildBlockers(data, blockersText)) all.push('BLOCKERS.md با سند نمی‌خواند — status بزن');
    }
    if (all.length) { console.error(all.join('\n')); process.exit(1); }
    const noAcc = data.entries.filter((e) => e.kind === 'feature' && !e.accepted).length;
    console.log(`سالم: ${parts.length} تکه = اصل (SHA-256 ${sha(src).slice(0, 12)}…)، ${data.byId.size + data.decisions.size} شناسهٔ یکتا (${data.byId.size} در STATUS با ${data.entries.length} ردیف، ${data.decisions.size} تصمیم در BLOCKERS)؛ ${data.dups.length} شناسهٔ چندجاتعریف‌شدهٔ رده‌دار؛ بی ارجاعِ شکسته.`);
    if (noAcc) console.warn(`هشدار (§۳۹.۹ ج): ${noAcc} قابلیتِ بخش ۶ در هیچ سناریو/آزمونی نام برده نشده است.`);
  } else if (cmd === 'ids') {
    for (const c of counts(data)) console.log(`${c.family}\t${c.ids}\t(تعریف: ${c.defs})`);
    console.log(`همه\t${data.byId.size + data.decisions.size}\t(ردیفِ STATUS: ${data.entries.length})`);
    if (data.errors.length) console.error(data.errors.join('\n'));
  } else {
    console.error('استفاده: spec.mjs split | status | check | ids');
    process.exit(2);
  }
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) main(process.argv[2]);
