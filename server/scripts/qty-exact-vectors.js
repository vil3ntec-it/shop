#!/usr/bin/env node
'use strict';
/**
 * سازندهٔ بردارهای مرجعِ PRD-08 (کالای وزنی، مقدار با ۳ رقمِ اعشار) — `core/test-vectors/qty-exact-v1.json`.
 *
 *   node scripts/qty-exact-vectors.js           ⇐ فقط می‌سنجد که فایل با خروجیِ امروز یکی است
 *   node scripts/qty-exact-vectors.js --write   ⇐ فایل را از نو می‌نویسد
 *
 * خواننده‌ها: `core/…/money/QtyExactVectorsTest.kt` (هسته)، `QtyExactWiringTest.kt` (موتورِ صندوقِ گوشی و کامپیوتر)
 * و `server/test/qty-exact.test.js` (هم‌زادِ JS، `license/money.js` ⇐ `qtyExact`).
 *
 * «legacyMinor» خروجیِ **موتورِ امروز** است: خودِ `lineTotal` ِ `index.html` ِ یخ‌زده (همان استخراجِ
 * `money-vectors.webLineTotal`) = `SalesEngine.lineTotal` ِ اندروید. «exactMinor» قاعدهٔ بخشِ ۳.۴ است، این‌جا با
 * BigInt و **مستقل** از `license/money.js`. «onMinor» دروازهٔ PRD-08 با پرچمِ روشن: مقدارِ اعشاری ⇒ exact، مقدارِ
 * درست ⇒ legacy (کالای بی اعشار بایت‌به‌بایت همان). پرچمِ خاموش ⇒ همیشه legacy.
 */
const fs = require('fs');
const path = require('path');
const { webLineTotal } = require('./money-vectors');

const ROOT = path.join(__dirname, '..', '..');
const FIXTURE = path.join(ROOT, 'core', 'test-vectors', 'qty-exact-v1.json');
const MAX_EXACT = 9007199254740991n;

const legacyOf = webLineTotal();
const legacyMinor = (price, qty) => Math.round(legacyOf(price, qty) * 100) + 0;

/** هزارم/پول از Double همان `Math.round` ِ JDK و JS؛ بیرون از بازهٔ دقیق ⇒ null */
function milli(q) {
  if (!Number.isFinite(q)) return null;
  const m = Math.round(q * 1000) + 0;
  return Math.abs(m) > Number(MAX_EXACT) ? null : m;
}
function floorDiv(a, b) { const q = a / b; return (a % b !== 0n && (a < 0n) !== (b < 0n)) ? q - 1n : q; }
/** floorDiv(milli × minor + 50_000, 100_000) × 100 با BigInt؛ بیرون از بازهٔ دقیق ⇒ null */
function exactMinor(qtyMilli, priceMinor) {
  const raw = BigInt(qtyMilli) * BigInt(priceMinor);
  if (raw > MAX_EXACT || raw < -MAX_EXACT || raw + 50000n > MAX_EXACT) return null;
  const out = floorDiv(raw + 50000n, 100000n) * 100n;
  return out > MAX_EXACT || out < -MAX_EXACT ? null : Number(out);
}
/** همان ترتیبِ `QtyExact.lineMinorOrNull` ِ هسته: مقدارِ درست یا نامعتبر، قیمتِ نامعتبر یا بیرون از بازه ⇒ legacy */
function onMinor(price, qty) {
  const q = milli(qty);
  if (q === null || q % 1000 === 0 || !Number.isFinite(price)) return legacyMinor(price, qty);
  const p = Math.round(price * 100) + 0;
  const e = Math.abs(p) > Number(MAX_EXACT) ? null : exactMinor(q, p);
  return e === null ? legacyMinor(price, qty) : e;
}

