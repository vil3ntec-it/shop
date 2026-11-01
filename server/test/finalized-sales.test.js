'use strict';
/**
 * S89 + نیمهٔ سرورِ SEC-03 — فاکتورِ ثبت‌شده در سرور سرِ جا ویرایش/حذف نمی‌شود (`lib/finalized-sales.js`).
 *
 *   ۱) پرچمِ خاموشِ `shop.doc_chain` ⇒ همان امروز: ویرایش و حذف می‌نشیند، هیچ رویدادی.
 *   ۲) پرچمِ روشن + دستگاهِ زنجیره‌دار ⇒ ویرایشِ مبلغ، قلم، پرداخت، تاریخ، شماره، مشتریِ نسیه، حذف، قلمِ تازه و
 *      برگشت از لغو `rejected` (`sale_finalized_edit_forbidden`) + رویدادِ `security.doc_chain`؛ بقیهٔ دسته می‌نشیند.
 *   ۳) آزاد: لغو (سندِ معکوس)، برگشت (`returnedQty`/`debtSettled`)، فروشنده، پر کردنِ شمارهٔ خالی، مشتریِ فاکتورِ
 *      نقد، ادغامِ مشتری (CRM-10)، نوشتنِ همان مقدار، قلم‌های همان ثبت در دو دسته.
 *   ۴) تکرار و بازپخش: همان op_id «duplicate»؛ درجِ دوبارهٔ نسخهٔ کهنهٔ همان سند (پس از بازیابی) پذیرفته و بی
 *      بازنویسی — لغو و شمارنده‌ها می‌مانند.
 *   ۵) برنامهٔ بی زنجیره ⇒ همان امروز + رویداد (`enforced: false`).
 *   ۶) جداییِ دکان‌ها: نه پرچم، نه دستگاهِ زنجیره‌دار و نه ردیفِ دکانِ دیگر.
 *   ۷) راهِ قدیم (`/api/sync`: فقط رویداد) و API ِ مستقیم (`/api/sales`…: ۴۰۹ و هیچ نوشتنی).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

const CHAIN = 'dev-fin-chain';   // دستگاهِ زنجیره‌دار (سری B1)
const OLD = 'dev-fin-old';       // برنامهٔ کهنه، بی زنجیره
const REASON = 'sale_finalized_edit_forbidden';

let owner;
let shopId;
let other;
let otherShop;
let seq = 0;

const opId = () => `01JFIN${String(Date.now()).slice(-8)}${String(++seq).padStart(5, '0')}`;
const op = (table, rowId, type, fields, ts = Date.now()) => ({ op_id: opId(), ts, table, row_id: rowId, type, fields });
const push = (dev, ops, token = owner.accessToken) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': dev }, body: { device_id: dev, schema_version: SCHEMA, caps: ['a00'], ops },
});
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const rowOf = async (table, id, shop = shopId) => {
  const r = await one(
    "SELECT data, deleted_at FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3",
    [shop, table, id]
  );
  return r ? { ...r.data, _deleted: r.deleted_at != null } : null;
};
const flag = (on, shop = shopId) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain',$2,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [shop, on ? 'true' : 'false']
);
const events = async (shop = shopId) => (await query(
  "SELECT target_type, target_id, detail FROM audit_logs WHERE action='security.doc_chain' AND shop_id=$1 ORDER BY created_at, id", [shop]
)).rows.map((r) => ({ type: r.target_type, sale: r.target_id, ...(typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) }))
  .filter((e) => e.problem === 'finalized_edit');

/** فاکتور + دو قلم، همه با یک `ts` (همان یک ثبت) */
function saleOps(id, { ts = Date.now(), credit = false, debtor = 'deb-1', invoiceNumber = 7 } = {}) {
  const sale = {
    id, invoiceNumber, series: 'B1', total: 1000, discountType: 'amount', discountValue: 0, discount: 0, finalTotal: 1000,
    paymentMethod: credit ? 'credit' : 'cash', debtorId: credit ? debtor : null,
    paidAmount: credit ? 400 : 1000, remaining: credit ? 600 : 0, status: 'completed',
    debtGiven: credit ? 600 : 0, debtSettled: 0, createdAt: ts, date: '2026-10-30', syncStatus: 'pending',
    cancelReason: '', seller: 'صاحب', channel: 'pos',
  };
  const items = [
    { id: `${id}-i1`, saleId: id, productId: 'p1', name: '', quantity: 2, unitPrice: 300, purchasePrice: 200, totalPrice: 600, returnedQty: 0 },
    { id: `${id}-i2`, saleId: id, productId: 'p2', name: '', quantity: 1, unitPrice: 400, purchasePrice: 250, totalPrice: 400, returnedQty: 0 },
  ];
  return [op('sales', id, 'insert', sale, ts), ...items.map((it) => op('saleItems', it.id, 'insert', it, ts))];
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ فاکتور');
  shopId = (await h.post('/api/shop', { name: 'دکانِ فاکتور' }, { token: owner.accessToken })).body.shop.id;
  other = await h.newUser('صاحبِ دکانِ دیگر');
  otherShop = (await h.post('/api/shop', { name: 'دکانِ دیگر' }, { token: other.accessToken })).body.shop.id;
  //  دستگاهِ زنجیره‌دار = سری‌ای که گزارشِ زنجیره داده (همان ردیفی که `doc-chain.report` می‌سازد)
  await query(
    `INSERT INTO device_chain_heads (shop_id, series, epoch, device_id, reported_at, created_at) VALUES ($1,'B1',1,$2,1,1)`,
    [shopId, CHAIN]
  );
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش: همان امروز — ویرایش و حذفِ فاکتورِ ثبت‌شده می‌نشیند، هیچ رویدادی', async () => {
  let r = await push(CHAIN, saleOps('s-off'));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);
  r = await push(CHAIN, [
    op('sales', 's-off', 'update', { total: 900, paidAmount: { $inc: -100 } }),
    op('saleItems', 's-off-i1', 'update', { quantity: 3 }),
    op('saleItems', 's-off-i2', 'delete', null),
    op('saleItems', 's-off-i3', 'insert', { id: 's-off-i3', saleId: 's-off', productId: 'p3', quantity: 1, unitPrice: 50, totalPrice: 50 }),
  ]);
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied', 'applied']);
  assert.equal((await rowOf('sales', 's-off')).total, 900);
  assert.equal((await rowOf('sales', 's-off')).paidAmount, 900);
  assert.equal((await rowOf('saleItems', 's-off-i1')).quantity, 3);
  assert.equal((await rowOf('saleItems', 's-off-i2'))._deleted, true);
  assert.deepEqual(await events(), []);
});

