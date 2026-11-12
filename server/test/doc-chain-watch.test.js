'use strict';
/**
 * ۲-۲۳ ِ صاحب‌کار (R7 ب، R8 ب؛ `lib/finalized-sales.js`، پرچمِ `shop.doc_chain`) — دو راهِ بازِ نگهبانِ سندهای ثبت‌شده
 * بسته نمی‌شوند ولی هر بار یک ردیف در «سابقهٔ عملیاتِ» دکان (`GET /api/shop/audit`) می‌گذارند:
 *
 *   R7  برداشتنِ ادغامِ مشتری (`mergedInto` ِ پُر ⇒ خالی یا مقصدِ دیگر) — دستهٔ اول ادغام و جابه‌جاییِ قرض، دستهٔ دوم فقط
 *       برداشتنِ ادغام ⇒ پیش‌تر هیچ ردی نمی‌ماند (دستهٔ بی سند اصلاً نگهبان نمی‌گرفت). `merge_undone`.
 *   R8  بردنِ قرض/رسیدِ ثبت‌شده به شیفتِ دیگر (`shiftId`) ⇒ `shift_moved`.
 *
 * هر دو آزاد می‌مانند (رد نمی‌شوند، ترمیمِ `CustomerMerge.sweep` و اصلاحِ درستِ شیفت نمی‌شکنند)؛ ردیف فقط وقتی که نوشتن
 * واقعاً نشست (دستهٔ برگشتهٔ راهِ قدیم و op ِ ردشده ردیف نمی‌گذارند). پرچمِ خاموش ⇒ هیچ.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

let owner;
let shopId;
let bound;
let clerk;
let seq = 0;

const opId = () => `01JWCH${String(Date.now()).slice(-8)}${String(++seq).padStart(5, '0')}`;
const op = (table, rowId, type, fields, ts = Date.now() + seq) => ({ op_id: opId(), ts, table, row_id: rowId, type, fields });
const push = (dev, ops, token = owner.accessToken) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': dev }, body: { device_id: dev, schema_version: SCHEMA, caps: ['a00'], ops },
});
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const legacy = (token, deviceId, changes) => h.api('POST', '/api/sync/push', {
  token, headers: { 'X-App': 'shop' },
  body: { deviceId, changes: changes.map((c) => ({ updatedAt: 2_000_000 + (++seq), ...c })) },
});
const flag = (on) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain',$2,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [shopId, on ? 'true' : 'false']
);
/** ردیف‌های «سابقهٔ عملیات» ِ همان دکان از درِ صاحب، برای [problem] */
const audit = async (problem) => {
  const r = await h.api('GET', '/api/shop/audit?limit=500', { token: owner.accessToken, headers: { 'X-App': 'shop' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.entries
    .map((e) => ({ type: e.target_type, id: e.target_id, ...(typeof e.detail === 'string' ? JSON.parse(e.detail) : e.detail) }))
    .filter((e) => e.problem === problem)
    .reverse();
};
const tx = (id, extra = {}) => ({ id, debtorId: 'deb-a', type: 'receive', amount: 500, date: '2026-11-01', notes: '', createdAt: 5, ...extra });
const devicesOf = async (userId) => (await query('SELECT device_uid FROM devices WHERE user_id=$1', [userId])).rows.map((r) => r.device_uid);

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ سابقهٔ ادغام و شیفت');
  shopId = (await h.post('/api/shop', { name: 'دکانِ ادغام و شیفت' }, { token: owner.accessToken })).body.shop.id;
  bound = (await devicesOf(owner.user.id))[0];
  clerk = await h.signIn(owner);
  await query(
    `INSERT INTO device_chain_heads (shop_id, series, epoch, device_id, reported_at, created_at) VALUES ($1,'D1',1,$2,1,1)`,
    [shopId, bound]
  );
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش: برداشتنِ ادغام و بردنِ رسید به شیفتِ دیگر همان امروز، هیچ ردیفی', async () => {
  let r = await push(bound, [
    op('debtors', 'off-a', 'insert', { id: 'off-a', name: 'الف', mergedInto: 'off-b' }),
    op('transactions', 'off-t', 'insert', tx('off-t', { shiftId: 'sh-1' })),
  ]);
  assert.deepEqual(statuses(r), ['applied', 'applied'], JSON.stringify(r.body));
  r = await push(bound, [op('debtors', 'off-a', 'update', { mergedInto: null }), op('transactions', 'off-t', 'update', { shiftId: 'sh-2' })]);
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  assert.deepEqual(await audit('merge_undone'), []);
  assert.deepEqual(await audit('shift_moved'), []);
});

test('R7 v1: ادغام + جابه‌جاییِ قرض در یک دسته، برداشتنِ ادغام در دستهٔ دیگر ⇒ آزاد، با یک ردیف در سابقه', async () => {
  await flag(true);
  let r = await push(bound, [op('transactions', 'r7-t', 'insert', tx('r7-t'))]);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(bound, [
    op('debtors', 'deb-a', 'insert', { id: 'deb-a', name: 'الف' }),
    op('debtors', 'deb-a', 'update', { mergedInto: 'deb-b' }),
    op('transactions', 'r7-t', 'update', { debtorId: 'deb-b' }),
  ]);
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied'], 'ادغام همان CRM-10');
  assert.deepEqual(await audit('merge_undone'), [], 'خودِ ادغام ردیفِ «برداشتن» نیست');
  //  دستهٔ دوم فقط مشتری دارد (پیش‌تر اصلاً نگهبان نمی‌گرفت)
  r = await push(bound, [op('debtors', 'deb-a', 'update', { mergedInto: null })]);
  assert.deepEqual(statuses(r), ['applied'], 'ب: رد نمی‌شود');
  const rows = await audit('merge_undone');
  assert.equal(rows.length, 1, JSON.stringify(rows));
  assert.deepEqual([rows[0].type, rows[0].id, rows[0].from, rows[0].to, rows[0].path, rows[0].enforced], ['debtors', 'deb-a', 'deb-b', '', 'v1', true]);
  assert.ok(rows[0].text, 'متنِ سابقه برای صاحب');
  //  نوشتنِ دوبارهٔ «بی ادغام» و نامِ مشتری ⇒ ردیفِ تازه نه
  r = await push(bound, [op('debtors', 'deb-a', 'update', { mergedInto: '', name: 'الف۲' })]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await audit('merge_undone')).length, 1);
  //  ادغام به مقصدِ دیگر (بی برداشتن) هم برداشتنِ ادغامِ پیشین است
  r = await push(bound, [op('debtors', 'deb-c', 'insert', { id: 'deb-c', name: 'ج', mergedInto: 'deb-b' })]);
  r = await push(bound, [op('debtors', 'deb-c', 'update', { mergedInto: 'deb-d' })]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.deepEqual((await audit('merge_undone')).map((e) => [e.id, e.from, e.to]), [['deb-a', 'deb-b', ''], ['deb-c', 'deb-b', 'deb-d']]);
});

test('R8 v1: بردنِ رسیدِ ثبت‌شده به شیفتِ دیگر ⇒ آزاد، با یک ردیف؛ همان شیفت یا فیلدِ دیگر ⇒ هیچ', async () => {
  let r = await push(bound, [op('transactions', 'r8-t', 'insert', tx('r8-t', { shiftId: 'sh-1' }))]);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(bound, [op('transactions', 'r8-t', 'update', { notes: 'یادداشت', shiftId: 'sh-1' })]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.deepEqual(await audit('shift_moved'), []);
  r = await push(bound, [op('transactions', 'r8-t', 'update', { shiftId: 'sh-2' })]);
  assert.deepEqual(statuses(r), ['applied'], 'ب: رد نمی‌شود');
  //  کشوی اصلی (`null`) هم شیفتِ دیگری است
  r = await push(bound, [op('transactions', 'r8-t', 'update', { shiftId: null })]);
  assert.deepEqual(statuses(r), ['applied']);
  const rows = await audit('shift_moved');
  assert.deepEqual(rows.map((e) => [e.type, e.id, e.from, e.to, e.path]), [
    ['transactions', 'r8-t', 'sh-1', 'sh-2', 'v1'], ['transactions', 'r8-t', 'sh-2', '', 'v1'],
  ]);
  //  همان op با مبلغِ تازه (رد) شیفت را هم نمی‌برد ⇒ ردیفی نمی‌ماند
  r = await push(bound, [op('transactions', 'r8-t', 'update', { amount: 1, shiftId: 'sh-9' })]);
  assert.deepEqual(statuses(r), ['rejected:doc_posted_edit_forbidden']);
  assert.equal((await audit('shift_moved')).length, 2);
});

test('راهِ قدیم: برداشتنِ ادغام و بردنِ رسید به شیفتِ دیگر ⇒ ۲۰۰ و ردیف؛ دستهٔ برگشته ⇒ هیچ', async () => {
  let r = await legacy(clerk.accessToken, 'old-web', [
    { collection: 'debtors', id: 'L-a', data: { id: 'L-a', name: 'الف', mergedInto: 'L-b' } },
    { collection: 'transactions', id: 'L-t', data: tx('L-t', { debtorId: 'L-b', shiftId: 'sh-1' }) },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  //  دسته‌ای که با ویرایشِ مالیِ قرضِ ثبت‌شده برمی‌گردد (نشستِ زنجیره‌دار ⇒ ۴۰۹) هیچ ردیفی نمی‌گذارد
  r = await legacy(owner.accessToken, 'old-phone', [
    { collection: 'debtors', id: 'L-a', data: { id: 'L-a', name: 'الف' } },
    { collection: 'transactions', id: 'L-t', data: tx('L-t', { debtorId: 'L-b', shiftId: 'sh-2', amount: 1 }) },
  ]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.ok(!(await audit('merge_undone')).some((e) => e.id === 'L-a'));
  assert.ok(!(await audit('shift_moved')).some((e) => e.id === 'L-t'));
  //  همان دو نوشتن بی ویرایشِ مالی: می‌نشیند و هر کدام یک ردیف
  r = await legacy(clerk.accessToken, 'old-web', [
    { collection: 'debtors', id: 'L-a', data: { id: 'L-a', name: 'الف' } },
    { collection: 'transactions', id: 'L-t', data: tx('L-t', { debtorId: 'L-b', shiftId: 'sh-2' }) },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual((await audit('merge_undone')).filter((e) => e.id === 'L-a').map((e) => [e.from, e.to, e.path, e.enforced]),
    [['L-b', '', 'legacy', false]]);
  assert.deepEqual((await audit('shift_moved')).filter((e) => e.id === 'L-t').map((e) => [e.from, e.to, e.path]), [['sh-1', 'sh-2', 'legacy']]);
  //  دستهٔ فقط‌مشتریِ راهِ قدیم هم (پیش‌تر بی نگهبان)
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'debtors', id: 'L-c', data: { id: 'L-c', name: 'ج', mergedInto: 'L-b' } }]);
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'debtors', id: 'L-c', data: { id: 'L-c', name: 'ج', mergedInto: 'L-d' } }]);
  assert.equal(r.status, 200);
  assert.deepEqual((await audit('merge_undone')).filter((e) => e.id === 'L-c').map((e) => [e.from, e.to]), [['L-b', 'L-d']]);
});
