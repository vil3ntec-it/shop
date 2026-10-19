#!/usr/bin/env node
'use strict';
/**
 * سازندهٔ بردارهای مرجعِ کلاینتِ Sync v3 — از **خودِ پیاده‌سازیِ سرور**:
 *
 *   core/test-vectors/hlc-v1.json             ⇐ server/src/lib/hlc.js
 *   core/test-vectors/sync-v3-convert-v1.json ⇐ server/src/lib/sync-v3-convert.js (fromV2)
 *                                                + validate ِ server/src/lib/sync-v3.js
 *
 *   node scripts/sync-v3-vectors.js           ⇐ فقط می‌سنجد که فایل‌ها با خروجیِ امروز یکی‌اند
 *   node scripts/sync-v3-vectors.js --write   ⇐ فایل‌ها را از نو می‌نویسد
 *
 * کاتلین (`core/.../Hlc.kt`، `V3Convert.kt`) و وب (`license/sync-v3-engine.js`)
 * همین فایل‌ها را می‌دوانند؛ پس «HLCِ برنامه همان HLCِ سرور است» و «تبدیلِ دفترِ
 * امروز به op ِ v3 همان قاعدهٔ یگانهٔ fromLegacy است» سنجه دارد، نه ادعا.
 * ⛔ دستی ویرایش نشود؛ هر تغییرِ سرور ⇒ `--write` و همان تغییر در دو کلاینت.
 */
const fs = require('fs');
const path = require('path');
const hlc = require('../src/lib/hlc');
const convert = require('../src/lib/sync-v3-convert');
const { validate } = require('../src/lib/sync-v3');
const schema = require('../src/lib/sync-v3-schema');

const ROOT = path.join(__dirname, '..', '..');
const HLC_FILE = path.join(ROOT, 'core', 'test-vectors', 'hlc-v1.json');
const CONVERT_FILE = path.join(ROOT, 'core', 'test-vectors', 'sync-v3-convert-v1.json');

/* ------------------------------------------------------------ HLC */