test('پرچمِ روشن + دستگاهِ زنجیره‌دار: هر ویرایشِ مالی و هر حذف رد؛ بقیهٔ دسته می‌نشیند؛ رویداد', async () => {
  await flag(true);
  let r = await push(CHAIN, [...saleOps('s1'), ...saleOps('s2', { credit: true })]);
  assert.deepEqual(statuses(r), Array(6).fill('applied'), 'ثبتِ فاکتورِ تازه آزاد است');

  const forbidden = [
    op('sales', 's1', 'update', { total: 900 }),
    op('sales', 's1', 'update', { finalTotal: 900, discount: 100 }),
    op('sales', 's1', 'update', { paidAmount: { $inc: -100 } }),
    op('sales', 's1', 'update', { payments: [{ method: 'cash', kind: 'cash', amount: 50000, status: 'confirmed' }] }),
    op('sales', 's1', 'update', { date: '2026-10-01' }),
    op('sales', 's1', 'update', { createdAt: 1 }),
    op('sales', 's1', 'update', { invoiceNumber: 8 }),
    op('sales', 's1', 'update', { status: 'pending' }),
    op('sales', 's2', 'update', { debtorId: 'deb-other' }),
    op('saleItems', 's1-i1', 'update', { quantity: 3 }),
    op('saleItems', 's1-i1', 'update', { unitPrice: 1, totalPrice: 2 }),
    op('saleItems', 's1-i2', 'update', { saleId: 's2' }),
    op('saleItems', 's1-i2', 'delete', null),
    op('sales', 's1', 'delete', null),
    //  قلمِ تازه برای فاکتورِ ثبت‌شده، از ثبتِ دیگر (ts ِ دیگر)
    op('saleItems', 's1-i3', 'insert', { id: 's1-i3', saleId: 's1', productId: 'p3', quantity: 1, unitPrice: 50, totalPrice: 50 }, Date.now() + 5),
  ];
  for (const o of forbidden) {
    r = await push(CHAIN, [o, op('products', `p-ok-${seq}`, 'insert', { name: 'کالای دیگر' })]);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(statuses(r), [`rejected:${REASON}`, 'applied'], `${o.table} ${o.type} ${JSON.stringify(o.fields)}`);
  }
  const s1 = await rowOf('sales', 's1');
  assert.deepEqual([s1.total, s1.finalTotal, s1.paidAmount, s1.date, s1.invoiceNumber, s1.status, s1._deleted],
    [1000, 1000, 1000, '2026-10-30', 7, 'completed', false]);
  assert.equal(s1.payments, undefined);
  assert.equal((await rowOf('sales', 's2')).debtorId, 'deb-1');
  const i1 = await rowOf('saleItems', 's1-i1');
  assert.deepEqual([i1.quantity, i1.unitPrice, i1.totalPrice], [2, 300, 600]);
  const i2 = await rowOf('saleItems', 's1-i2');
  assert.deepEqual([i2.saleId, i2._deleted], ['s1', false]);
  assert.equal(await rowOf('saleItems', 's1-i3'), null);

  const ev = await events();
  assert.equal(ev.length, forbidden.length, 'یک رویداد برای هر نوشتن');
  assert.ok(ev.every((e) => e.enforced === true && e.path === 'v1' && e.deviceId === CHAIN && e.type === 'sale'));
  assert.deepEqual(ev.map((e) => e.edits), [
    'sales/s1 update: total', 'sales/s1 update: discount,finalTotal', 'sales/s1 update: paidAmount', 'sales/s1 update: payments',
    'sales/s1 update: date', 'sales/s1 update: createdAt', 'sales/s1 update: invoiceNumber', 'sales/s1 update: status',
    'sales/s2 update: debtorId', 'saleItems/s1-i1 update: quantity', 'saleItems/s1-i1 update: unitPrice,totalPrice',
    'saleItems/s1-i2 update: saleId', 'saleItems/s1-i2 delete: *delete*', 'sales/s1 delete: *delete*', 'saleItems/s1-i3 insert: +line',
  ]);
});

