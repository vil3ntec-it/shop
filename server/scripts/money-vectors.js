#!/usr/bin/env node
'use strict';
/**
 * سازندهٔ بردارهای مرجعِ پول و مقدار — `license/test-vectors/money-v1.json`.
 *
 *   node scripts/money-vectors.js           ⇐ فقط می‌سنجد که فایل با خروجیِ امروز یکی است
 *   node scripts/money-vectors.js --write   ⇐ فایل را از نو می‌نویسد
 *
 * «legacyMinor» خروجیِ **موتورِ امروز** است: خودِ `lineTotal`ِ `index.html`
 * (از متنِ فایل بیرون کشیده و اجرا می‌شود، نه بازنویسی). همان عدد را
 * `SalesEngine.lineTotal`ِ اندروید هم باید بدهد (`MoneyTest.kt` می‌سنجد).
 * «minor» قاعدهٔ بخشِ ۳.۴ است، این‌جا با BigInt و **مستقل** از
 * `license/money.js` حساب می‌شود تا آزمونِ آن دوری نباشد.
 *
 * هر ردیفی که این دو فرق دارند «تغییرِ رفتار» است و فقط در نیمه‌های دقیق رخ
 * می‌دهد (ضربِ Double یک ذره کمتر از ۰٫۵ می‌افتد). بی تصمیمِ صاحبِ محصول هیچ
 * موتوری به قاعدهٔ تازه وصل نمی‌شود.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const FIXTURE = path.join(ROOT, 'license', 'test-vectors', 'money-v1.json');
const MAX_EXACT = 9007199254740991n;

/** همان `lineTotal`ِ امروزِ وب، از خودِ index.html */
function webLineTotal() {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const m = html.match(/function lineTotal\(price, ?qty\)\s*\{([^\n]*)\}/);
  if (!m) throw new Error('lineTotal در index.html پیدا نشد');
  // eslint-disable-next-line no-new-func
  return new Function('price', 'qty', m[1]);
}

// ── پیاده‌سازیِ مرجع با BigInt (مستقل از license/money.js) ──
function exact(v) {
  if (v > MAX_EXACT || v < -MAX_EXACT) throw new RangeError('out of exact range');
  return v;
}
function floorDiv(a, b) { const q = a / b; return (a % b !== 0n && (a < 0n) !== (b < 0n)) ? q - 1n : q; }
function roundHalfUp(n, d) { return floorDiv(exact(n + d / 2n), d); }
function refLineTotal(qtyMilli, priceMinor, step) {
  const raw = exact(BigInt(qtyMilli) * BigInt(priceMinor));
  return Number(exact(roundHalfUp(raw, 1000n * BigInt(step)) * BigInt(step)));
}
function refAllocate(discount, lines, step) {
  const sum = lines.reduce((a, b) => a + BigInt(b), 0n);
  if (discount === 0) return lines.map(() => 0);
  const units = BigInt(discount / step);
  const parts = lines.map((l, i) => { const n = units * BigInt(l); return { i, base: n / sum, rem: n % sum }; });
  let left = units - parts.reduce((a, p) => a + p.base, 0n);
  [...parts].sort((a, b) => (a.rem === b.rem ? a.i - b.i : (b.rem > a.rem ? 1 : -1)))
    .forEach((p) => { if (left > 0n) { p.base += 1n; left -= 1n; } });
  return parts.map((p) => Number(p.base * BigInt(step)));
}
function refCash(total, step) {
  return Number(roundHalfUp(BigInt(total), BigInt(step)) * BigInt(step) - BigInt(total));
}

/** مولدِ شبه‌تصادفیِ ثابت (mulberry32) تا فایل همیشه همان باشد */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// قیمت به پول (۱/۱۰۰ افغانی)، مقدار به هزارم — کسری، وزنی، منفی و بزرگ
const PRICES = [0, 1, 5, 10, 25, 29, 35, 50, 70, 99, 100, 115, 150, 250, 735, 999, 1000, 1150, 1250, 1995,
  2500, 3333, 4999, 9999, 10000, 11500, 12345, 24950, 100000, 123456, 999999, 2500000, 9999999,
  -29, -35, -50, -150, -1995, -11500];
