'use strict';
/**
 * کلاینتِ Sync v3ِ وب (`license/sync-v3-engine.js`) — با **سرورِ واقعی** (PGlite یا pg)
 * و `SYNC_V3_ENABLED=1`. بی ساختگی: همان HTTP، همان جدول‌ها.
 *
 *   • بردارهای مشترک: HLC و مبدل همان سرورند (فایل‌ها را سرور از کدِ خودش می‌سازد)
 *   • نخستین بارگذاری: دفترِ امروز ⇒ opِ v3 با همان fromLegacy؛ دستگاهِ دوم از Snapshot
 *   • Push در دسته‌های کوچک؛ پاسخِ گم‌شده ⇒ تکرار «تکراری» است، نه دلتای دوم
 *   • Pull با cursor؛ بسته شدن وسطِ نشاندن ⇒ بازیابی از ژورنال، بی جمعِ دوباره
 *   • تعارض: «آخرین نوشتن» با HLC به صندوق، برگرداندن با اجازه
 *   • ۴۲۶: نگه داشتنِ صف و ایستادن؛ قطعِ شبکه: عقب‌نشینیِ نمایی با پراکندگی
 *   • ردشده و ویرایشِ سندِ مالی: گم نمی‌شوند (فهرستِ ردشده / صندوقِ تعارض)
 *   • تنظیمِ هر دستگاه: پیش‌فرض v1
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.RATE_SYNC_MAX = process.env.RATE_SYNC_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const h = require('./helpers');
const { one, many, query, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const vectors = require('../scripts/sync-v3-vectors');

const ROOT = path.join(__dirname, '..', '..');
const V3 = require(path.join(ROOT, 'license', 'sync-v3-engine.js'));
const core = require(path.join(ROOT, 'license', 'sync-core.js'));
const HLC = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'hlc-v1.json'), 'utf8'));
const CONV = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'sync-v3-convert-v1.json'), 'utf8'));

let owner;
let staff;
let shopId;

/* ---------------------------------------------------- ابزارِ آزمون */

/** دفترِ یک دستگاه — همان شکلی که index.html نگه می‌دارد */
function ledgerOf(data = {}) {
  let state = core.cloneLedger(data);
  return { read: () => state, write: (d) => { state = core.cloneLedger(d); }, get: () => state };
}

/** انتقالِ واقعی؛ می‌شمارد و می‌تواند «پاسخ را گم کند» (سرور انجام داده، دستگاه نشنیده) */
function transport(token, opts = {}) {
  const real = V3.FetchTransport(h.base(), token);
  const t = {
    calls: [],
    loseNext: 0,
    offline: false,
    async request(method, p, body) {
      t.calls.push(`${method} ${p.split('?')[0]}`);
      if (t.offline) throw new Error('به سرور نرسیدیم');
      const r = await real.request(method, p, body);
      if (t.loseNext > 0 && p.endsWith('/push')) { t.loseNext--; throw new Error('پاسخ گم شد'); }
      return r;
    },
  };
  Object.assign(t, opts);
  return t;
}

function device(name, token, data, extra = {}) {
  const ledger = ledgerOf(data);
  const tr = transport(token);
  const store = new V3.MemoryStore();
  const engine = new V3.Engine({ transport: tr, store, ledger, deviceId: name, random: () => 0.5, ...extra });
  engine.keepLocalOnly = true;
  return { name, ledger, tr, store, engine };
}