test('آزاد: لغو و برگشت (سندِ معکوس)، برچسب، پر کردنِ شماره، مشتریِ نقد، ادغامِ مشتری، همان مقدار، بُرشِ دسته', async () => {
  const before = (await events()).length;
  //  برگشت روی s1: ردیفِ برگشت + شمارنده‌ها
  let r = await push(CHAIN, [
    op('returns', 'ret-1', 'insert', { id: 'ret-1', saleId: 's1', saleItemId: 's1-i1', productId: 'p1', quantity: 1, amount: 300 }),
    op('saleItems', 's1-i1', 'update', { returnedQty: { $inc: 1 } }),
    op('sales', 's1', 'update', { debtSettled: { $inc: 0.5 } }),
  ]);
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);
  assert.equal((await rowOf('saleItems', 's1-i1')).returnedQty, 1);

  //  لغوِ فاکتورِ نسیه (سندِ معکوسِ SEC-03): وضعیت، دلیل، بستنِ بدهی
  r = await push(CHAIN, [op('sales', 's2', 'update', { status: 'cancelled', cancelReason: 'ویرایش در سبد', debtSettled: { $inc: 600 } })]);
  assert.deepEqual(statuses(r), ['applied']);
  //  برگشت از لغو نه
  r = await push(CHAIN, [op('sales', 's2', 'update', { status: 'completed' })]);
  assert.deepEqual(statuses(r), [`rejected:${REASON}`]);
  assert.equal((await rowOf('sales', 's2')).status, 'cancelled');

  //  برچسب‌ها و همان مقدار
  r = await push(CHAIN, [op('sales', 's1', 'update', { seller: 'مدیر', syncStatus: 'synced', total: 1000, discount: 0 })]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await rowOf('sales', 's1')).seller, 'مدیر');

  //  مشتریِ فاکتورِ نقد
  r = await push(CHAIN, [op('sales', 's1', 'update', { debtorId: 'deb-9' })]);
  assert.deepEqual(statuses(r), ['applied']);

  //  فاکتورِ کهنهٔ بی شماره: یک بار پر می‌شود، بعد نه
  r = await push(CHAIN, saleOps('s3', { invoiceNumber: null }));
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);
  r = await push(CHAIN, [op('sales', 's3', 'update', { invoiceNumber: 41 })]);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(CHAIN, [op('sales', 's3', 'update', { invoiceNumber: 42 })]);
  assert.deepEqual(statuses(r), [`rejected:${REASON}`]);

  //  ادغامِ مشتری (CRM-10): فاکتور پیش از ردیفِ مشتری در همان دسته (ترتیبِ `LedgerDiff.COLLECTIONS`)
  r = await push(CHAIN, saleOps('s4', { credit: true, debtor: 'deb-a' }));
  r = await push(CHAIN, [
    op('sales', 's4', 'update', { debtorId: 'deb-b' }),
    op('debtors', 'deb-a', 'update', { mergedInto: 'deb-b' }),
  ]);
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  //  دستگاهِ دیرتر (sweep): مشتریِ ادغام‌شده در سرور
  r = await push(CHAIN, saleOps('s5', { credit: true, debtor: 'deb-a' }));
  r = await push(CHAIN, [op('sales', 's5', 'update', { debtorId: 'deb-b' })]);
  assert.deepEqual(statuses(r), ['applied']);

  //  ثبتِ بزرگ در دو دسته (`OpQueue.cut`): قلم‌ها با همان ts پسِ فاکتور
  const ts = Date.now() + 1000;
  const [sale, ...items] = saleOps('s6', { ts });
  r = await push(CHAIN, [sale]);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(CHAIN, items);
  assert.deepEqual(statuses(r), ['applied', 'applied']);

  assert.equal((await events()).length, before + 2, 'فقط برگشت از لغو و شمارهٔ دوم');
});