function hlcVectors() {
  const parse = [
    '1760000000000:3:dev-a', '000001760000000000:000003:dev-a', '0:0:x', '999999999999999:999999:n',
    '1760000000000:3:a.b_c-D9', '1760000000000:3:' + 'n'.repeat(64), '1760000000000:3:' + 'n'.repeat(65),
    '1234567890123456:0:x', '1:1234567:x', '1:2:', ':2:x', '1::x', '-1:2:x', '1.5:2:x', '1:2:dev a',
    '1:2:dev/a', '1:2:a\n', ' 1:2:a', '1:2:a:b', '', 'abc', '١:٢:a',
  ].map((s) => [s, hlc.parse(s)]);

  const format = [
    [0, 0, 'x'], [1760000000000, 3, 'dev-a'], [999999999999999, 999999, 'n'], [1, 1234567, 'x'],
    [1234567890123456, 0, 'x'], [-5, -2, 'neg'],
  ].map(([wall, counter, node]) => [wall, counter, node, hlc.format({ wall, counter, node })]);

  const canonical = parse.map(([s]) => [s, hlc.canonical(s)]);

  const pairs = [
    ['1:0:a', '1:0:a'], ['1:0:a', '1:0:b'], ['1:0:b', '1:0:a'], ['1:1:a', '1:0:z'], ['2:0:a', '1:999999:z'],
    ['000000000000001:000000:a', '1:0:a'], ['1760000000000:3:dev-a', '1760000000000:3:dev-B'],
    ['1760000000000:3:Z', '1760000000000:3:a'], ['1760000000000:3:v1.000000000000012', '1760000000000:3:server'],
    ['999999999999999:999999:a', '1:0:a'],
  ];
  const compare = pairs.map(([a, b]) => [a, b, hlc.compare(a, b)]);

  const fromTs = [
    [1760000000000, 0, 'dev'], [1760000000000, 7, 'dev'], [0, 0, 'dev'], [-1, 2, 'dev'],
    [1e16, 0, 'dev'], [999999999999999, 999999, 'dev'], [5, 1234567, 'dev'], [5, -3, 'dev'],
  ].map(([ts, counter, node]) => [ts, counter, node, hlc.fromTs(ts, counter, node)]);

  /*
   *  ساعت: گام‌ها داده‌اند، خروجی را خودِ `createClock`ِ سرور می‌دهد.
   *  هر گام: [now, کار, ورودی] ⇒ خروجی
   */
  const scripts = [
    { name: 'tick-monotonic', node: 'dev-a', skewMs: hlc.SKEW_MS, steps: [
      [1000, 'tick'], [1000, 'tick'], [999, 'tick'], [1001, 'tick'], [5000, 'tick'], [5000, 'peek'],
    ] },
    { name: 'recv-ahead-equal-behind', node: 'dev-b', skewMs: hlc.SKEW_MS, steps: [
      [1000, 'tick'], [1000, 'recv', '1200:4:server'], [1000, 'recv', '1200:9:server'], [1300, 'recv', '1200:1:server'],
      [1300, 'recv', '1300:0:dev-a'], [1300, 'recv', '1300:5:dev-a'], [1300, 'tick'], [1400, 'recv', '1400:2:x'],
    ] },
    { name: 'recv-skewed', node: 'dev-c', skewMs: hlc.SKEW_MS, steps: [
      [10_000, 'tick'], [10_000, 'isSkewed', String(10_000 + hlc.SKEW_MS + 1) + ':0:fast'],
      [10_000, 'recv', String(10_000 + hlc.SKEW_MS + 1) + ':0:fast'], [10_000, 'peek'],
      [10_000, 'isSkewed', String(10_000 + hlc.SKEW_MS) + ':0:edge'],
      [10_000, 'recv', String(10_000 + hlc.SKEW_MS) + ':0:edge'], [10_000, 'tick'],
    ] },
    { name: 'small-skew-limit', node: 'dev-d', skewMs: 1000, steps: [
      [100, 'recv', '1101:0:a'], [100, 'recv', '1100:0:a'], [100, 'tick'],
    ] },
    { name: 'counter-overflow', node: 'dev-e', skewMs: hlc.SKEW_MS, steps: [
      [50, 'observe', '50:999998:x'], [50, 'tick'], [50, 'tick'], [50, 'tick'], [50, 'peek'],
    ] },
    { name: 'observe', node: 'dev-f', skewMs: hlc.SKEW_MS, steps: [
      [10, 'observe', '9000:4:x'], [10, 'peek'], [10, 'observe', '8000:9:x'], [10, 'peek'],
      [10, 'observe', '9000:3:x'], [10, 'observe', '9000:6:y'], [10, 'peek'], [10, 'observe', 'nonsense'],
      [10, 'tick'], [10, 'observe', '99999999999:0:far-future'], [10, 'tick'],
    ] },
    { name: 'recv-invalid', node: 'dev-g', skewMs: hlc.SKEW_MS, steps: [
      [7, 'recv', 'nonsense'], [7, 'recv', ''], [7, 'isSkewed', 'nonsense'], [8, 'recv', '8:0:a'],
    ] },
  ];
  for (const sc of scripts) {
    let t = 0;
    const clock = hlc.createClock({ node: sc.node, skewMs: sc.skewMs, now: () => t });
    sc.steps = sc.steps.map(([now, op, arg]) => {
      t = now;
      let out;
      if (op === 'tick') out = clock.tick();
      else if (op === 'recv') out = clock.recv(arg);
      else if (op === 'observe') { clock.observe(arg); out = null; }
      else if (op === 'isSkewed') out = clock.isSkewed(arg);
      else if (op === 'peek') out = clock.peek();
      else throw new Error(op);
      return arg === undefined ? [now, op, null, out] : [now, op, arg, out];
    });
  }

  return {
    version: 1,
    about: 'بردارهای مرجعِ HLC ِ Sync v3 — ساخته‌شده از server/src/lib/hlc.js با server/scripts/sync-v3-vectors.js. دستی ویرایش نشود.',
    skewMs: hlc.SKEW_MS,
    parse: { columns: ['in', 'out'], rows: parse },
    format: { columns: ['wall', 'counter', 'node', 'out'], rows: format },
    canonical: { columns: ['in', 'out'], rows: canonical },
    compare: { columns: ['a', 'b', 'out'], rows: compare },
    fromTs: { columns: ['ts', 'counter', 'node', 'out'], rows: fromTs },
    clock: { columns: ['now', 'op', 'arg', 'out'], scripts },
  };
}

