'use strict';
/**
 * SEC-03 (§۶.۱۳) — «دستگاهِ زنجیره‌دار» از نشست است، نه از بدنه (`lib/finalized-sales.js`، پرچمِ `shop.doc_chain`).
 *
 * شناسهٔ دستگاه در بدنهٔ push از کاربر است و بی پرچمِ `sync.device_bind` فقط ثبت می‌شود. پیش‌تر نگهبانِ S89 فقط همان
 * شناسهٔ ادعاشده را می‌پرسید ⇒ نشستِ دستگاهِ زنجیره‌دار با `device_id` ِ تازه (یا راهِ قدیمِ `/api/sync`) به `log`
 * می‌رسید و مبلغِ فاکتورِ ثبت‌شده سرِ جا عوض می‌شد (فقط یک رویداد). حالا:
 *   ۱) v1: نشستِ زنجیره‌دار + شناسهٔ ساختگی ⇒ `rejected` (`sale_finalized_edit_forbidden`)؛ لغو آزاد می‌ماند.
 *   ۲) راهِ قدیم: نشستِ زنجیره‌دار، یا شناسهٔ ادعاشدهٔ زنجیره‌دار ⇒ ۴۰۹ و هیچ نوشتنی (راهِ `legacy`، `enforced: true`).
 *   ۳) بی تغییر: نشستِ بی زنجیره با شناسهٔ بی زنجیره ⇒ همان امروز + رویداد (F0-02)؛ پرچمِ خاموش ⇒ هیچ پرسشی.
 *   ۴) جداییِ دکان‌ها: زنجیرهٔ همان دستگاه در دکانِ دیگر `enforce` نمی‌سازد.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

const REASON = 'sale_finalized_edit_forbidden';
const SPOOF = 'dev-bound-spoof';

let owner;
let shopId;
let bound;       //  دستگاهِ نشستِ صاحب (زنجیره‌دار)
let clerk;       //  نشستِ دوم: دستگاهِ بی زنجیره
let seq = 0;

const opId = () => `01JBND${String(Date.now()).slice(-8)}${String(++seq).padStart(5, '0')}`;
const op = (table, rowId, type, fields, ts = Date.now()) => ({ op_id: opId(), ts, table, row_id: rowId, type, fields });
const push = (dev, ops, token) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': dev }, body: { device_id: dev, schema_version: SCHEMA, caps: ['a00'], ops },
});
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const v1Row = async (id) => (await one(
  "SELECT data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='sales' AND row_id=$2",
  [shopId, id]
)).data;
const flag = (on, shop = shopId) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain',$2,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [shop, on ? 'true' : 'false']
);
const events = async (sale) => (await query(
  "SELECT detail FROM audit_logs WHERE action='security.doc_chain' AND shop_id=$1 AND target_id=$2 ORDER BY created_at, id", [shopId, sale]
)).rows.map((r) => (typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail)).filter((e) => e.problem === 'finalized_edit');
const devicesOf = async (userId) => (await query('SELECT device_uid FROM devices WHERE user_id=$1', [userId])).rows.map((r) => r.device_uid);

function sale(id, extra = {}) {
  return {
    id, invoiceNumber: 11, series: 'B9', total: 1000, discountType: 'amount', discountValue: 0, discount: 0, finalTotal: 1000,
    paymentMethod: 'cash', paidAmount: 1000, remaining: 0, status: 'completed', debtGiven: 0, debtSettled: 0,
    createdAt: 5, date: '2026-11-01', cancelReason: '', seller: 'صاحب', channel: 'pos', ...extra,
  };
}
const legacyPush = (token, deviceId, id, data) => h.api('POST', '/api/sync/push', {
  token, headers: { 'X-App': 'shop' },
  body: { deviceId, changes: [{ collection: 'sales', id, updatedAt: Date.now() + (++seq), data }] },
});
const legacyRow = async (id) => (await one('SELECT data FROM sales WHERE shop_id=$1 AND id=$2', [shopId, id]))?.data || {};

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ نشستِ زنجیره‌دار');
  shopId = (await h.post('/api/shop', { name: 'دکانِ نشست' }, { token: owner.accessToken })).body.shop.id;
  const first = await devicesOf(owner.user.id);
  assert.equal(first.length, 1, 'نشستِ صاحب یک دستگاه دارد');
  bound = first[0];
  //  دومین ورودِ همان صاحب از دستگاهی دیگر (بی زنجیره)
  clerk = await h.signIn(owner);
  assert.equal(new Set(await devicesOf(owner.user.id)).size, 2, 'نشستِ دوم دستگاهِ جدا دارد');
  //  دستگاهِ نشستِ نخست زنجیره دارد (همان ردیفی که `doc-chain.report` می‌سازد)
  await query(
    `INSERT INTO device_chain_heads (shop_id, series, epoch, device_id, reported_at, created_at) VALUES ($1,'B9',1,$2,1,1)`,
    [shopId, bound]
  );
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش: نشستِ زنجیره‌دار با شناسهٔ ساختگی همان امروز (هیچ پرسش و رویدادی)', async () => {
  let r = await push(SPOOF, [op('sales', 'b0', 'insert', sale('b0'))], owner.accessToken);
  assert.deepEqual(statuses(r), ['applied'], JSON.stringify(r.body));
  r = await push(SPOOF, [op('sales', 'b0', 'update', { total: 1, finalTotal: 1 })], owner.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await v1Row('b0')).finalTotal, 1);
  assert.deepEqual(await events('b0'), []);
});

test('v1: نشستِ زنجیره‌دار با `device_id` ِ ساختگی ⇒ ویرایشِ مالی رد، لغو آزاد', async () => {
  await flag(true);
  let r = await push(SPOOF, [op('sales', 'b1', 'insert', sale('b1'))], owner.accessToken);
  assert.deepEqual(statuses(r), ['applied'], 'ثبتِ تازه آزاد است');
  r = await push(SPOOF, [op('sales', 'b1', 'update', { total: 10, finalTotal: 10, paidAmount: 10 })], owner.accessToken);
  assert.deepEqual(statuses(r), [`rejected:${REASON}`], 'شناسهٔ ساختگی نگهبان را دور نمی‌زند');
  r = await push(SPOOF, [op('sales', 'b1', 'delete', null)], owner.accessToken);
  assert.deepEqual(statuses(r), [`rejected:${REASON}`]);
  assert.equal((await v1Row('b1')).finalTotal, 1000);
  const ev = await events('b1');
  assert.deepEqual(ev.map((e) => [e.path, e.enforced, e.deviceId]), [['v1', true, SPOOF], ['v1', true, SPOOF]]);
  //  سندِ معکوس (لغو) همچنان آزاد
  r = await push(SPOOF, [op('sales', 'b1', 'update', { status: 'cancelled', cancelReason: 'اشتباه' })], owner.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await v1Row('b1')).status, 'cancelled');
});

test('v1، بی تغییر: نشستِ بی زنجیره با شناسهٔ بی زنجیره همان امروز + رویداد (F0-02)', async () => {
  let r = await push('dev-bound-old', [op('sales', 'b2', 'insert', sale('b2'))], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push('dev-bound-old', [op('sales', 'b2', 'update', { total: 7, finalTotal: 7 })], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await v1Row('b2')).finalTotal, 7);
  assert.deepEqual((await events('b2')).map((e) => [e.path, e.enforced]), [['v1', false]]);
  //  همان نشستِ بی زنجیره با شناسهٔ ادعاشدهٔ زنجیره‌دار ⇒ همان رد ِ پیشین
  r = await push(bound, [op('sales', 'b2', 'update', { total: 8, finalTotal: 8 })], clerk.accessToken);
  assert.deepEqual(statuses(r), [`rejected:${REASON}`]);
  assert.equal((await v1Row('b2')).finalTotal, 7);
});

test('راهِ قدیم: نشستِ زنجیره‌دار یا شناسهٔ زنجیره‌دار ⇒ ۴۰۹ و هیچ نوشتنی؛ بی زنجیره همان امروز', async () => {
  //  ثبتِ تازه از راهِ قدیم آزاد است (هیچ ردیفی نبود)
  let r = await legacyPush(owner.accessToken, 'old-phone', 'L9', sale('L9'));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.applied, 1);

  //  نشستِ زنجیره‌دار، شناسهٔ دیگری در بدنه
  r = await legacyPush(owner.accessToken, 'old-phone', 'L9', sale('L9', { total: 1, finalTotal: 1 }));
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, REASON);
  assert.equal((await legacyRow('L9')).finalTotal, 1000, 'هیچ بخشی ننشست');

  //  نشستِ بی زنجیره، ولی شناسهٔ ادعاشده همان دستگاهِ زنجیره‌دار
  r = await legacyPush(clerk.accessToken, bound, 'L9', sale('L9', { total: 2, finalTotal: 2 }));
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal((await legacyRow('L9')).finalTotal, 1000);

  //  لغو از راهِ قدیم با نشستِ زنجیره‌دار آزاد
  r = await legacyPush(owner.accessToken, 'old-phone', 'L9', sale('L9', { status: 'cancelled', cancelReason: 'اشتباه' }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await legacyRow('L9')).status, 'cancelled');

  //  بی زنجیره (نشست و شناسه) ⇒ همان امروز + رویداد
  r = await legacyPush(clerk.accessToken, 'old-web', 'L9', sale('L9', { status: 'cancelled', total: 3, finalTotal: 3 }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await legacyRow('L9')).finalTotal, 3);

  assert.deepEqual((await events('L9')).map((e) => [e.path, e.enforced]),
    [['legacy', true], ['legacy', true], ['legacy', false]]);
});

test('جداییِ دکان‌ها: زنجیرهٔ همان دستگاه در دکانِ دیگر `enforce` نمی‌سازد', async () => {
  const other = await h.newUser('صاحبِ دکانِ دیگرِ نشست');
  const otherShop = (await h.post('/api/shop', { name: 'دکانِ دیگرِ نشست' }, { token: other.accessToken })).body.shop.id;
  await flag(true, otherShop);
  let r = await push(bound, [op('sales', 'b1', 'insert', sale('b1'))], other.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(bound, [op('sales', 'b1', 'update', { total: 4, finalTotal: 4 })], other.accessToken);
  assert.deepEqual(statuses(r), ['applied'], 'زنجیرهٔ دکانِ نخست در این دکان نیست');
  const row = await one(
    "SELECT data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='sales' AND row_id='b1'", [otherShop]
  );
  assert.equal(row.data.finalTotal, 4);
  assert.equal((await v1Row('b1')).finalTotal, 1000, 'دکانِ نخست دست نخورد');
});