test('تکرار و بازپخش پس از بازیابی: هرگز رد نمی‌شود و لغو/شمارنده‌ها برنمی‌گردند', async () => {
  const before = (await events()).length;
  const ts = Date.now() + 2000;
  const original = saleOps('s7', { ts, credit: true });
  let r = await push(CHAIN, original);
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);

  //  همان op_id (پاسخِ گم‌شده و تلاشِ دوباره) ⇐ «تکراری» ِ خودِ v1
  r = await push(CHAIN, original);
  assert.deepEqual(statuses(r), ['duplicate', 'duplicate', 'duplicate']);

  //  لغو، بعد بازپخشِ نسخهٔ پیش از لغو با op_id ِ تازه (پشتیبانِ کهنه روی دستگاهی دیگر)
  r = await push(CHAIN, [op('sales', 's7', 'update', { status: 'cancelled', cancelReason: 'دلیل', debtSettled: { $inc: 600 } })]);
  assert.deepEqual(statuses(r), ['applied']);
  const replay = () => original.map((o) => ({ ...o, op_id: opId() }));
  //  دستگاهِ زنجیره‌دار: پذیرفته و بی بازنویسی
  r = await push(CHAIN, replay());
  assert.deepEqual(statuses(r), ['duplicate', 'duplicate', 'duplicate']);
  assert.ok(r.body.results.every((x) => x.replay === true));
  let s7 = await rowOf('sales', 's7');
  assert.deepEqual([s7.status, s7.debtSettled, s7.total], ['cancelled', 600, 1000], 'سندِ معکوس سرِ جایش ماند');
  //  ویرایشی که همان مقدارها را دوباره می‌نویسد (ادغامِ سه‌طرفه، #407)
  r = await push(CHAIN, [op('sales', 's7', 'update', { total: 1000, finalTotal: 1000, paidAmount: { $inc: 0 }, status: 'cancelled' })]);
  assert.deepEqual(statuses(r), ['applied']);
  //  درجِ دوباره با مبلغِ دیگر بازپخش نیست
  const changed = { ...original[0], op_id: opId(), fields: { ...original[0].fields, total: 1, finalTotal: 1 } };
  r = await push(CHAIN, [changed]);
  assert.deepEqual(statuses(r), [`rejected:${REASON}`]);
  assert.equal((await events()).length, before + 1, 'بازپخشِ یکسان رویداد نمی‌سازد');

  //  برنامهٔ بی زنجیره: همان امروز (درجِ دوباره می‌نشیند و می‌نویسد)، بی رویداد
  r = await push(OLD, replay());
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);
  s7 = await rowOf('sales', 's7');
  assert.deepEqual([s7.status, s7.debtSettled], ['completed', 0], 'log = رفتارِ امروز');
  assert.equal((await events()).length, before + 1);
});

test('برنامهٔ بی زنجیره (پرچمِ روشن): همان امروز می‌نشیند، فقط رویداد', async () => {
  let r = await push(OLD, saleOps('s8'));
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);
  r = await push(OLD, [op('sales', 's8', 'update', { total: 1 }), op('saleItems', 's8-i1', 'delete', null)]);
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  assert.equal((await rowOf('sales', 's8')).total, 1);
  const ev = (await events()).filter((e) => e.sale === 's8');
  assert.equal(ev.length, 1, 'یک رویداد برای هر فاکتور در هر نوشتن');
  assert.equal(ev[0].enforced, false);
  assert.equal(ev[0].deviceId, OLD);
  assert.equal(ev[0].edits, 'sales/s8 update: total; saleItems/s8-i1 delete: *delete*');
});