/* ------------------------------------------------------------ مبدل */

/** opهای v1ِ نمونه — همان شکلی که تفاضلِ دفتر (`LedgerDiff`/`sync-core.js`) می‌سازد */
function legacyOps() {
  let n = 0;
  const id = () => `01JV3VEC${String(++n).padStart(18, '0')}`;
  const ins = (table, fields) => ({ op_id: id(), ts: 1760000000000 + n, table, row_id: `${table}-r${n}`, type: 'insert', fields });
  const upd = (table, fields) => ({ op_id: id(), ts: 1760000000000 + n, table, row_id: `${table}-r${n}`, type: 'update', fields });
  const del = (table) => ({ op_id: id(), ts: 1760000000000 + n, table, row_id: `${table}-r${n}`, type: 'delete' });
  const out = [];

  //  پول و مقدار: همان مقدارهای money-v1.json (fromLegacy)، روی چند جدول
  const money = [0, 0.01, 0.29, 1.15, 19.95, 115, 172.5, 99999.99, 1.005, 0.1 + 0.2, -19.95, -0.005, 2.675];
  const qty = [0, 0.001, 0.58, 1.005, 1.0005, 0.1 + 0.2, 2.5, 12.345, 1000, -1.5, -0.0005];
  money.forEach((m, i) => out.push(ins('products', {
    name: `کالا ${i}`, salePrice: m, purchasePrice: money[(i + 3) % money.length], wholesalePrice: null,
    minStock: qty[i % qty.length], stock: 7, barcodes: ['111' + i], unit: 'دانه',
  })));
  qty.forEach((q, i) => out.push(ins('saleItems', {
    saleId: 's1', productId: 'p' + i, quantity: q, returnedQty: 0, unitPrice: money[i], purchasePrice: 1, totalPrice: money[i],
  })));
  out.push(ins('products', { name: 'رشته', salePrice: '12.5', purchasePrice: 'abc', minStock: '' }));
  out.push(ins('products', { name: 'بزرگ', salePrice: 1e300, purchasePrice: 90071992547409.91 }));

  //  هر جدولِ v1 یک درج با همهٔ فیلدهای پولی/مقداری + فیلدِ ساده + مانده/نشانِ محلی
  for (const [table, t] of Object.entries(schema.TABLES)) {
    if (t.v3only) continue;
    const f = { note: 'یادداشت' };
    (t.money || []).forEach((k, i) => { f[k] = 10.25 * (i + 1); });
    (t.qty || []).forEach((k, i) => { f[k] = 1.5 * (i + 1); });
    (t.derived || []).forEach((k) => { f[k] = 99; });
    (t.localOnly || []).forEach((k) => { f[k] = 'pending'; });
    if (t.policy === 'moves') f.productId = 'p1';
    if (['productCategories', 'productUnits', 'expenseCategories'].includes(table)) {
      out.push(ins(table, { name: 'لبنیات' }));
      out.push(del(table));
      continue;
    }
    out.push(ins(table, f));
    out.push(del(table));
  }

  //  ویرایش‌ها
  out.push(upd('products', { salePrice: 125.5, name: 'تازه' }));
  out.push(upd('products', { salePrice: null }));
  out.push(upd('products', { stock: 3 }));                              //  فقط مانده ⇒ هیچ op
  out.push(upd('products', { minStock: { $inc: 2.5 } }));               //  دلتای غیرشمارنده
  out.push(upd('debtors', { name: 'احمد', phone: '0700000000', balance: 500, totalDebt: 900 }));
  out.push(upd('sales', { paidAmount: { $inc: 50.25 }, remaining: { $inc: -50.25 } }));
  out.push(upd('sales', { status: 'cancelled', cancelReason: '  مشتری پس آورد  ' }));
  out.push(upd('sales', { status: 'cancelled' }));
  out.push(upd('sales', { status: 'cancelled', paidAmount: { $inc: -10 } }));
  out.push(upd('sales', { customerName: 'نامِ تازه' }));               //  ویرایشِ سندِ مالی
  out.push(upd('sales', { syncStatus: 'synced' }));                     //  فقط نشانِ محلی
  out.push(upd('saleItems', { returnedQty: { $inc: 1.5 } }));
  out.push(upd('purchases', { paidAmount: { $inc: 100 }, debt: { $inc: -100 }, note: 'x' }));
  out.push(upd('stockMovements', { qty: 2 }));
  out.push(upd('priceHistory', { newPrice: 3 }));
  out.push(upd('expenses', { amount: 12.345, title: 'کرایه' }));
  out.push(upd('suppliers', { name: 'تأمین‌کننده', balance: 10 }));
  return out;
}

