// آزمون‌های ابزارِ docs/spec — node --test docs/spec/tools/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  splitParts, collect, sha, readBlockers, readStatus, verifyStatus, buildStatus, expandRefs, counts, familyOf,
} from './spec.mjs';

const DIR = join(dirname(fileURLToPath(import.meta.url)), '..');

// سندِ کوچکی با همهٔ شکل‌های سرخط و شناسه‌ای که v3 دارد.
const DOC = [
  '# عنوان',
  '',
  '## پیام شروع رسمی',
  '```text',
  '## ۹۹. این سرخط داخلِ fence است و بخش نیست',
  '```',
  '## ۱. هدف',
  'متن با SHA-256 و UTF-8 که شناسه نیستند.',
  '## ۲. وضعیت',
  '| شناسه | شدت | مشکل |',
  '|---|---|---|',
  '| F0-01 | 🔴 | ذخیرهٔ ناامن |',
  '## ۶. فهرست',
  'راهنما',
  '### ۶.۱ صندوق (POS)',
  '- **POS-01** · جست‌وجو · P0 · ندارد · فاز ۲',
  '- **POS-02** · اسکن دوباره · P1 · بررسی',
  '  ادامهٔ بلوک · فاز ۳',
  '### ۶.۲ انبار (INV)',
  '- **INV-01** · موجودی به تفکیک انبار · P0 · ناقص · فاز ۳',
  '## ۷. سناریوها',
  '### ۷.۱ صندوق',
  '**S01 ⌨ · فروش نقدی** (POS-01، F0-01) — خروج فاز ۲',
  'بدنهٔ سناریو',
  '  - **S01b:** همان با کسری',
  '**S02 · اسکن** (POS-01 تا POS-02، D-01) — خروج فاز ۳',
  '## ۱۳. تصمیم‌ها',
  '| شناسه | تصمیم | پیشنهاد این سند |',
  '|---|---|---|',
  '| D-01 | میزبانی | ترکیبی |',
  '| D-02 | کلید | بررسی |',
  '## 15. اصلاحات',
  '- **F0-01 ذخیرهٔ ویندوز:** جایگزینیِ امن (D-02).',
  '| تصمیم | ابهام | شرط لازم پیش از انتشار عمومی |',
  '|---|---|---|',
  '| D-01/D-03 | میزبانی | آزمون بار |',
  '- **S141 — retry مالی:** سه بار.',
  '# ۲۸. قرارداد',
  '- **S141 (فاز ۲):** همان retry.',
  '## ۲۸.۱ زیربخش که جدا نمی‌شود',
  '# ۳۲. کاتالوگ',
  '- **INV-01:** دو مشتری هم‌زمان.',
  '- **AUTH-01:** رمز اشتباه. GATE: AUTH-01 تا AUTH-01',
  '- `DEL-01`: پیک رد می‌کند.',
  '## ۴۵. بخشِ آینده با شمارهٔ بالاتر',
  'پایان',
  '',
].join('\n');

test('split: همهٔ سرخط‌ها کشف می‌شوند و چسباندنِ بایت‌ها همان اصل است', () => {
  const buf = Buffer.from(DOC, 'utf8');
  const { parts, warnings } = splitParts(buf);
  assert.deepEqual(parts.map((p) => p.name), [
    '00-intro', '00-start', '01-goals', '02-current', '06-intro', '06-POS', '06-INV', '07-0', '07-1',
    '13-decisions', '15-corrections', '28-safe-execution', '32-catalog-stock', '45-section',
  ]);
  assert.equal(sha(Buffer.concat(parts.map((p) => p.bytes))), sha(buf));
  assert.equal(parts.map((p) => p.text).join(''), DOC);
  assert.ok(warnings.some((w) => w.includes('45')), 'پرشِ شمارهٔ بخش هشدار می‌دهد');
  const p28 = parts.find((p) => p.section === 28);
  assert.ok(p28.text.includes('## ۲۸.۱'), 'زیربخشِ «## ۲۸.۱» در همان بخشِ ۲۸ می‌ماند');
});

test('split: بخشِ تکراری یا نامرتب و fenceِ باز خطاست', () => {
  assert.throws(() => splitParts('## ۱. الف\n## ۱. ب\n'), /ترتیب یا تکرار/);
  assert.throws(() => splitParts('## ۲. الف\n## 1. ب\n'), /ترتیب یا تکرار/);
  assert.throws(() => splitParts('## ۱. الف\n```\n'), /بسته نشده/);
});