test('جداییِ دکان‌ها: پرچم، دستگاهِ زنجیره‌دار و ردیف‌ها مالِ همان دکان‌اند', async () => {
  //  همان شناسه‌ها در دکانِ دیگر ⇐ فاکتورِ تازه؛ پرچمِ آن دکان خاموش ⇐ ویرایش می‌نشیند
  let r = await push(CHAIN, saleOps('s1'), other.accessToken);
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);
  r = await push(CHAIN, [op('sales', 's1', 'update', { total: 5 })], other.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  assert.deepEqual(await events(otherShop), []);
  //  پرچمِ روشن در دکانِ دیگر: CHAIN آن‌جا زنجیره ندارد ⇐ فقط رویداد
  await flag(true, otherShop);
  r = await push(CHAIN, [op('sales', 's1', 'update', { total: 6 })], other.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await rowOf('sales', 's1', otherShop)).total, 6);
  const ev = await events(otherShop);
  assert.equal(ev.length, 1);
  assert.equal(ev[0].enforced, false);
  //  دکانِ نخست دست نخورد
  assert.equal((await rowOf('sales', 's1')).total, 1000);
  assert.ok((await events()).every((e) => e.sale !== 's1' || e.enforced === true));
  await flag(false, otherShop);
});

test('راهِ قدیم و API ِ مستقیم: API ۴۰۹ و هیچ نوشتنی؛ `/api/sync` فقط رویداد؛ پرچمِ خاموش همان امروز', async () => {
  const T = owner.accessToken;
  const H = { 'X-App': 'shop' };
  const legacy = (id) => h.api('GET', `/api/sales/${id}`, { token: T, headers: H });
  const sale = { invoiceNumber: 9, total: 500, finalTotal: 500, paidAmount: 500, remaining: 0, status: 'completed', date: '2026-10-30', createdAt: 5 };

  //  ثبتِ کامل (فاکتور + قلم‌ها در یک دسته) آزاد است
  let r = await h.api('POST', '/api/sales/full', {
    token: T, headers: H,
    body: { sale: { id: 'L1', ...sale }, items: [{ id: 'L1-i1', productId: 'p1', quantity: 1, unitPrice: 500, totalPrice: 500 }] },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  r = await h.api('PUT', '/api/sales/L1', { token: T, headers: H, body: { data: { ...sale, total: 400, finalTotal: 400 } } });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, REASON);
  r = await h.api('DELETE', '/api/sales/L1', { token: T, headers: H });
  assert.equal(r.status, 409);
  r = await h.api('PUT', '/api/sale-items/L1-i1', { token: T, headers: H, body: { data: { saleId: 'L1', productId: 'p1', quantity: 9, unitPrice: 500, totalPrice: 4500 } } });
  assert.equal(r.status, 409);
  r = await h.api('POST', '/api/sale-items', { token: T, headers: H, body: { id: 'L1-i2', data: { saleId: 'L1', productId: 'p2', quantity: 1, unitPrice: 10, totalPrice: 10 } } });
  assert.equal(r.status, 409);
  assert.equal((await legacy('L1')).body.total, 500, 'هیچ بخشی ننشست');
  assert.equal((await h.api('GET', '/api/sale-items/L1-i2', { token: T, headers: H })).status, 404);

  //  لغو (کلِ ردیف با وضعیتِ لغو) آزاد است
  r = await h.api('PUT', '/api/sales/L1', { token: T, headers: H, body: { data: { ...sale, status: 'cancelled', cancelReason: 'اشتباه' } } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await legacy('L1')).body.status, 'cancelled');

  //  راهِ قدیمِ برنامهٔ کهنه: می‌نشیند، فقط رویداد
  r = await h.api('POST', '/api/sync/push', {
    token: T, headers: H,
    body: { deviceId: 'old-web', changes: [{ collection: 'sales', id: 'L1', updatedAt: Date.now(), data: { ...sale, status: 'cancelled', total: 1 } }] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.applied, 1);
  assert.equal((await legacy('L1')).body.total, 1);

  const ev = (await events()).filter((e) => e.sale === 'L1');
  assert.deepEqual(ev.map((e) => [e.path, e.enforced]),
    [['api', true], ['api', true], ['api', true], ['api', true], ['legacy', false]]);

  //  پرچمِ خاموش ⇒ API همان امروز
  await flag(false);
  r = await h.api('PUT', '/api/sales/L1', { token: T, headers: H, body: { data: { ...sale, total: 2 } } });
  assert.equal(r.status, 200);
  assert.equal((await legacy('L1')).body.total, 2);
  await flag(true);
});