function stripLegacy(o) {
  const { legacy, ...rest } = o;   // eslint-disable-line no-unused-vars
  return rest;
}

function convertVectors() {
  const GIVEN = '001760000000000:000000:dev-x';
  const cases = legacyOps().map((op) => {
    const r = convert.fromV2(op, { schemaVersion: 3, deviceId: 'dev-x', hlc: GIVEN });
    if (!r.ok) throw new Error(`نمونهٔ نادرست: ${op.op_id} ${r.reason}`);
    const ops = r.ops.map(stripLegacy);
    let native = true;
    let reason = '';
    for (const o of ops) {
      const v = validate(o, 'v3');
      if (!v.ok) { native = false; reason = v.reason; break; }
    }
    return { in: op, ops, dropped: r.dropped, native, reason };
  });
  const tables = {};
  for (const [name, t] of Object.entries(schema.TABLES)) {
    tables[name] = {
      policy: t.policy, money: t.money || [], qty: t.qty || [], counters: t.counters || [],
      derived: t.derived || [], localOnly: t.localOnly || [], cancel: t.cancel || '', v3only: !!t.v3only,
    };
  }
  return {
    version: 1,
    about: 'بردارهای مرجعِ تبدیلِ دفترِ امروز (op v1/v2) به op ِ v3 — ساخته‌شده از fromV2 و validate ِ سرور با server/scripts/sync-v3-vectors.js. دستی ویرایش نشود.',
    hlc: GIVEN,
    tables,
    kinds: schema.KINDS,
    cases,
  };
}

/* ------------------------------------------------------------ نوشتن */

function serialize(v, listKeys) {
  //  یک ردیف/مورد در هر خط تا تفاوت‌ها در گیت خوانا بمانند
  const lines = ['{'];
  const keys = Object.keys(v);
  keys.forEach((k, i) => {
    const comma = i < keys.length - 1 ? ',' : '';
    const val = v[k];
    if (listKeys.includes(k) && Array.isArray(val)) {
      lines.push(`  ${JSON.stringify(k)}: [\n${val.map((x) => '    ' + JSON.stringify(x)).join(',\n')}\n  ]${comma}`);
    } else if (listKeys.includes(k) && val && typeof val === 'object') {
      const inner = Object.entries(val).map(([kk, vv]) => (Array.isArray(vv) && kk !== 'columns'
        ? `    ${JSON.stringify(kk)}: [\n${vv.map((x) => '      ' + JSON.stringify(x)).join(',\n')}\n    ]`
        : `    ${JSON.stringify(kk)}: ${JSON.stringify(vv)}`));
      lines.push(`  ${JSON.stringify(k)}: {\n${inner.join(',\n')}\n  }${comma}`);
    } else {
      lines.push(`  ${JSON.stringify(k)}: ${JSON.stringify(val)}${comma}`);
    }
  });
  lines.push('}');
  return lines.join('\n') + '\n';
}

function build() {
  return [
    [HLC_FILE, serialize(hlcVectors(), ['parse', 'format', 'canonical', 'compare', 'fromTs', 'clock'])],
    [CONVERT_FILE, serialize(convertVectors(), ['tables', 'cases'])],
  ];
}

if (require.main === module) {
  const write = process.argv.includes('--write');
  let bad = 0;
  for (const [file, text] of build()) {
    const rel = path.relative(ROOT, file);
    if (write) {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, text);
      console.log(`نوشته شد: ${rel}`);
    } else if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8') !== text) {
      console.error(`⛔ ${rel} با سرورِ امروز یکی نیست — node scripts/sync-v3-vectors.js --write`);
      bad++;
    } else {
      console.log(`یکی است: ${rel}`);
    }
  }
  process.exit(bad ? 1 : 0);
}

module.exports = { build, hlcVectors, convertVectors, HLC_FILE, CONVERT_FILE };