/** [نام، قیمت (افغانی)، مقدار] — S04، نیمه‌های دقیق، مقدارِ درست، رانشِ Double، بزرگ و بیرون از بازه */
const LINES = [
  ['S04: برچسبِ ترازو ۱٫۲۵۰ کیلو × ۳۰۰', 300, 1.25],
  ['S04: وزنِ دستی ۰٫۷۵ × ۱۲۰', 120, 0.75],
  ['نیمهٔ دقیق: ۱٫۰۱۵ کیلو × ۱۰۰ = ۱۰۱٫۵ ⇒ ۱۰۲ (امروز ۱۰۱)', 100, 1.015],
  ['نیمهٔ دقیق: ۰٫۵۸ × ۲۵ = ۱۴٫۵ ⇒ ۱۵ (امروز ۱۴)', 25, 0.58],
  ['نیمهٔ دقیق: ۲٫۳۲ × ۶٫۲۵', 6.25, 2.32],
  ['نیمه‌ای که Double درست دارد: ۱٫۵ × ۱۱۵ = ۱۷۲٫۵ ⇒ ۱۷۳', 115, 1.5],
  ['۰٫۲۵ × ۱۰۰۰', 1000, 0.25],
  ['کوچک‌ترین مقدار: ۰٫۰۰۱ × ۴۹۹ ⇒ ۰', 499, 0.001],
  ['کوچک‌ترین مقدار: ۰٫۰۰۱ × ۵۰۰ ⇒ ۱', 500, 0.001],
  ['قیمتِ پول‌دار: ۱٫۵ × ۳۳٫۳۳', 33.33, 1.5],
  ['۰٫۳۳۳ × ۳', 3, 0.333],
  ['۲٫۶۷۵ × ۱۰', 10, 2.675],
  ['۰٫۰۰۵ × ۱۰۰ = ۰٫۵ ⇒ ۱', 100, 0.005],
  ['رانشِ Double: ۰٫۱ + ۰٫۲ کیلو × ۱۱۵', 115, 0.1 + 0.2],
  ['مقدارِ درست همان امروز: ۵۰ × ۰٫۲۹ (امروز ۱۴، قاعدهٔ دقیق ۱۵)', 0.29, 50],
  ['مقدارِ درست: ۲ × ۱۱۵', 115, 2],
  ['مقدارِ درستِ کیلو: ۳ × ۹۹٫۹۹', 99.99, 3],
  ['بزرگ: ۹۹۹۹٫۹۹۹ × ۹۹۹۹۹٫۹۹', 99999.99, 9999.999],
  ['بیرون از بازهٔ دقیق ⇒ همان امروز', 949062.66, 94906.266],
  ['منفی: −۱٫۲۵ × ۱۱۵', 115, -1.25],
  ['منفیِ نیمه: −۰٫۵ × ۱۱۵', 115, -0.5],
];

/** [نام، a، b] ⇒ جمع به هزارم */
const PLUS = [
  ['۰٫۱ + ۰٫۲', 0.1, 0.2],
  ['۰٫۲ + ۰٫۱ (سه بار «+۰٫۱» در سبد)', 0.1 + 0.1, 0.1],
  ['۱٫۲۵ + ۰٫۷۵', 1.25, 0.75],
  ['درست: ۲ + ۱', 2, 1],
  ['رانش + ۰٫۱', 0.1 + 0.2, 0.1],
  ['۰٫۷ + ۰٫۱', 0.7, 0.1],
  ['۱٫۰۰۵ + ۰٫۰۰۰۵', 1.005, 0.0005],
];

/** [نام، موجودی، در سبد] ⇒ جای خالی به هزارم */
const ROOM = [
  ['موجودیِ رانش‌دار − ۰٫۱', 0.1 + 0.2, 0.1],
  ['بیش از موجودی ⇒ ۰', 1, 1.5],
  ['درست: ۵ − ۲', 5, 2],
  ['تقریباً برابر ⇒ ۰', 0.3, 0.1 + 0.2],
  ['۲٫۵ − ۰٫۷۵', 2.5, 0.75],
];

/** [متن، بیش از سه رقمِ اعشار؟] */
const PRECISE = [
  ['1.25', false], ['1.250', false], ['1.2500', false], ['1.2505', true], ['0.0001', true], ['12', false],
  ['۱٫۲۳۴۵', true], ['۱٫۲۵۰', false], ['1٬0001', true], ['', false], ['.', false], ['1.000000', false], ['٠٫٠٠١٥', true],
];

/** جاروب: مقدارِ اعشاری × قیمتِ افغانیِ درست؛ و مقدارِ درست × قیمتِ پول‌دار (دروازه باید همان امروز بماند) */
const SWEEP = { qtyMilli: [1, 3000], priceMinor: [100, 30000], priceStepMinor: 100 };
const WHOLE = { qtyMilli: [1000, 30000], qtyStepMilli: 1000, priceMinor: [1, 3000] };