const QTYS = [0, 1, 5, 10, 50, 100, 125, 140, 250, 333, 500, 560, 580, 750, 999, 1000, 1005, 1160, 1250, 1500,
  2000, 2320, 2500, 3000, 7250, 10000, 12345, 50000, 90000, 100000, 999999, 1000000,
  -1, -125, -280, -500, -1000, -1500, -2500, -50000];

const SWEEP = { priceMinor: [-500, 2500], qtyMilli: [-1000, 3000], stepMinor: 100 };

function build() {
  const legacy = webLineTotal();
  const legacyMinor = (qtyMilli, priceMinor) => Math.round(legacy(priceMinor / 100, qtyMilli / 1000) * 100) + 0;

  const rows = [];
  for (const p of PRICES) for (const q of QTYS) rows.push([q, p, 100, legacyMinor(q, p), refLineTotal(q, p, 100)]);
  // پلهٔ دیگر: سنتِ دالر (۱) و دکانی که ردیف را به ۵ افغانی گرد می‌کند (۵۰۰) — موتورِ امروز ندارد
  for (const p of [1, 29, 115, 1995, 11500, -1995]) {
    for (const q of [1, 125, 580, 1005, 1500, -1500]) {
      for (const step of [1, 500]) rows.push([q, p, step, null, refLineTotal(q, p, step)]);
    }
  }
  // مرزِ بازهٔ دقیق: ۹۴۹۰۶۲۶۵² هنوز زیرِ ۲^۵۳ است
  rows.push([94906265, 94906265, 100, null, refLineTotal(94906265, 94906265, 100)]);

  const diffs = [];
  for (let p = SWEEP.priceMinor[0]; p <= SWEEP.priceMinor[1]; p++) {
    for (let q = SWEEP.qtyMilli[0]; q <= SWEEP.qtyMilli[1]; q++) {
      const a = legacyMinor(q, p);
      const b = refLineTotal(q, p, 100);
      if (a !== b) diffs.push([q, p, a, b]);
    }
  }

  const overflow = [
    [94906266, 94906266, 100],           // ضرب از ۲^۵۳−۱ بیرون می‌زند
    [100000000000, 100000000, 100],
    [-9007199254740991, 2, 100],
    [9007199254740991, 1, 100],          // ضرب در بازه، ولی «+ ۵۰٬۰۰۰» بیرون می‌زند
  ];

  const money = [0, 0.01, 0.29, 1.15, 19.95, 115, 172.5, 99999.99, 1.005, 0.1 + 0.2, -19.95, -0.005, 2.675]
    .map((x) => [x, Math.round(x * 100) + 0]);
  const qty = [0, 0.001, 0.58, 1.005, 1.0005, 0.1 + 0.2, 2.5, 12.345, 1000, -1.5, -0.0005]
    .map((x) => [x, Math.round(x * 1000) + 0]);

  const r = rng(20261014);
  const alloc = [
    [0, [17300, 25000]],
    [100, [100, 100, 100]],
    [200, [100, 100, 100]],
    [300, [100, 100, 100]],
    [1000, [17300, 0, 25000, 500]],
    [42300, [17300, 25000]],
    [100, [0, 0, 100]],
    [500, [33300, 33300, 33400]],
    [700, [100, 200, 300, 400, 500, 600]],
  ].map(([d, l]) => ({ stepMinor: 100, discount: d, lines: l }));
  for (let i = 0; i < 40; i++) {
    const step = i % 5 === 4 ? 500 : 100;
    const n = 1 + Math.floor(r() * 8);
    const lines = Array.from({ length: n }, () => (r() < 0.15 ? 0 : step * (1 + Math.floor(r() * 5000))));
    const sum = lines.reduce((a, b) => a + b, 0);
    const discount = step * Math.floor(r() * (sum / step + 1));
    alloc.push({ stepMinor: step, discount, lines });
  }
  alloc.push({ stepMinor: 1, discount: 1001, lines: [333, 333, 334, 1] });
  alloc.forEach((c) => { c.shares = refAllocate(c.discount, c.lines, c.stepMinor); });

  const cash = [];
  for (const step of [1, 100, 500, 1000, 250]) {
    for (const t of [0, 100, 17200, 17250, 17251, 17249, 17300, 17500, 17750, 99900, 12345, -17250, -17251, -17300, -250]) {
      cash.push([t, step, refCash(t, step)]);
    }
  }

  return {
    version: 1,
    about: 'بردارهای مرجعِ پول و مقدار (بخشِ ۳.۴). ساخته‌شده با server/scripts/money-vectors.js — دستی ویرایش نشود.',
    currencies: [
      { code: 'AFN', decimals: 2, stepMinor: 100 },
      { code: 'USD', decimals: 2, stepMinor: 1 },
    ],
    lineTotal: { columns: ['qtyMilli', 'priceMinor', 'stepMinor', 'legacyMinor', 'minor'], rows },
    lineTotalOverflow: { columns: ['qtyMilli', 'priceMinor', 'stepMinor'], rows: overflow },
    sweep: { ...SWEEP, columns: ['qtyMilli', 'priceMinor', 'legacyMinor', 'minor'], diffs },
    fromLegacy: { money: money, qty: qty },
    allocateDiscount: alloc,
    cashRounding: { columns: ['totalMinor', 'stepMinor', 'adjustMinor'], rows: cash },
  };
}