test('شناسه‌ها: تعریف‌ها، فاز، اصلاحیه و تصمیم‌ها از همهٔ شکل‌ها', () => {
  const { parts } = splitParts(DOC);
  const reg = new Map([['INV-01', 'برخورد'], ['S141', 'هم‌معنا']]);
  const d = collect(parts, reg);
  assert.deepEqual(d.errors, []);
  const k = (key) => d.entries.find((e) => e.key === key);
  assert.equal(k('F0-01').phase, 0);
  assert.equal(k('POS-01').phase, 2);
  assert.equal(k('POS-02').phase, 3, 'فازِ بلوکِ چندخطی از انتهای بلوک');
  assert.equal(k('S01').phase, 2);
  assert.deepEqual(k('S01').deps, ['POS-01', 'F0-01']);
  assert.deepEqual(k('S02').deps.sort(), ['D-01', 'POS-01', 'POS-02']);
  assert.ok(k('S01b'), 'زیرسناریوی تورفته شناسهٔ خودش را دارد');
  assert.equal(k('S141').phase, 2, 'هم‌معنا: فاز از تعریفِ دوم');
  assert.equal(k('S141').locs.length, 2);
  assert.ok(k('INV-01@32'), 'برخورد: تعریفِ دوم کلیدِ بخش‌دار می‌گیرد');
  assert.ok(k('AUTH-01') && k('DEL-01'));
  assert.equal(k('F0-01').amends.length, 1, 'اصلاحیهٔ بخش ۱۵ تعریفِ تازه نیست');
  assert.deepEqual(k('F0-01').blockers, ['D-02']);
  assert.deepEqual([...d.decisions.keys()].sort(), ['D-01', 'D-02', 'D-03']);
  assert.equal(d.decisions.get('D-01').recommendation, 'ترکیبی');
  assert.equal(d.decisions.get('D-01').condition, 'آزمون بار');
  assert.equal(d.decisions.get('D-01').rows.length, 2, 'ردیف‌های دو جدول یک تصمیم‌اند');
  assert.ok(d.decisions.get('D-02').blocks.includes('F0-01'));
});

test('تکراری: بی رده خطاست؛ دو بار در یک جدولِ تصمیم هم', () => {
  const { parts } = splitParts(DOC);
  const d = collect(parts);
  const dupErr = d.errors.filter((e) => e.startsWith('شناسهٔ تکراری ثبت‌نشده'));
  assert.equal(dupErr.length, 2);
  assert.ok(dupErr.some((e) => e.includes('INV-01')) && dupErr.some((e) => e.includes('S141')));
  const twice = splitParts('## ۱۳. ت\n| شناسه | تصمیم |\n|---|---|\n| D-01 | الف |\n| D-01 | ب |\n').parts;
  assert.ok(collect(twice).errors.some((e) => e.includes('D-01') && e.includes('یک جدول')));
  const stale = collect(splitParts(DOC).parts, new Map([['INV-01', 'برخورد'], ['S141', 'هم‌معنا'], ['POS-01', 'برخورد']]));
  assert.ok(stale.errors.some((e) => e.includes('POS-01') && e.includes('دیگر چندجا')));
});

test('ارجاعِ شکسته: فقط برای خانواده‌ای که تعریف دارد', () => {
  const bad = DOC.replace('پایان', 'پایان AUTH-02 و POS-99 و W-24817 و EAN-13 و D-77');
  const d = collect(splitParts(bad).parts, new Map([['INV-01', 'برخورد'], ['S141', 'هم‌معنا']]));
  const br = d.errors.filter((e) => e.startsWith('ارجاعِ شکسته')).map((e) => e.match(/[A-Z][A-Z0-9-]*-[0-9]+/)[0]);
  assert.deepEqual(br.sort(), ['AUTH-02', 'D-77', 'POS-99']);
  assert.ok(collect(splitParts('## ۱. الف\n- **S01 — ب:** پ\n').parts).errors.length === 0);
  const unknownDep = DOC.replace('(POS-01، F0-01)', '(POS-01، F0-09)');
  assert.ok(collect(splitParts(unknownDep).parts, new Map([['INV-01', 'برخورد'], ['S141', 'هم‌معنا']])).errors.some((e) => e.includes('F0-09')));
});

test('بازه‌ها و خانواده', () => {
  assert.deepEqual(expandRefs('RPT-02 تا RPT-04').sort(), ['RPT-02', 'RPT-03', 'RPT-04']);
  assert.deepEqual(expandRefs('S51–S53').sort(), ['S51', 'S52', 'S53']);
  assert.equal(familyOf('IMG-BANK-01'), 'IMG-BANK');
  assert.equal(familyOf('E2E-ORD-03'), 'E2E-ORD');
  assert.equal(familyOf('S28b'), 'S');
  assert.equal(familyOf('INV-01@32'), 'INV');
});

