'use strict';
/**
 * SEC-DATA-07 (DATA-04) — برگشتِ کارمند بیش از خودِ فاکتور.
 *
 * نگهبانِ `returns.create` (`lib/staff-approval.js`) تا امروز فقط سقفِ نقش (تجمعیِ هر فاکتور) را
 * می‌سنجید: شاگردی که برگشت دارد، یا کلاینتِ دست‌کاری‌شده با حسابِ او، زیرِ سقفش برگشتی بیش از
 * جمعِ خودِ فاکتور می‌فرستاد و پولِ دخل بیرون می‌رفت. با پرچمِ `shop.returns.sale_cap`
 * (live_config، پیش‌فرض خاموش — برنامهٔ پیش از #586 هر برگشتِ تکه‌ای را جدا گرد می‌کرد):
 *   جمعِ برگشت‌های فاکتور (ثبت‌شده در هر دو دفتر + همین دسته + همین سند)
 *     ≤ `finalTotal` ِ فاکتور + نیم افغانی برای هر قلمِ فاکتور (گردِ قلم‌ها)
 * وگرنه رد با `return_exceeds_sale` و صندوقِ `perm_denied` — هر دو راه (v3 و v1)، فاکتورِ سرور یا
 * همین دسته. فاکتوری که سرور ندارد، و صاحبِ دکان (همه‌کاره)، سنجیده نمی‌شوند؛ پرچمِ خاموش ⇒ همان امروز.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const liveConfig = require('../src/lib/live-config');

const FLAG = 'shop.returns.sale_cap';

let shopId;
let owner;
let staff;
let n = 0;
const oid = () => `01JRC${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', {
  token: who.accessToken, body: { device_id: device, protocol: 3, ops },
});
const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: who.accessToken, body: { device_id: device, schema_version: 1, ops },
});
const boxOf = (opId) => one(
  `SELECT * FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2 AND status='open'`, [shopId, opId]
);
const flag = (value) => liveConfig.set(shopId, FLAG, value, 'test');

/* v3: فاکتور با قلم‌ها، و برگشت */
const sale3 = (id, finalMinor) => ({
  op_id: oid(), hlc: H('o'), table: 'sales', row_id: id, kind: 'insert',
  fields: { totalMinor: finalMinor, discountMinor: 0, finalTotalMinor: finalMinor, status: 'completed' },
});
const item3 = (id, saleId, totalMinor) => ({
  op_id: oid(), hlc: H('o'), table: 'saleItems', row_id: id, kind: 'insert',
  fields: { saleId, quantityMilli: 1000, totalPriceMinor: totalMinor },
});
const ret3 = (id, saleId, amountMinor) => ({
  op_id: oid(), hlc: H('s'), table: 'returns', row_id: id, kind: 'insert',
  fields: { saleId, amountMinor },
});

/* v1: افغانیِ اعشاری؛ opهای یک «ثبت» یک ts */
const v1op = (table, rowId, fields, ts) => ({ op_id: oid(), table, row_id: rowId, type: 'insert', ts, fields });

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ سقفِ برگشت', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ سقفِ برگشت' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ سقفِ برگشت', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  staff = await h.signIn(s, 'shop');
});
test.after(async () => { await h.stop(); });

test('آزمونِ مرجع (پرچم خاموش): برگشتِ شاگرد بیش از جمعِ فاکتور همان امروز می‌نشیند', async () => {
  assert.equal((await push3(owner, 'o-1', [sale3('s-off', 10000)])).body.results[0].status, 'applied');
  const r = await push3(staff, 's-1', [ret3('r-off', 's-off', 15000)]);
  assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body));
});

test('پرچم روشن، Sync v3: بیش از جمعِ فاکتور ⇒ return_exceeds_sale و صندوق؛ تا جمع می‌نشیند و جمع تجمعی است', async () => {
  await flag(true);
  try {
    assert.equal((await push3(owner, 'o-1', [sale3('s-v3', 10000), item3('si-v3', 's-v3', 10000)])).body.results
      .every((x) => x.status === 'applied'), true);
    const over = ret3('r-v3-over', 's-v3', 15000);
    const r = await push3(staff, 's-1', [over]);
    assert.equal(r.body.results[0].status, 'rejected', JSON.stringify(r.body));
    assert.equal(r.body.results[0].reason, 'return_exceeds_sale');
    const box = await boxOf(over.op_id);
    assert.ok(box, 'صندوقِ تعارض برای صاحب دکان');
    assert.equal(box.kind, 'perm_denied');
    assert.equal(box.detail.perm, 'returns.create');
    assert.equal(box.detail.sale_total, 10000);
    assert.equal(box.detail.cumulative, 15000);
    assert.equal(await one(`SELECT 1 FROM sync_v3_rows WHERE account_id=$1 AND table_name='returns' AND row_id='r-v3-over'`,
      [shopId]), null);

    assert.equal((await push3(staff, 's-1', [ret3('r-v3-a', 's-v3', 6000)])).body.results[0].status, 'applied');
    //  ۶۰۰۰ برگشته ⇒ ۵۰۰۰ ِ دیگر (۱۱۰۰۰) بیش از فاکتور است، ۴۰۰۰ (۱۰۰۰۰) نه
    assert.equal((await push3(staff, 's-1', [ret3('r-v3-b', 's-v3', 5000)])).body.results[0].reason, 'return_exceeds_sale');
    assert.equal((await push3(staff, 's-1', [ret3('r-v3-c', 's-v3', 4000)])).body.results[0].status, 'applied');
    assert.equal((await push3(staff, 's-1', [ret3('r-v3-d', 's-v3', 100)])).body.results[0].reason, 'return_exceeds_sale');
  } finally { await flag(false); }
});

