'use strict';
/**
 * گاوصندوقِ چندارزی (PAY-18، خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴) — فیلدِ اختیاریِ `currency` روی همان جدولِ `safeEntries`، از همان
 * opهای امروز (`src/lib/safe-ledger.js`؛ `sync-v1.js` ِ مشترک با پمپ دست نخورده):
 *   • v1: ردیفِ دالری می‌نشیند و دستگاهِ دیگر همان را در pull و snapshot می‌گیرد؛ ردیفِ افغانی هیچ `currency`ی نمی‌گیرد؛
 *   • ساختِ کهنه (بی `caps`، نسخهٔ قدیمیِ گوشی) که همان ردیف را بی این فیلد یا با `currency: null` دوباره بفرستد ارزِ نشسته
 *     را پاک نمی‌کند؛ عوض کردنِ ارزِ ردیفِ نشسته ویرایش است و رد می‌شود؛
 *   • کدِ نادرست و ارزِ جز افغانی روی «از دخل» ⇒ `safe_invalid`؛
 *   • v3: opِ بومی با ارز می‌نشیند و در pull و snapshot ِ v3 همان است.
 * ⚠️ دادهٔ این‌جا ساختگی است.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.SAFE_LOCK_SCOPE = 'db';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const migrations = require('../src/lib/sync-v1-migrations');

let shopId;
let owner;
let n = 0;
const oid = () => `01JSCUR${String(++n).padStart(19, '0')}`;
let tick = 0;
const at = () => Date.now() + (++tick);
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

/** ساختِ A00 به بعد (برنامهٔ امروز) */
const pushNew = (device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: owner.accessToken, body: { device_id: device, schema_version: migrations.FLAGGED.shop, caps: ['a00'], ops },
});
/** ساختِ کهنهٔ گوشی: بی `caps` و با نسخهٔ پیش از A00 — همان که کلیدِ ناشناخته را null می‌فرستاد */
const pushOld = (device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: owner.accessToken, headers: { 'X-App-Version': '3.2.410' },
  body: { device_id: device, schema_version: migrations.FLAGGED.shop, ops },
});
const push3 = (device, ops) => h.api('POST', '/api/sync/v3/push', {
  token: owner.accessToken, body: { device_id: device, protocol: 3, ops },
});
const op1 = (rowId, fields, type = 'insert') => ({ op_id: oid(), ts: Date.now(), table: 'safeEntries', row_id: rowId, type, fields });
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const rowOf = async (id) => (await one(
  `SELECT data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
      AND table_name='safeEntries' AND row_id=$2 AND deleted_at IS NULL`,
  [shopId, id]
))?.data;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ گاوصندوقِ چندارزی', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ ارزها' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  await require('../src/lib/live-config').set(shopId, 'sync.safe_entries', true);
  owner = await h.signIn(o, 'shop');
});
test.after(async () => { await h.stop(); });

const usd = () => ({ id: 'cur-u1', kind: 'in', amountMinor: 12550, createdAt: at(), reason: 'deposit', by: 'صاحب', currency: 'USD' });