test('STATUS: نگاشتِ نسخهٔ ۱ و قاعدهٔ شواهد', () => {
  const v1 = [
    '| F0-01 | عنوان | ۰ | 🔴 | ✅ | #193 (مرج)؛ DiskWriteTest ۴/۴ |',
    '| F0-03 | عنوان | ۰ | 🔴 | 🟡 | PR #198 در حال مرج |',
    '| F0-09 | عنوان | ۰ | 🔴 | ⛔ | — |',
    '| D-05 | تصمیم | ۱۴۰۵/۰۷/۲۲ — بی‌سقف |',
  ].join('\n');
  const { keep, legacyDecisions } = readStatus(v1);
  assert.deepEqual(keep.get('F0-01'), { state: 'IMPLEMENTED-VERIFIED', verify: '#193 (مرج)؛ DiskWriteTest ۴/۴', ext: '—', blocker: null, pr: '#193' });
  assert.equal(keep.get('F0-03').state, 'PARTIAL');
  assert.equal(keep.get('F0-09').state, 'MISSING');
  assert.equal(legacyDecisions.get('D-05'), '۱۴۰۵/۰۷/۲۲ — بی‌سقف');

  const { parts } = splitParts(DOC);
  const d = collect(parts, new Map([['INV-01', 'برخورد'], ['S141', 'هم‌معنا']]));
  const built = buildStatus(d, v1, sha(Buffer.from(DOC)));
  assert.deepEqual(verifyStatus(d, built), []);
  assert.equal(buildStatus(d, built, sha(Buffer.from(DOC))), built, 'status دوباره همان را می‌سازد');
  assert.match(built, /^\| F0-01 \|.*\| IMPLEMENTED-VERIFIED \| #193 \(مرج\)؛ DiskWriteTest ۴\/۴ \| — \| D-02 \| #193 \|$/m, 'مانعِ پیش‌فرض: D-ِ نام‌برده و باز');
  const noEvidence = built.replace(/^(\| F0-01 \|.*\| IMPLEMENTED-VERIFIED \|).*$/m, '$1 — | — | — | — |');
  assert.ok(verifyStatus(d, noEvidence).some((e) => e.includes('F0-01') && e.includes('IMPLEMENTED-VERIFIED')));
  const badState = built.replace(/^(\| AUTH-01 \|(?:[^|]*\|){4}) NOT-VERIFIED \|/m, '$1 DONE |');
  assert.ok(verifyStatus(d, badState).some((e) => e.includes('AUTH-01') && e.includes('نامعتبر')));
  assert.ok(verifyStatus(d, built.replace(/^\| POS-02 \|.*\n/m, '')).some((e) => e.includes('POS-02')), 'ردیفِ جاافتاده');
});

test('سندِ واقعیِ docs/spec: همان SHA، بی خطا با ردهٔ BLOCKERS.md، و شمارهای واقعی', () => {
  const src = readFileSync(join(DIR, 'MASTER-SPEC.md'));
  const man = JSON.parse(readFileSync(join(DIR, 'manifest.json'), 'utf8'));
  assert.equal(man.sha256, sha(src));
  const { parts } = splitParts(src);
  assert.equal(sha(Buffer.concat(parts.map((p) => p.bytes))), sha(src));
  assert.deepEqual(parts.map((p) => p.file), man.parts.map((p) => p.file));
  const mains = [...new Set(parts.map((p) => p.section).filter((s) => s > 0))];
  assert.deepEqual(mains, Array.from({ length: mains.length }, (_, i) => i + 1), 'همهٔ بخش‌های اصلی به ترتیب');
  const reg = readBlockers(readFileSync(join(DIR, 'BLOCKERS.md'), 'utf8')).reg;
  const d = collect(parts, reg);
  assert.deepEqual(d.errors, []);
  const c = Object.fromEntries(counts(d).map((x) => [x.family, x.ids]));
  assert.equal(c.F0, 28);
  assert.ok(c.D >= 26 && c.S >= 146);
  for (const id of ['S28b', 'S50', 'S141', 'S144', 'IMG-BANK-18', 'ABUSE-01', 'E2E-ADM-15', 'D-25']) {
    assert.ok(d.byId.has(id) || d.decisions.has(id), id);
  }
});