test('پرچم روشن: نیم افغانی برای هر قلمِ فاکتور (گردِ قلم‌ها)، نه بیشتر', async () => {
  await flag(true);
  try {
    await push3(owner, 'o-1', [sale3('s-tol', 1001), item3('si-tol-1', 's-tol', 501), item3('si-tol-2', 's-tol', 500)]);
    //  دو قلم ⇒ تا ۱۰۰۱ + ۱۰۰
    assert.equal((await push3(staff, 's-1', [ret3('r-tol-a', 's-tol', 551), ret3('r-tol-b', 's-tol', 550)])).body.results
      .map((x) => x.status).join(), 'applied,applied');
    assert.equal((await push3(staff, 's-1', [ret3('r-tol-c', 's-tol', 1)])).body.results[0].reason, 'return_exceeds_sale');
  } finally { await flag(false); }
});

test('پرچم روشن، Sync v1: فاکتورِ ثبت‌شده و فاکتورِ همین دسته؛ همراهِ برگشتِ ردشده هم برمی‌گردد', async () => {
  await flag(true);
  try {
    const t0 = Date.now();
    const s1 = await push1(owner, 'o-v1', [v1op('sales', 's-v1', { total: 100, discount: 0, finalTotal: 100, status: 'completed' }, t0),
      v1op('saleItems', 'si-v1', { saleId: 's-v1', quantity: 1, totalPrice: 100 }, t0)]);
    assert.ok(s1.body.results.every((x) => x.status === 'applied'), JSON.stringify(s1.body));

    const t1 = Date.now() + 1;
    const over = v1op('returns', 'r-v1-over', { saleId: 's-v1', amount: 150, createdAt: t1 }, t1);
    const move = v1op('stockMovements', 'sm-v1-over', { refId: 'r-v1-over', type: 'return', quantity: 1 }, t1);
    const r = await push1(staff, 's-v1', [over, move]);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(r.body.results.map((x) => x.status), ['rejected', 'rejected']);
    assert.equal(r.body.results[0].reason, 'return_exceeds_sale');
    assert.equal(r.body.results[1].reason, 'tx_rejected');
    assert.equal(await one('SELECT 1 FROM oplog WHERE op_id=$1', [over.op_id]), null);
    assert.equal((await boxOf(over.op_id)).detail.path, 'v1');

    const t2 = Date.now() + 2;
    assert.equal((await push1(staff, 's-v1', [v1op('returns', 'r-v1-ok', { saleId: 's-v1', amount: 60, createdAt: t2 }, t2)]))
      .body.results[0].status, 'applied');

    //  فاکتور و برگشتش در همین دسته (فروشِ آفلاینِ خودِ شاگرد) — فاکتور هنوز روی سرور نیست
    const t3 = Date.now() + 3;
    const same = await push1(staff, 's-v1', [
      v1op('sales', 's-v1-b', { total: 50, discount: 0, finalTotal: 50, status: 'completed' }, t3),
      v1op('saleItems', 'si-v1-b', { saleId: 's-v1-b', quantity: 1, totalPrice: 50 }, t3),
      v1op('returns', 'r-v1-b', { saleId: 's-v1-b', amount: 80, createdAt: t3 + 1 }, t3 + 1),
    ]);
    assert.deepEqual(same.body.results.map((x) => x.status), ['applied', 'applied', 'rejected'], JSON.stringify(same.body));
    assert.equal(same.body.results[2].reason, 'return_exceeds_sale');
  } finally { await flag(false); }
});

test('پرچم روشن: فاکتوری که سرور ندارد و صاحبِ دکان سنجیده نمی‌شوند', async () => {
  await flag(true);
  try {
    assert.equal((await push3(staff, 's-1', [ret3('r-nosale', 's-unknown', 50000)])).body.results[0].status, 'applied');
    await push3(owner, 'o-1', [sale3('s-own', 1000)]);
    assert.equal((await push3(owner, 'o-1', [ret3('r-own', 's-own', 5000)])).body.results[0].status, 'applied');
  } finally { await flag(false); }
});