test('v1: ردیفِ دالری می‌نشیند و دستگاهِ دیگر در pull و snapshot همان را می‌گیرد؛ ردیفِ افغانی بی ارز', async () => {
  const fields = usd();
  const afn = { id: 'cur-a1', kind: 'count', amountMinor: 50000, createdAt: at(), expectedMinor: 0 };
  const r = await pushNew('cur-dev-a', [op1('cur-u1', fields), op1('cur-a1', afn)]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  assert.equal((await rowOf('cur-u1')).currency, 'USD');
  assert.equal((await rowOf('cur-u1')).amountMinor, 12550, 'واحدِ کوچکِ همان ارز، بی تبدیل');
  assert.ok(!('currency' in (await rowOf('cur-a1'))), 'افغانی ارز نمی‌نویسد');

  const pulled = await h.api('GET', '/api/sync/v1/pull?device_id=cur-dev-b&since=0', { token: owner.accessToken });
  assert.equal(pulled.status, 200, JSON.stringify(pulled.body));
  const op = pulled.body.ops.find((o) => o.row_id === 'cur-u1');
  assert.ok(op, 'opِ دالری در pull');
  assert.equal(op.fields.currency, 'USD');
  const snap = await h.api('GET', '/api/sync/v1/snapshot', { token: owner.accessToken });
  assert.equal(snap.status, 200);
  const row = snap.body.tables.safeEntries.find((x) => x.id === 'cur-u1');
  assert.equal(row.data.currency, 'USD');
});

test('ساختِ کهنه همان ردیف را بی ارز یا با currency:null دوباره می‌فرستد ⇒ ارزِ نشسته می‌ماند', async () => {
  const { currency, ...old } = usd();
  assert.equal(currency, 'USD');
  //  بی فیلد: «بخشی از همان» ⇒ بی‌اثر
  const a = await pushOld('cur-old', [op1('cur-u1', { ...old, createdAt: (await rowOf('cur-u1')).createdAt })]);
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.deepEqual(statuses(a), ['applied']);
  assert.equal((await rowOf('cur-u1')).currency, 'USD');
  //  null ِ ساختِ پیش از A00 برای کلیدی که نمی‌شناسد کنار می‌رود (`newer-fields.js`) ⇒ باز همان
  const b = await pushOld('cur-old', [op1('cur-u1', { currency: null }, 'update')]);
  assert.equal(b.status, 200, JSON.stringify(b.body));
  assert.equal((await rowOf('cur-u1')).currency, 'USD');
  //  ساختِ امروز که null بفرستد ویرایش است ⇒ رد، ارز همان
  const c = await pushNew('cur-dev-a', [op1('cur-u1', { currency: null }, 'update')]);
  assert.deepEqual(statuses(c), ['rejected:safe_append_only']);
  assert.equal((await rowOf('cur-u1')).currency, 'USD');
});

test('عوض کردنِ ارزِ ردیفِ نشسته ویرایش است: درجِ دوباره با ارزِ دیگر رد و ردیف همان', async () => {
  const row = await rowOf('cur-u1');
  const r = await pushNew('cur-dev-a', [op1('cur-u1', { ...row, currency: 'EUR' })]);
  assert.deepEqual(statuses(r), ['rejected:safe_append_only']);
  const back = await pushNew('cur-dev-a', [op1('cur-a1', { ...(await rowOf('cur-a1')), currency: 'USD' })]);
  assert.deepEqual(statuses(back), ['rejected:safe_append_only'], 'ردیفِ افغانی دالری نمی‌شود');
  assert.equal((await rowOf('cur-u1')).currency, 'USD');
  assert.ok(!('currency' in (await rowOf('cur-a1'))));
});

test('کدِ نادرست، نوعِ نادرست و ارزِ جز افغانی روی «از دخل» ⇒ safe_invalid؛ افغانیِ صریح پذیرفته', async () => {
  const base = { kind: 'in', amountMinor: 100, createdAt: at(), reason: 'deposit' };
  const r = await pushNew('cur-dev-a', [
    op1('cur-x1', { id: 'cur-x1', ...base, currency: 'usd' }),
    op1('cur-x2', { id: 'cur-x2', ...base, currency: 840 }),
    op1('cur-x3', { id: 'cur-x3', kind: 'from_drawer', amountMinor: 100, createdAt: at(), shiftId: 'sh', auditId: 'a', currency: 'USD' }),
    op1('cur-x4', { id: 'cur-x4', ...base, currency: 'AFN' }),
  ]);
  assert.deepEqual(statuses(r), ['rejected:safe_invalid', 'rejected:safe_invalid', 'rejected:safe_invalid', 'applied']);
  assert.equal(await rowOf('cur-x1'), undefined);
  assert.equal((await rowOf('cur-x4')).currency, 'AFN');
});

test('v3: opِ بومی با ارز می‌نشیند و در pull و snapshot ِ v3 همان است؛ کدِ نادرست رد', async () => {
  const fields = { kind: 'to_bank', amountMinor: 9000, createdAt: at(), bagNo: 'K-9', currency: 'EUR' };
  const r = await push3('cur-v3-a', [{ op_id: oid(), hlc: H('cur-v3'), table: 'safeEntries', row_id: 'cur-e1', kind: 'insert', fields }]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body));
  const bad = await push3('cur-v3-a', [{ op_id: oid(), hlc: H('cur-v3'), table: 'safeEntries', row_id: 'cur-e2', kind: 'insert',
    fields: { ...fields, currency: 'eur' } }]);
  assert.equal(bad.body.results[0].reason, 'safe_invalid', JSON.stringify(bad.body));

  const pulled = await h.api('GET', '/api/sync/v3/pull?device_id=cur-v3-b&since=0', { token: owner.accessToken });
  assert.equal(pulled.status, 200, JSON.stringify(pulled.body));
  const op = pulled.body.ops.find((o) => o.row_id === 'cur-e1');
  assert.ok(op, 'opِ یورویی در pull ِ v3');
  assert.ok(JSON.stringify(op.effect).includes('"currency":"EUR"'), JSON.stringify(op));
  const snap = await h.api('GET', '/api/sync/v3/snapshot?device_id=cur-v3-b', { token: owner.accessToken });
  assert.equal(snap.status, 200, JSON.stringify(snap.body));
  assert.ok(JSON.stringify(snap.body.tables.safeEntries).includes('"currency":"EUR"'), JSON.stringify(snap.body.tables.safeEntries));
});