/** یک ردیف در هر خط تا تفاوت‌ها در گیت خوانا بمانند */
function serialize(v) {
  const rowsOf = (a) => '[\n' + a.map((x) => '      ' + JSON.stringify(x)).join(',\n') + '\n    ]';
  const out = [];
  out.push('{');
  out.push('  "version": ' + v.version + ',');
  out.push('  "about": ' + JSON.stringify(v.about) + ',');
  out.push('  "currencies": ' + JSON.stringify(v.currencies) + ',');
  out.push('  "lineTotal": {\n    "columns": ' + JSON.stringify(v.lineTotal.columns) + ',\n    "rows": ' + rowsOf(v.lineTotal.rows) + '\n  },');
  out.push('  "lineTotalOverflow": {\n    "columns": ' + JSON.stringify(v.lineTotalOverflow.columns) + ',\n    "rows": ' + rowsOf(v.lineTotalOverflow.rows) + '\n  },');
  const s = v.sweep;
  out.push('  "sweep": {\n    "priceMinor": ' + JSON.stringify(s.priceMinor) + ',\n    "qtyMilli": ' + JSON.stringify(s.qtyMilli) +
    ',\n    "stepMinor": ' + s.stepMinor + ',\n    "columns": ' + JSON.stringify(s.columns) + ',\n    "diffs": ' + rowsOf(s.diffs) + '\n  },');
  out.push('  "fromLegacy": {\n    "money": ' + rowsOf(v.fromLegacy.money) + ',\n    "qty": ' + rowsOf(v.fromLegacy.qty) + '\n  },');
  out.push('  "allocateDiscount": [\n' + v.allocateDiscount.map((c) => '    ' + JSON.stringify(c)).join(',\n') + '\n  ],');
  out.push('  "cashRounding": {\n    "columns": ' + JSON.stringify(v.cashRounding.columns) + ',\n    "rows": ' + rowsOf(v.cashRounding.rows) + '\n  }');
  out.push('}');
  return out.join('\n') + '\n';
}

module.exports = { build, serialize, FIXTURE, webLineTotal };

if (require.main === module) {
  const text = serialize(build());
  if (process.argv.includes('--write')) {
    fs.mkdirSync(path.dirname(FIXTURE), { recursive: true });
    fs.writeFileSync(FIXTURE, text);
    console.log('نوشته شد:', path.relative(process.cwd(), FIXTURE));
  } else {
    const same = fs.existsSync(FIXTURE) && fs.readFileSync(FIXTURE, 'utf8') === text;
    console.log(same ? 'بردارها با موتورِ امروز یکی‌اند.' : 'بردارها کهنه‌اند — با --write دوباره بسازید.');
    process.exitCode = same ? 0 : 1;
  }
}