function build() {
  const line = LINES.map(([name, price, qty]) => {
    const q = milli(qty);
    const p = Math.round(price * 100) + 0;
    return { name, price, qty, qtyMilli: q, priceMinor: p, legacyMinor: legacyMinor(price, qty), exactMinor: exactMinor(q, p), onMinor: onMinor(price, qty) };
  });
  const plus = PLUS.map(([name, a, b]) => ({ name, a, b, legacy: a + b, milli: milli(a) + milli(b) }));
  const room = ROOM.map(([name, available, inCart]) => ({ name, available, inCart, milli: Math.max(0, milli(available) - milli(inCart)) }));
  const precise = PRECISE.map(([text, over]) => ({ name: text || '(خالی)', text, over }));

  let pairs = 0; let diffs = 0; let sumOn = 0; let sumLegacy = 0; const first = [];
  for (let q = SWEEP.qtyMilli[0]; q <= SWEEP.qtyMilli[1]; q++) {
    if (q % 1000 === 0) continue;
    for (let p = SWEEP.priceMinor[0]; p <= SWEEP.priceMinor[1]; p += SWEEP.priceStepMinor) {
      const price = p / 100; const qty = q / 1000;
      const lg = legacyMinor(price, qty); const on = onMinor(price, qty);
      pairs++; sumOn += on; sumLegacy += lg;
      if (on !== lg) { diffs++; if (first.length < 12) first.push([q, p, lg, on]); }
    }
  }
  let wPairs = 0; let wOnDiffs = 0; let wExactDiffs = 0;
  for (let q = WHOLE.qtyMilli[0]; q <= WHOLE.qtyMilli[1]; q += WHOLE.qtyStepMilli) {
    for (let p = WHOLE.priceMinor[0]; p <= WHOLE.priceMinor[1]; p++) {
      const price = p / 100; const qty = q / 1000; const lg = legacyMinor(price, qty);
      wPairs++;
      if (onMinor(price, qty) !== lg) wOnDiffs++;
      if (exactMinor(q, p) !== lg) wExactDiffs++;
    }
  }
  return {
    version: 1,
    about: 'PRD-08: مقدارِ اعشاری × قیمت. ساخته‌شده با server/scripts/qty-exact-vectors.js — دستی ویرایش نشود.',
    rule: 'پرچمِ shop.qty_exact روشن و هزارمِ مقدار نامضربِ ۱۰۰۰ ⇒ floorDiv(qtyMilli × priceMinor + 50000, 100000) × 100؛ '
      + 'وگرنه Math.round(price × qty) × 100 (موتورِ امروز). مقدار = Math.round(q × 1000)، قیمت = Math.round(price × 100).',
    line, plus, room, precise,
    sweep: { ...SWEEP, pairs, diffs, sumOn, sumLegacy, first },
    whole: { ...WHOLE, pairs: wPairs, onDiffs: wOnDiffs, exactDiffs: wExactDiffs },
  };
}

function serialize(v) {
  const rows = (a) => '[\n' + a.map((x) => '    ' + JSON.stringify(x)).join(',\n') + '\n  ]';
  const s = v.sweep;
  const w = v.whole;
  return [
    '{',
    '  "version": ' + v.version + ',',
    '  "about": ' + JSON.stringify(v.about) + ',',
    '  "rule": ' + JSON.stringify(v.rule) + ',',
    '  "line": ' + rows(v.line) + ',',
    '  "plus": ' + rows(v.plus) + ',',
    '  "room": ' + rows(v.room) + ',',
    '  "precise": ' + rows(v.precise) + ',',
    '  "sweep": {\n    "qtyMilli": ' + JSON.stringify(s.qtyMilli) + ', "priceMinor": ' + JSON.stringify(s.priceMinor)
      + ', "priceStepMinor": ' + s.priceStepMinor + ',\n    "pairs": ' + s.pairs + ', "diffs": ' + s.diffs + ', "sumOn": ' + s.sumOn
      + ', "sumLegacy": ' + s.sumLegacy + ',\n    "first": ' + JSON.stringify(s.first) + '\n  },',
    '  "whole": {\n    "qtyMilli": ' + JSON.stringify(w.qtyMilli) + ', "qtyStepMilli": ' + w.qtyStepMilli + ', "priceMinor": ' + JSON.stringify(w.priceMinor)
      + ',\n    "pairs": ' + w.pairs + ', "onDiffs": ' + w.onDiffs + ', "exactDiffs": ' + w.exactDiffs + '\n  }',
    '}',
  ].join('\n') + '\n';
}

module.exports = { build, serialize, FIXTURE };

if (require.main === module) {
  const text = serialize(build());
  if (process.argv.includes('--write')) {
    fs.writeFileSync(FIXTURE, text);
    console.log('نوشته شد:', path.relative(process.cwd(), FIXTURE));
  } else {
    const same = fs.existsSync(FIXTURE) && fs.readFileSync(FIXTURE, 'utf8') === text;
    console.log(same ? 'بردارها با موتورِ امروز یکی‌اند.' : 'بردارها کهنه‌اند — با --write دوباره بسازید.');
    process.exitCode = same ? 0 : 1;
  }
}