const rowOf = (table, id) => one(
  `SELECT * FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const find = (ledger, table, id) => (ledger.get()[table] || []).find((r) => r.id === id);

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دکانِ کلاینتِ v3', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ کلاینتِ v3' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
  const s = await h.newUser('شاگردِ v3', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), shopId, s.user.id, now()]
  );
  staff = await h.signIn(s, 'shop');
});
test.after(async () => { await h.stop(); });

/* ---------------------------------------------------- بردارهای مشترک */

test('بردارها همان خروجیِ امروزِ سرورند (scripts/sync-v3-vectors.js)', () => {
  for (const [file, text] of vectors.build()) {
    assert.equal(fs.readFileSync(file, 'utf8'), text, `${path.relative(ROOT, file)} — node scripts/sync-v3-vectors.js --write`);
  }
});

test('HLCِ وب = HLCِ سرور (core/test-vectors/hlc-v1.json)', () => {
  const x = V3.hlc;
  for (const [i, o] of HLC.parse.rows) assert.deepEqual(x.parse(i), o, `parse ${JSON.stringify(i)}`);
  for (const [w, c, n, o] of HLC.format.rows) assert.equal(x.format({ wall: w, counter: c, node: n }), o);
  for (const [i, o] of HLC.canonical.rows) assert.equal(x.canonical(i), o);
  for (const [a, b, o] of HLC.compare.rows) assert.equal(x.compare(a, b), o, `${a} ⟷ ${b}`);
  for (const [t, c, n, o] of HLC.fromTs.rows) assert.equal(x.fromTs(t, c, n), o);
  assert.equal(x.SKEW_MS, HLC.skewMs);
  for (const sc of HLC.clock.scripts) {
    let t = 0;
    const clock = x.createClock({ node: sc.node, skewMs: sc.skewMs, now: () => t });
    for (const [at, op, arg, out] of sc.steps) {
      t = at;
      const got = op === 'observe' ? (clock.observe(arg), null) : op === 'peek' ? clock.peek() : clock[op](arg === null ? undefined : arg);
      assert.deepEqual(got, out, `${sc.name}: ${op}(${arg})`);
    }
  }
});

test('مبدلِ وب = fromV2ِ سرور و همان fromLegacy (core/test-vectors/sync-v3-convert-v1.json)', () => {
  for (const [name, t] of Object.entries(CONV.tables)) {
    const s = V3.specOf(name);
    assert.ok(s, `جدولِ ${name} در کلاینت نیست`);
    for (const k of ['policy', 'money', 'qty', 'counters', 'derived', 'localOnly', 'cancel', 'v3only']) {
      assert.deepEqual(s[k], t[k], `${name}.${k}`);
    }
  }
  assert.deepEqual(Object.keys(CONV.tables).sort(), Object.keys(V3.TABLES).sort());
  for (const c of CONV.cases) {
    const r = V3.fromLegacyOp(c.in, CONV.hlc);
    assert.deepEqual(r.ops, c.ops, `ops: ${c.in.table} ${c.in.type} ${c.in.op_id}`);
    assert.deepEqual(r.dropped, c.dropped);
    assert.equal(r.native, c.native, `native: ${c.in.op_id}`);
    assert.equal(r.reason, c.reason, `reason: ${c.in.op_id}`);
  }
  //  همان قاعدهٔ money-v1.json — بی گردکردنِ دوم
  const money = JSON.parse(fs.readFileSync(path.join(ROOT, 'license', 'test-vectors', 'money-v1.json'), 'utf8'));
  for (const [x, minor] of money.fromLegacy.money) {
    const r = V3.fromLegacyOp({ op_id: 'm1', table: 'expenses', row_id: 'e', type: 'insert', fields: { amount: x } }, CONV.hlc);
    assert.equal(r.ops[0].fields.amountMinor, minor, `amount ${x}`);
  }
});

test('تنظیمِ هر دستگاه: پیش‌فرض v1؛ گذر با صفِ پر رد می‌شود', () => {
  const mem = new Map();
  const storage = { getItem: (k) => (mem.has(k) ? mem.get(k) : null), setItem: (k, v) => mem.set(k, String(v)) };
  assert.equal(V3.choice(storage), 'v1');
  storage.setItem(V3.CHOICE_KEY, 'something');
  assert.equal(V3.choice(storage), 'v1');
  assert.equal(V3.setChoice('v3', 2, storage).ok, false);
  assert.equal(V3.choice(storage), 'v1');
  assert.equal(V3.setChoice('v3', 0, storage).ok, true);
  assert.equal(V3.choice(storage), 'v3');
  //  بی TohidAccount یا بی IndexedDB نصب نمی‌شود — v1 دست نمی‌خورد
  assert.equal(V3.install(null, { storage }), false);
});

/* ---------------------------------------------------- با سرورِ واقعی */

const P1 = { id: 'p-v3c-1', name: 'شیر', salePrice: 115.5, purchasePrice: 1.005, minStock: 2.5, stock: 40, barcodes: ['6261'] };
let A;
let B;

test('نخستین بارگذاری: دفترِ امروز ⇒ opِ v3 با fromLegacy؛ سند + اقلام + حرکت در یک tx', async () => {
  A = device('v3c-dev-a', owner.accessToken, {
    products: [P1],
    sales: [{ id: 's-v3c-1', invoiceNo: 1, total: 231, discount: 0, finalTotal: 231, paidAmount: 100, remaining: 131, status: 'completed', syncStatus: 'pending' }],
    saleItems: [{ id: 'si-v3c-1', saleId: 's-v3c-1', productId: P1.id, quantity: 2, returnedQty: 0, unitPrice: 115.5, purchasePrice: 1.005, totalPrice: 231 }],
    stockMovements: [{ id: 'm-v3c-0', productId: P1.id, qty: 40, reason: 'purchase' }, { id: 'm-v3c-1', productId: P1.id, qty: -2, reason: 'sale' }],
    productCategories: ['لبنیات'],
  });
  await A.engine.load();
  const r = await A.engine.sync();
  assert.equal(r.ok, true, JSON.stringify(r));
  assert.equal(A.engine.queued(), 0);
  assert.equal(A.engine.status().state, 'synced');

  const p = await rowOf('products', P1.id);
  assert.equal(p.data.salePriceMinor, 11550);
  //  ۱٫۰۰۵ × ۱۰۰ = ۱۰۰٫۴۹۹۹… ⇒ ۱۰۰ — همان ردیفِ money-v1.json، نه «درست‌ترِ» ۱۰۱ (قاعدهٔ دوم)
  assert.equal(p.data.purchasePriceMinor, 100);
  assert.equal(p.data.minStockMilli, 2500);
  assert.equal(p.data.stock, undefined, 'مانده همگام نمی‌شود');
  const s = await rowOf('sales', 's-v3c-1');
  assert.ok(s, JSON.stringify(A.engine.rejected()));
  assert.equal(s.data.paidAmountMinor, 10000);
  assert.equal(s.data.syncStatus, undefined, 'نشانِ محلی نمی‌رود');
  const m = await rowOf('stockMovements', 'm-v3c-1');
  assert.equal(m.data.qtyMilli, -2000);
  //  سند، قلم و حرکت با یک tx رفتند
  const txs = await many(
    `SELECT DISTINCT tx_id FROM sync_v3_ops WHERE app='shop' AND account_id=$1 AND table_name IN ('sales','saleItems','stockMovements')`, [shopId]
  );
  assert.equal(txs.length, 1);
  assert.match(txs[0].tx_id, /^tx\./);
});

test('دستگاهِ دوم از Snapshot؛ ویرایشِ قیمت با Pull می‌رسد و پژواک نمی‌سازد', async () => {
  B = device('v3c-dev-b', owner.accessToken, {});
  await B.engine.load();
  assert.equal((await B.engine.sync()).ok, true);
  const p = find(B.ledger, 'products', P1.id);
  assert.equal(p.salePrice, 115.5);
  assert.equal(find(B.ledger, 'saleItems', 'si-v3c-1').quantity, 2);
  assert.deepEqual(B.ledger.get().productCategories, ['لبنیات']);
  assert.equal(B.engine.queued(), 0, 'Snapshot هیچ opی نمی‌سازد');

  const d = B.ledger.get();
  d.products = d.products.map((x) => (x.id === P1.id ? { ...x, salePrice: 120.25 } : x));
  B.ledger.write(d);
  assert.equal((await B.engine.sync()).ok, true);
  assert.equal((await rowOf('products', P1.id)).data.salePriceMinor, 12025);

  assert.equal((await A.engine.sync()).ok, true);
  assert.equal(find(A.ledger, 'products', P1.id).salePrice, 120.25);
  assert.equal(await A.engine.record(), 0, 'اعمالِ opِ رسیده opِ تازه نمی‌سازد');
});

test('دلتا: پاسخِ گم‌شده ⇒ تکرار «تکراری» است؛ شمارنده یک بار جمع می‌شود', async () => {
  const d = A.ledger.get();
  d.sales = d.sales.map((x) => (x.id === 's-v3c-1' ? { ...x, paidAmount: 150.25, remaining: 80.75 } : x));
  A.ledger.write(d);
  assert.equal(await A.engine.record(), 1);
  A.tr.loseNext = 1;
  const r1 = await A.engine.pushOnce();
  assert.equal(r1.ok, false);
  assert.ok(r1.retryInMs > 0);
  assert.equal(A.engine.queued(), 1, 'بی پاسخ از صف بیرون نمی‌رود');
  const r2 = await A.engine.pushOnce();
  assert.equal(r2.ok, true);
  assert.equal(A.engine.queued(), 0);
  const s = await rowOf('sales', 's-v3c-1');
  assert.equal(s.data.paidAmountMinor, 15025);
  assert.equal(s.data.remainingMinor, 8075);

  assert.equal((await B.engine.sync()).ok, true);
  const bs = find(B.ledger, 'sales', 's-v3c-1');
  assert.equal(bs.paidAmount, 150.25);
  assert.equal(bs.remaining, 80.75);
});

test('Pull: بسته شدن پس از نوشتنِ دفتر ⇒ بازیابی از ژورنال، cursor جلو، بی جمعِ دوباره', async () => {
  const d = A.ledger.get();
  d.sales = d.sales.map((x) => (x.id === 's-v3c-1' ? { ...x, paidAmount: 160.25, remaining: 70.75 } : x));
  A.ledger.write(d);
  assert.equal((await A.engine.sync()).ok, true);

  //  دستگاهِ B: همان انبار و همان دفتر، ولی برنامه وسطِ نشاندن «بسته می‌شود»
  let crash = true;
  B.engine.failpoint = (at) => { if (crash && at === 'after-ledger') { crash = false; throw new Error('بسته شد'); } };
  const before = B.engine.meta.cursor;
  await assert.rejects(B.engine.pullOnce(), /بسته شد/);
  assert.equal(find(B.ledger, 'sales', 's-v3c-1').paidAmount, 160.25, 'دفتر نشسته بود');

  const again = new V3.Engine({ transport: B.tr, store: B.store, ledger: B.ledger, deviceId: B.name, random: () => 0.5 });
  await again.load();
  assert.ok(again.meta.cursor > before, 'ژورنال کار را تمام کرد');
  assert.equal(await again.record(), 0, 'تغییرِ رسیده «تغییرِ من» شمرده نشد');
  assert.equal((await again.pullOnce()).applied, 0, 'همان op دوباره نیامد');
  assert.equal(find(B.ledger, 'sales', 's-v3c-1').paidAmount, 160.25);
  B.engine = again;
  B.engine.keepLocalOnly = true;
});

test('Pull: بسته شدن پیش از نوشتنِ دفتر ⇒ برمی‌گردد و همان صفحه دوباره می‌آید', async () => {
  const d = A.ledger.get();
  d.products = d.products.map((x) => (x.id === P1.id ? { ...x, name: 'شیرِ تازه' } : x));
  A.ledger.write(d);
  assert.equal((await A.engine.sync()).ok, true);
  let crash = true;
  B.engine.failpoint = (at) => { if (crash && at === 'after-journal') { crash = false; throw new Error('بسته شد'); } };
  const before = B.engine.meta.cursor;
  await assert.rejects(B.engine.pullOnce(), /بسته شد/);
  const again = new V3.Engine({ transport: B.tr, store: B.store, ledger: B.ledger, deviceId: B.name, random: () => 0.5 });
  await again.load();
  assert.equal(again.meta.cursor, before);
  assert.equal(find(B.ledger, 'products', P1.id).name, 'شیر');
  assert.equal((await again.pullOnce()).applied, 1);
  assert.equal(find(B.ledger, 'products', P1.id).name, 'شیرِ تازه');
  B.engine = again;
  B.engine.keepLocalOnly = true;
});

test('تعارض: دو دستگاه یک فیلد را بی‌خبر از هم عوض می‌کنند ⇒ برنده با HLC، بازنده در صندوق، برگرداندن', async () => {
  const edit = (dev, name) => {
    const d = dev.ledger.get();
    d.products = d.products.map((x) => (x.id === P1.id ? { ...x, name } : x));
    dev.ledger.write(d);
  };
  edit(A, 'نامِ A');
  assert.equal(await A.engine.record(), 1);
  edit(B, 'نامِ B');
  assert.equal(await B.engine.record(), 1);          //  HLCِ B تازه‌تر است
  assert.equal((await A.engine.pushOnce()).ok, true);
  const pb = await B.engine.pushOnce();
  assert.equal(pb.ok, true);
  assert.equal((await rowOf('products', P1.id)).data.name, 'نامِ B');

  const list = await A.engine.fetchConflicts('open');
  const cf = list.find((c) => c.table === 'products' && c.row_id === P1.id && c.field === 'name');
  assert.ok(cf, JSON.stringify(list));
  assert.equal(cf.kind, 'lww');
  assert.equal(cf.loser_value, 'نامِ A');
  assert.equal(A.engine.status().state, 'conflict');
  assert.ok(A.engine.status().conflictsOpen >= 1);

  const res = await A.engine.resolveConflict(cf.id, 'revert');
  assert.equal(res.ok, true, JSON.stringify(res));
  assert.equal((await rowOf('products', P1.id)).data.name, 'نامِ A');
  assert.equal((await B.engine.sync()).ok, true);
  assert.equal(find(B.ledger, 'products', P1.id).name, 'نامِ A');
  assert.equal(A.engine.conflicts().some((c) => c.id === cf.id), false);
});

test('ویرایشِ سندِ مالی گم نمی‌شود: با format=v2 می‌رود و در صندوقِ تعارض می‌نشیند', async () => {
  const d = A.ledger.get();
  d.sales = d.sales.map((x) => (x.id === 's-v3c-1' ? { ...x, customerName: 'نامِ تازهٔ مشتری' } : x));
  A.ledger.write(d);
  assert.equal(await A.engine.record(), 1);
  assert.equal(A.engine.outbox[0].fmt, 'v2');
  const r = await A.engine.pushOnce();
  assert.equal(r.ok, true);
  assert.equal(A.engine.queued(), 0);
  const list = await A.engine.fetchConflicts('open');
  assert.ok(list.some((c) => c.kind === 'append_only_edit' && c.row_id === 's-v3c-1'), JSON.stringify(list.map((c) => c.kind)));
  assert.equal(find(A.ledger, 'sales', 's-v3c-1').customerName, 'نامِ تازهٔ مشتری', 'سندِ محلی دست نخورد');
});

test('ردشده: تخفیفِ بی اجازه از صف بیرون می‌رود ولی با دلیل می‌ماند؛ سندِ محلی دست نمی‌خورد', async () => {
  const S = device('v3c-dev-staff', staff.accessToken, {});
  await S.engine.load();
  assert.equal((await S.engine.sync()).ok, true);
  const d = S.ledger.get();
  d.sales = [...d.sales, { id: 's-v3c-disc', total: 100, discount: 10, finalTotal: 90, paidAmount: 90, remaining: 0, status: 'completed' }];
  S.ledger.write(d);
  assert.equal(await S.engine.record(), 1);
  const r = await S.engine.pushOnce();
  assert.equal(r.ok, true);
  assert.equal(r.rejected, 1);
  assert.equal(S.engine.queued(), 0);
  const rej = S.engine.rejected();
  assert.equal(rej.length, 1);
  assert.deepEqual(rej[0].reasons, ['discount_not_allowed']);
  assert.ok(find(S.ledger, 'sales', 's-v3c-disc'), 'فروشِ ثبت‌شده روی دستگاه می‌ماند');
  assert.equal(S.engine.status().state, 'conflict');
  assert.equal(await rowOf('sales', 's-v3c-disc'), null);
});

test('دسته‌های کوچک: ۴۵۰ کالا در چند دسته؛ همه می‌رسند', async () => {
  const d = A.ledger.get();
  for (let i = 0; i < 450; i++) d.products.push({ id: `p-v3c-bulk-${i}`, name: `کالای ${i}`, salePrice: i + 0.5, purchasePrice: i });
  A.ledger.write(d);
  assert.equal(await A.engine.record(), 450);
  const before = A.tr.calls.filter((c) => c === 'POST /api/sync/v3/push').length;
  assert.equal((await A.engine.sync()).ok, true);
  const pushes = A.tr.calls.filter((c) => c === 'POST /api/sync/v3/push').length - before;
  assert.ok(pushes >= 3, `دسته‌ها: ${pushes}`);
  const n = await one(`SELECT COUNT(*)::int AS n FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND row_id LIKE 'p-v3c-bulk-%'`, [shopId]);
  assert.equal(n.n, 450);
  assert.equal((await rowOf('products', 'p-v3c-bulk-7')).data.salePriceMinor, 750);
});

test('۴۲۶: سرور پروتکلِ برنامه را نمی‌شناسد ⇒ هیچ چیز اعمال نمی‌شود، صف می‌ماند، موتور می‌ایستد', async () => {
  const N = device('v3c-dev-new', owner.accessToken, {}, { protocol: 4 });
  await N.engine.load();
  assert.equal((await N.engine.sync()).ok, true);   //  Snapshot و Pull بی پروتکل‌اند
  const d = N.ledger.get();
  d.products = [...d.products, { id: 'p-v3c-426', name: 'نو', salePrice: 1 }];
  N.ledger.write(d);
  const r = await N.engine.sync();
  assert.equal(r.ok, false);
  assert.equal(r.upgrade, true);
  assert.equal(N.engine.queued(), 1, 'opها نگه داشته شدند');
  const st = N.engine.status();
  assert.equal(st.state, 'upgrade');
  assert.equal(st.upgradeRequired, true);
  assert.match(st.lastError, /به‌روز کنید/);
  assert.equal(await rowOf('products', 'p-v3c-426'), null);
  const calls = N.tr.calls.length;
  assert.equal((await N.engine.sync()).stopped, true);
  assert.equal(N.tr.calls.length, calls, 'پس از ۴۲۶ دیگر درخواستی نمی‌رود');
  //  حالِ ایستاده از بسته و باز شدن هم می‌گذرد
  const again = new V3.Engine({ transport: N.tr, store: N.store, ledger: N.ledger, deviceId: N.name, protocol: 4 });
  await again.load();
  assert.equal(again.status().upgradeRequired, true);
  assert.equal(again.queued(), 1);
});

test('قطعِ شبکه: صف می‌ماند و فاصلهٔ تلاش نمایی با پراکندگی بزرگ می‌شود', async () => {
  const d = A.ledger.get();
  d.products = d.products.map((x) => (x.id === P1.id ? { ...x, salePrice: 130 } : x));
  A.ledger.write(d);
  await A.engine.record();
  A.tr.offline = true;
  const waits = [];
  for (let i = 0; i < 4; i++) waits.push((await A.engine.pushOnce()).retryInMs);
  assert.deepEqual(waits, [0, 1, 2, 3].map((n) => core.backoffMs(n, 0.5)));
  assert.deepEqual(waits, [2000, 4000, 8000, 16000]);
  //  پراکندگی: صد دستگاهی که با هم قطع شدند با هم برنمی‌گردند
  const J = device('v3c-dev-jitter', owner.accessToken, { products: [{ id: 'p-v3c-j', name: 'ج' }] }, { random: () => 0 });
  await J.engine.load();
  await J.engine.sync();
  const dj = J.ledger.get();
  dj.products[0] = { ...dj.products[0], name: 'جج' };
  J.ledger.write(dj);
  await J.engine.record();
  J.tr.offline = true;
  assert.equal((await J.engine.pushOnce()).retryInMs, 1500);
  assert.equal(A.engine.queued(), 1);
  assert.equal(A.engine.status().state, 'failed');
  A.tr.offline = false;
  assert.equal((await A.engine.sync()).ok, true);
  assert.equal(A.engine.queued(), 0);
  assert.equal(A.engine.status().lastError, '');
  assert.notEqual(A.engine.status().state, 'failed');
  assert.equal((await rowOf('products', P1.id)).data.salePriceMinor, 13000);
});

test('وضعیت برای نوار و نگهبانِ خروج (SYN-08، SYN-10): منتظر، آخرین موفق، خطای آخر، اختلافِ ساعت', async () => {
  const st = A.engine.status();
  assert.equal(st.engine, 'v3');
  assert.equal(st.unsynced, false);
  assert.ok(st.lastOkAt > 0);
  assert.equal(typeof st.clockOffsetMs, 'number');
  const srv = await h.api('GET', `/api/sync/v3/status?device_id=${A.name}`, { token: owner.accessToken });
  assert.equal(srv.status, 200);
  assert.equal(srv.body.device.device_id, A.name);
  assert.equal(srv.body.device.queued, 0);
});
