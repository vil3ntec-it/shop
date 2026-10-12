'use strict';
/**
 * همگام‌سازیِ چنددستگاهی — بندِ ۳۲ِ پرامپتِ ممیزی (۱۴۰۵/۰۷/۲۰).
 *
 * آنچه `sync-v1.test.js` نمی‌سنجد و این‌جا سنجیده می‌شود:
 *   • دو دکان: opهای یکی هرگز در pull یا snapshotِ دیگری نمی‌آید، حتی با row_idِ یکسان
 *   • pushهای هم‌زمان از دو دستگاه ⇒ دستگاهِ سوم همه را می‌گیرد، هر کدام دقیقاً یک بار
 *     (نگهبانِ قفلِ هر حساب در push و head پیش از خواندن در pull)
 *   • pull صفحه‌به‌صفحه ⇒ cursor هیچ opی را جا نمی‌اندازد و دوبار نمی‌دهد
 *   • `$inc` از دو دستگاه روی یک فیلد ⇒ جمعِ هر دو، نه آخری
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const syncV1 = require('../src/lib/sync-v1');

let seq = 0;
const opId = () => `01JI${String(Date.now()).slice(-9)}${String(++seq).padStart(5, '0')}`;

const push = (token, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const pull = (token, device, since = 0, limit) => h.api('GET',
  `/api/sync/v1/pull?device_id=${device}&since=${since}${limit ? `&limit=${limit}` : ''}`,
  { token, headers: { 'X-App': 'shop' } });

async function ownerWithShop(label) {
  const u = await h.newUser(`صاحب-${label}`, 'shop');
  await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکان ${label}` } });
  return u;
}

/** همهٔ opهای بعد از `since`، صفحه‌به‌صفحه — همان کاری که برنامه می‌کند */
async function pullAll(token, device, limit) {
  let since = 0;
  const all = [];
  for (let guard = 0; guard < 200; guard++) {
    const r = await pull(token, device, since, limit);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    all.push(...r.body.ops);
    since = r.body.cursor;
    if (!r.body.has_more) break;
  }
  return { ops: all, cursor: since };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.after(async () => { await h.stop(); });

test('دو دکان: opهای یکی در pull و snapshotِ دیگری نمی‌آید، حتی با row_idِ یکسان', async () => {
  const a = await ownerWithShop('جدا-۱');
  const b = await ownerWithShop('جدا-۲');
  await push(a.accessToken, 'iso-a', [{ op_id: opId(), ts: Date.now(), table: 'products', row_id: 'same', type: 'insert', fields: { name: 'مالِ الف' } }]);
  await push(b.accessToken, 'iso-b', [{ op_id: opId(), ts: Date.now(), table: 'products', row_id: 'same', type: 'insert', fields: { name: 'مالِ ب' } }]);

  const seenByB = await pullAll(b.accessToken, 'iso-b2');
  assert.equal(seenByB.ops.length, 1);
  assert.equal(seenByB.ops[0].fields.name, 'مالِ ب');

  const snapA = await h.api('GET', '/api/sync/v1/snapshot', { token: a.accessToken, headers: { 'X-App': 'shop' } });
  const rows = (snapA.body.tables && snapA.body.tables.products) || [];
  assert.equal(rows.length, 1);
  assert.equal(rows[0].data.name, 'مالِ الف');
});

test('pushهای هم‌زمان از دو دستگاه ⇒ دستگاهِ سوم همه را دقیقاً یک بار می‌گیرد', async () => {
  const u = await ownerWithShop('هم‌زمان');
  const batch = (dev, n) => Array.from({ length: n }, (_, i) => ({
    op_id: opId(), ts: Date.now(), table: 'sales', row_id: `${dev}-s${i}`, type: 'insert', fields: { total: i + 1 },
  }));
  const results = await Promise.all([
    push(u.accessToken, 'cc-a', batch('a', 30)),
    push(u.accessToken, 'cc-b', batch('b', 30)),
    push(u.accessToken, 'cc-a', batch('a2', 20)),
    push(u.accessToken, 'cc-b', batch('b2', 20)),
  ]);
  results.forEach(r => assert.equal(r.status, 200, JSON.stringify(r.body)));

  const got = await pullAll(u.accessToken, 'cc-c');
  const ids = got.ops.map(o => o.row_id);
  assert.equal(ids.length, 100, 'هیچ opی جا نیفتد');
  assert.equal(new Set(ids).size, 100, 'هیچ opی دو بار نیاید');
  //  شماره‌ها پیوسته و رو به بالا — همان چیزی که cursor به آن تکیه دارد
  const seqs = got.ops.map(o => o.server_seq);
  for (let i = 1; i < seqs.length; i++) assert.ok(seqs[i] > seqs[i - 1]);
});

test('pull صفحه‌به‌صفحه ⇒ cursor هیچ opی را جا نمی‌اندازد', async () => {
  const u = await ownerWithShop('صفحه');
  const ops = Array.from({ length: 23 }, (_, i) => ({
    op_id: opId(), ts: Date.now(), table: 'debtors', row_id: `d${i}`, type: 'insert', fields: { name: `مشتری ${i}` },
  }));
  assert.equal((await push(u.accessToken, 'pg-a', ops)).status, 200);
  const got = await pullAll(u.accessToken, 'pg-b', 5);
  assert.equal(got.ops.length, 23);
  assert.deepEqual(got.ops.map(o => o.row_id), ops.map(o => o.row_id));
  //  و بعد از آن، چیزی برای گرفتن نمانده
  const again = await pull(u.accessToken, 'pg-b', got.cursor);
  assert.equal(again.body.ops.length, 0);
});

test('`$inc` از دو دستگاه روی یک فیلد ⇒ جمعِ هر دو، نه آخری', async () => {
  const u = await ownerWithShop('جمع');
  await push(u.accessToken, 'inc-a', [{ op_id: opId(), ts: Date.now(), table: 'sales', row_id: 's1', type: 'insert', fields: { paidAmount: 100 } }]);
  await Promise.all([
    push(u.accessToken, 'inc-a', [{ op_id: opId(), ts: Date.now(), table: 'sales', row_id: 's1', type: 'update', fields: { paidAmount: { $inc: 50 } } }]),
    push(u.accessToken, 'inc-b', [{ op_id: opId(), ts: Date.now(), table: 'sales', row_id: 's1', type: 'update', fields: { paidAmount: { $inc: 30 } } }]),
  ]);
  const snap = await h.api('GET', '/api/sync/v1/snapshot', { token: u.accessToken, headers: { 'X-App': 'shop' } });
  const row = snap.body.tables.sales.find(r => r.id === 's1');
  assert.equal(row.data.paidAmount, 180);
});
