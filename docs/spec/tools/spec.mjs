#!/usr/bin/env node
// ابزارِ docs/spec — شکستنِ MASTER-SPEC.md، ساختِ 00-index.md و STATUS.md، و سنجش.
//
//   node docs/spec/tools/spec.mjs split    ⇐ تکه‌ها + 00-index.md + parts.txt را از نو می‌سازد
//   node docs/spec/tools/spec.mjs status   ⇐ STATUS.md را می‌سازد/تازه می‌کند (وضعیت و شواهدِ موجود حفظ می‌شود)
//   node docs/spec/tools/spec.mjs check    ⇐ بی‌نوشتن می‌سنجد؛ با خطا کدِ ۱ برمی‌گرداند
//
// ⛔ منبعِ حقیقت MASTER-SPEC.md است. تکه‌ها دستی ویرایش نمی‌شوند؛ اصل را عوض کن و split بزن.
// ⛔ ستون‌های «وضعیت» و «شواهد» در STATUS.md دستی پر می‌شوند و status آن‌ها را دور نمی‌ریزد.

import { readFileSync, writeFileSync, existsSync, readdirSync, unlinkSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const DIR = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(DIR, 'MASTER-SPEC.md');
const PARTS_LIST = join(DIR, 'parts.txt');
const INDEX = join(DIR, '00-index.md');
const STATUS = join(DIR, 'STATUS.md');

const FA = '۰۱۲۳۴۵۶۷۸۹';
const toLatin = (s) => s.replace(/[۰-۹]/g, (d) => String(FA.indexOf(d)));
const toFa = (n) => String(n).replace(/[0-9]/g, (d) => FA[d]);
const sha = (buf) => createHash('sha256').update(buf).digest('hex');

// نامِ پرونده برای هر بخشِ «## N.»
const TOP = {
  1: '01-goals', 2: '02-current', 3: '03-architecture', 4: '04-afghanistan', 5: '05-ux',
  8: '08-phases', 9: '09-testing', 10: '10-security', 11: '11-operations',
  12: '12-report', 13: '13-decisions', 14: '14-sources',
};

// ---------------------------------------------------------------- شکستن
// خط‌ها با پایانِ خطشان نگه داشته می‌شوند تا چسباندنِ تکه‌ها بایت‌به‌بایت همان اصل شود.
function splitParts(text) {
  const lines = text.match(/[^\n]*\n|[^\n]+$/g) ?? [];
  const parts = [];
  let cur = null;
  let fence = false;
  let top = 0; // شمارهٔ بخشِ «## N.» جاری
  const open = (name, title) => {
    cur = { name, title, lines: [] };
    parts.push(cur);
  };
  open('00-intro', 'مقدمه و پیام شروع');
  for (const line of lines) {
    if (/^```/.test(line)) fence = !fence;
    if (!fence) {
      const h2 = line.match(/^## ([۰-۹]+)\. (.+?)\s*$/);
      const h3 = line.match(/^### ([۰-۹]+)\.([۰-۹]+) (.+?)\s*$/);
      if (h2) {
        top = Number(toLatin(h2[1]));
        if (top === 6) open('06-intro', `${h2[1]}. ${h2[2]} — راهنما`);
        else if (top === 7) open('07-0', `${h2[1]}. ${h2[2]} — قواعد`);
        else if (TOP[top]) open(TOP[top], `${h2[1]}. ${h2[2]}`);
        else throw new Error(`بخشِ ناشناخته: ${line}`);
      } else if (h3 && (top === 6 || top === 7)) {
        const sub = Number(toLatin(h3[2]));
        if (top === 6) {
          const g = h3[3].match(/\(([A-Z]+)\)/);
          if (!g) throw new Error(`گروهِ بی‌کد در بخش ۶: ${line}`);
          open(`06-${g[1]}`, `${h3[1]}.${h3[2]} ${h3[3]}`);
        } else {
          open(`07-${sub}`, `${h3[1]}.${h3[2]} ${h3[3]}`);
        }
      }
    }
    cur.lines.push(line);
  }
  if (fence) throw new Error('یک ``` بسته نشده است');
  const seen = new Set();
  for (const p of parts) {
    if (seen.has(p.name)) throw new Error(`نامِ تکراری: ${p.name}`);
    seen.add(p.name);
    p.file = `${p.name}.md`;
    p.text = p.lines.join('');
  }
  return parts;
}

// ---------------------------------------------------------------- شناسه‌ها
const RANGE = /([A-Z]+[0-9]?)-([0-9]+) تا (?:\1-)?([0-9]+)/g;

function expandRefs(s) {
  const out = new Set();
  s = s.replace(RANGE, (_, g, a, b) => {
    const w = a.length;
    for (let i = Number(a); i <= Number(b); i++) out.add(`${g}-${String(i).padStart(w, '0')}`);
    return ' ';
  });
  for (const m of s.matchAll(/\b([A-Z]+[0-9]?-[0-9]+[a-z]?)\b/g)) out.add(m[1]);
  return [...out];
}

function collect(parts) {
  const items = []; // {id, group, phase, prio, title, file}
  const scenarios = []; // {id, phase, deps, title, file}
  const decisions = []; // {id, title}
  for (const p of parts) {
    const ls = p.text.split('\n');
    if (p.name === '02-current') {
      for (const l of ls) {
        const m = l.match(/^\| (F0-[0-9]+) \| (\S+) \| (.+?) \|/);
        if (m) items.push({ id: m[1], group: 'F0', phase: 0, prio: m[2], title: brief(m[3]), file: p.file });
      }
    }
    if (p.name.startsWith('06-') && p.name !== '06-intro') {
      for (let i = 0; i < ls.length; i++) {
        const m = ls[i].match(/^- \*\*([A-Z]+-[0-9]+)\*\* · (.*)$/);
        if (!m) continue;
        let block = ls[i];
        for (let j = i + 1; j < ls.length && /^\s{2,}\S/.test(ls[j]); j++) block += '\n' + ls[j];
        const ph = [...block.matchAll(/فاز ([۰-۹]+)/g)];
        const pr = block.match(/· (P[0-2]) ·/);
        items.push({
          id: m[1], group: m[1].split('-')[0],
          phase: ph.length ? Number(toLatin(ph.at(-1)[1])) : null,
          prio: pr ? pr[1] : '?', title: brief(m[2].split(' · ')[0]), file: p.file,
        });
      }
    }
    if (p.name.startsWith('07-')) {
      for (const l of ls) {
        const m = l.match(/^\*\*(S[0-9]+[a-z]?)[^*]*?· ([^*]+)\*\*\s*(?:\(([^)]*)\))?.*?خروج فاز ([۰-۹]+)/);
        if (m) scenarios.push({ id: m[1], title: m[2].trim(), deps: expandRefs(m[3] ?? ''), phase: Number(toLatin(m[4])), file: p.file });
      }
    }
    if (p.name === '13-decisions') {
      for (const l of ls) {
        const m = l.match(/^\| (D-[0-9]+) \| (.+?) \|/);
        if (m) decisions.push({ id: m[1], title: brief(m[2]) });
      }
    }
  }
  return { items, scenarios, decisions };
}

function brief(s, n = 90) {
  s = s.replace(/\*\*/g, '').replace(/`/g, '').replace(/\|/g, '¦').replace(/\s+/g, ' ').trim();
  return s.length > n ? s.slice(0, n - 1).trimEnd() + '…' : s;
}

// ---------------------------------------------------------------- سنجش‌ها
function verify(parts, src, data) {
  const errs = [];
  const joined = Buffer.from(parts.map((p) => p.text).join(''), 'utf8');
  if (sha(joined) !== sha(src)) errs.push('چسباندنِ تکه‌ها با MASTER-SPEC.md یکی نیست');

  const phaseOf = new Map();
  for (const it of [...data.items]) {
    if (it.phase === null) errs.push(`${it.id}: برچسبِ فاز ندارد`);
    if (phaseOf.has(it.id)) errs.push(`${it.id}: بیش از یک بار تعریف شده`);
    phaseOf.set(it.id, it.phase);
  }
  const sSeen = new Set();
  for (const s of data.scenarios) {
    if (sSeen.has(s.id)) errs.push(`${s.id}: سناریوی تکراری`);
    sSeen.add(s.id);
    for (const d of s.deps) {
      // D-xx دروازهٔ تصمیم است، نه قابلیت؛ فقط باید در بخش ۱۳ باشد.
      if (/^D-/.test(d)) {
        if (!data.decisions.some((x) => x.id === d)) errs.push(`${s.id}: تصمیمِ ناشناخته ${d}`);
      } else if (!phaseOf.has(d)) errs.push(`${s.id}: وابستگیِ ناشناخته ${d}`);
      else if (phaseOf.get(d) > s.phase) errs.push(`${s.id} (خروج فاز ${s.phase}) به ${d} (فاز ${phaseOf.get(d)}) وابسته است`);
    }
  }
  return errs;
}

// تکه‌های روی دیسک باید همان باشند که از اصل درمی‌آید، و چسباندنشان همان SHA.
function verifyDisk(parts, src) {
  const errs = [];
  if (!existsSync(PARTS_LIST)) return ['parts.txt نیست — split بزن'];
  const list = readFileSync(PARTS_LIST, 'utf8').split('\n').filter(Boolean);
  const want = parts.map((p) => p.file);
  if (list.join() !== want.join()) errs.push('parts.txt با تکه‌های اصل نمی‌خواند — split بزن');
  const bufs = [];
  for (const f of list) {
    const fp = join(DIR, f);
    if (!existsSync(fp)) { errs.push(`${f} نیست`); continue; }
    bufs.push(readFileSync(fp));
  }
  if (sha(Buffer.concat(bufs)) !== sha(src)) errs.push(`SHA-256ِ چسباندنِ ${list.length} تکه با اصل یکی نیست — تکه دستی ویرایش شده یا split زده نشده`);
  return errs;
}

// ---------------------------------------------------------------- 00-index.md
// پرونده‌هایی که برای هر فاز، جدا از شناسه‌ها و سناریوهایش، باید خوانده شوند.
const ALWAYS = ['08-phases.md', '12-report.md', '13-decisions.md'];
const EXTRA = {
  0: ['00-intro.md', '02-current.md', '09-testing.md', '10-security.md', '11-operations.md'],
  1: ['01-goals.md', '03-architecture.md', '04-afghanistan.md', '09-testing.md'],
  2: ['01-goals.md', '05-ux.md', '09-testing.md'],
  3: ['03-architecture.md', '09-testing.md'],
  4: ['03-architecture.md', '04-afghanistan.md', '09-testing.md', '10-security.md', '11-operations.md'],
  5: ['03-architecture.md'],
  6: ['03-architecture.md'],
};

function buildIndex(parts, src, data) {
  const lineCount = (t) => (t.match(/\n/g) ?? []).length;
  let start = 1;
  const rows = parts.map((p) => {
    const n = lineCount(p.text);
    const r = `| \`${p.file}\` | ${p.title} | ${toFa(start)}–${toFa(start + n - 1)} | ${toFa(Math.round(Buffer.byteLength(p.text) / 1024 * 10) / 10)} KB |`;
    start += n;
    return r;
  });
  const phases = [...new Set([...data.items, ...data.scenarios].map((x) => x.phase))].sort((a, b) => a - b);
  const prow = phases.map((ph) => {
    const its = data.items.filter((i) => i.phase === ph);
    const scs = data.scenarios.filter((s) => s.phase === ph);
    const files = new Set([...(EXTRA[ph] ?? []), ...its.map((i) => i.file), ...scs.map((s) => s.file), ...ALWAYS]);
    const order = parts.map((p) => p.file).filter((f) => files.has(f));
    return `| ${toFa(ph)} | ${toFa(its.length)} | ${scs.length ? scs.map((s) => s.id).join('، ') : '—'} | ${order.map((f) => `\`${f}\``).join('، ')} |`;
  });
  return `# مشخصاتِ جامع — فهرست (docs/spec)

> این فایل را \`node docs/spec/tools/spec.mjs split\` می‌سازد؛ دستی ویرایش نکن.

**قاعده:** برای کار روی هر فاز یا شناسه، اول همین فایل را بخوان و بعد **فقط** پرونده‌هایی را که جدولِ
«کدام پرونده برای کدام فاز» می‌گوید. \`MASTER-SPEC.md\` را کامل نخوان؛ تکه‌ها همان متن‌اند.

- منبعِ حقیقت: \`MASTER-SPEC.md\` (SHA-256 \`${sha(src)}\`). تکه‌ها دستی ویرایش نمی‌شوند: اصل را عوض کن و
  \`split\` بزن. \`check\` می‌سنجد که چسباندنِ تکه‌ها به ترتیبِ \`parts.txt\` بایت‌به‌بایت همان اصل است.
- وضعیتِ هر شناسه در \`STATUS.md\` است. برای یافتنِ یک شناسه: \`grep -n 'POS-14' docs/spec/STATUS.md docs/spec/0*.md\`.
- شمار: ${toFa(data.items.length)} شناسه (${toFa(data.items.filter((i) => i.group === 'F0').length)} مورد F0 و ${toFa(data.items.filter((i) => i.group !== 'F0').length)} قابلیت)، ${toFa(data.scenarios.length)} سناریوی خروج، ${toFa(data.decisions.length)} دروازهٔ تصمیم.

## کدام پرونده برای کدام فاز

| فاز | شناسه‌ها | سناریوهای خروج | پرونده‌هایی که باید خوانده شوند |
|---|---|---|---|
${prow.join('\n')}

## همهٔ تکه‌ها به ترتیب

| پرونده | بخش | خط‌ها در اصل | حجم |
|---|---|---|---|
${rows.join('\n')}
`;
}

// ---------------------------------------------------------------- STATUS.md
const STATES = ['✅', '🟡', '🔌', '⛔'];

function readStatus() {
  const keep = new Map();
  if (!existsSync(STATUS)) return keep;
  for (const l of readFileSync(STATUS, 'utf8').split('\n')) {
    const c = l.split('|').map((x) => x.trim());
    // | شناسه | ... | وضعیت | شواهد |
    if (c.length >= 4 && /^(?:[A-Z]+[0-9]?-[0-9]+|S[0-9]+[a-z]?)$/.test(c[1])) {
      keep.set(c[1], { state: c[c.length - 3], evidence: c[c.length - 2] });
    }
  }
  return keep;
}

function buildStatus(data) {
  const keep = readStatus();
  const st = (id) => keep.get(id) ?? { state: '⛔', evidence: '—' };
  const groups = [...new Set(data.items.map((i) => i.group))];
  const count = (pred) => {
    const all = [...data.items, ...data.scenarios].filter(pred);
    return STATES.map((s) => `${s} ${toFa(all.filter((x) => st(x.id).state === s).length)}`).join(' · ');
  };
  const phases = [...new Set(data.items.map((i) => i.phase))].sort((a, b) => a - b);
  let out = `# وضعیتِ شناسه‌ها (STATUS)

> ساختار را \`node docs/spec/tools/spec.mjs status\` می‌سازد. فقط ستون‌های **وضعیت** و **شواهد** دستی
> پر می‌شوند و با اجرای دوباره حفظ می‌مانند. برچسب‌ها فقط این چهارتا: ✅ کامل و آزموده · 🟡 ناقص ·
> 🔌 آماده اتصال · ⛔ شروع نشده. ✅ بی‌شواهد (PR، آزمون، اندازه‌گیری) ممنوع است (بخش ۱۲).

## خلاصه

| فاز | وضعیت |
|---|---|
${phases.map((ph) => `| ${toFa(ph)} | ${count((x) => x.phase === ph)} |`).join('\n')}

`;
  for (const g of groups) {
    const its = data.items.filter((i) => i.group === g);
    out += `## ${g}\n\n| شناسه | عنوان | فاز | اولویت | وضعیت | شواهد |\n|---|---|---|---|---|---|\n`;
    for (const i of its) out += `| ${i.id} | ${i.title} | ${toFa(i.phase)} | ${i.prio} | ${st(i.id).state} | ${st(i.id).evidence} |\n`;
    out += '\n';
  }
  out += `## سناریوهای خروج\n\n| شناسه | عنوان | فاز | وابسته به | وضعیت | شواهد |\n|---|---|---|---|---|---|\n`;
  for (const s of data.scenarios) out += `| ${s.id} | ${brief(s.title)} | ${toFa(s.phase)} | ${s.deps.join('، ')} | ${st(s.id).state} | ${st(s.id).evidence} |\n`;
  out += `\n## دروازه‌های تصمیم (بخش ۱۳)\n\nجواب‌های صاحب محصول این‌جا با تاریخ ثبت می‌شوند.\n\n| شناسه | تصمیم | جواب و تاریخ |\n|---|---|---|\n`;
  const dkeep = new Map();
  if (existsSync(STATUS)) {
    for (const l of readFileSync(STATUS, 'utf8').split('\n')) {
      const c = l.split('|').map((x) => x.trim());
      if (c.length >= 4 && /^D-[0-9]+$/.test(c[1])) dkeep.set(c[1], c[3]);
    }
  }
  for (const d of data.decisions) out += `| ${d.id} | ${d.title} | ${dkeep.get(d.id) ?? 'در انتظار'} |\n`;
  out += `\n## یادداشت‌ها\n\n${existsSync(STATUS) ? notesOf(readFileSync(STATUS, 'utf8')) : '—'}\n`;
  return out;
}

// بخشِ «یادداشت‌ها» آزاد است و دست‌نخورده می‌ماند.
function notesOf(text) {
  const i = text.indexOf('\n## یادداشت‌ها\n');
  return i < 0 ? '—' : text.slice(i + '\n## یادداشت‌ها\n'.length).trim() || '—';
}

function verifyStatus(data) {
  const errs = [];
  if (!existsSync(STATUS)) return ['STATUS.md نیست — status بزن'];
  const keep = readStatus();
  for (const x of [...data.items, ...data.scenarios]) {
    const k = keep.get(x.id);
    if (!k) errs.push(`STATUS.md: ${x.id} نیست — status بزن`);
    else if (!STATES.includes(k.state)) errs.push(`STATUS.md: ${x.id} وضعیتِ نامعتبر «${k.state}»`);
    else if (k.state === '✅' && (!k.evidence || k.evidence === '—')) errs.push(`STATUS.md: ${x.id} ✅ است ولی شواهد ندارد`);
  }
  return errs;
}

// ---------------------------------------------------------------- اجرا
const cmd = process.argv[2];
const src = readFileSync(SRC);
const parts = splitParts(src.toString('utf8'));
const data = collect(parts);
const errs = verify(parts, src, data);

if (cmd === 'split') {
  if (errs.length) { console.error(errs.join('\n')); process.exit(1); }
  const keepFiles = new Set(['MASTER-SPEC.md', 'STATUS.md', '00-index.md', 'parts.txt']);
  for (const f of readdirSync(DIR)) if (f.endsWith('.md') && !keepFiles.has(f) && /^[0-9]{2}-/.test(f)) unlinkSync(join(DIR, f));
  for (const p of parts) writeFileSync(join(DIR, p.file), p.text);
  writeFileSync(PARTS_LIST, parts.map((p) => p.file).join('\n') + '\n');
  writeFileSync(INDEX, buildIndex(parts, src, data));
  console.log(`${parts.length} تکه، SHA-256 ${sha(src)}`);
} else if (cmd === 'status') {
  if (errs.length) { console.error(errs.join('\n')); process.exit(1); }
  writeFileSync(STATUS, buildStatus(data));
  console.log(`STATUS.md: ${data.items.length} شناسه، ${data.scenarios.length} سناریو، ${data.decisions.length} تصمیم`);
} else if (cmd === 'check') {
  const all = [...errs, ...verifyDisk(parts, src), ...verifyStatus(data)];
  if (existsSync(INDEX) && readFileSync(INDEX, 'utf8') !== buildIndex(parts, src, data)) all.push('00-index.md کهنه است — split بزن');
  if (all.length) { console.error(all.join('\n')); process.exit(1); }
  console.log(`سالم: ${parts.length} تکه = اصل (SHA-256 ${sha(src).slice(0, 12)}…)، ${data.items.length} شناسه هر کدام یک فاز، ${data.scenarios.length} سناریو با وابستگیِ هم‌فاز یا پیشین.`);
} else {
  console.error('استفاده: spec.mjs split | status | check');
  process.exit(2);
}
