'use strict';
/**
 * عاملِ e-newinstall — «برنامهٔ تازه (`com.vill3n.dailyshop`) کنارِ برنامهٔ قدیم (`ir.vil3ntec.tohid`)»: قرارهای سرور که
 * برنامهٔ تازه به آن‌ها تکیه دارد (هیچ کدِ سرور عوض نشده؛ فقط قفل):
 *
 *  ۱) حسابِ موجود روی نصبِ تازه (دستگاهِ تازه، نشستِ تازه) همان دکان را می‌گیرد: ورود دکان را می‌گوید و Snapshot همهٔ ردیف‌ها.
 *  ۲) حسابِ بی دکان: push/pull/snapshot ⇒ ۴۰۳ با کدِ ماشینیِ `no_shop` و پیامِ سادهٔ فارسی — برنامه همان کد را نگه می‌دارد و
 *     «دکان ندارید» می‌گوید (`SyncStateV1.lastErrorCode`، `SyncModel.fromCode`).
 *  ۳) صفِ نرفتهٔ برنامهٔ قدیم که برنامهٔ تازه از پشتیبان با **همان op_id** و شناسهٔ دستگاهِ خودش می‌فرستد پذیرفته می‌شود؛
 *     برنامهٔ قدیم که بعداً همان را با شناسهٔ خودش بفرستد ⇒ همه `duplicate`، هیچ ردیفِ oplog ِ تازه (`SyncV1Engine.rebuildFromServer`
 *     با `carry`).
 *  ۴) شناسهٔ نصب (`X-App-Package`/نامِ بسته) هیچ‌جا شرطِ همگام‌سازی نیست: هر دو بسته با همان نشست همان دکان را می‌نویسند.
 *
 *   cd server && TEST_DATABASE_URL=pglite:memory node --test test/new-install-contract.test.js
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { many } = require('../src/db');

let seq = 0;
const opId = () => `01JNEWINST${String(Date.now()).slice(-8)}${String(++seq).padStart(6, '0')}`;
const op = (o) => ({ op_id: opId(), ts: Date.now(), table: 'products', row_id: `p-${seq}`, type: 'insert', fields: { name: 'کالا' }, ...o });

const push = (token, device, ops, headers = {}) => h.api('POST', '/api/sync/v1/push', {
  token, body: { device_id: device, schema_version: 3, queued: ops.length, ops }, headers: { 'X-App': 'shop', 'X-Device': device, ...headers },
});

async function login(user, deviceId, pkg) {
  const r = await h.api('POST', '/api/auth/login', {
    body: { identifier: user.email, password: user.password, app: 'shop', device: { deviceId, name: pkg, platform: 'android' } },
    headers: { 'X-App': 'shop', 'X-App-Package': pkg },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
}

let owner;
let shopId;

test.before(async () => {
  await h.start();
  owner = await h.newUser('صاحبِ دکان', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ قدیمی' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  assert.ok(shopId);
});
test.after(async () => { await h.stop(); });

test('حسابِ موجود روی نصبِ تازه همان دکان و همهٔ ردیف‌ها را می‌گیرد', async () => {
  const old = await login(owner, 'old-phone-1', 'ir.vil3ntec.tohid');
  const ops = [op({ row_id: 'p-old-1' }), op({ row_id: 'p-old-2' }), op({ table: 'debtors', row_id: 'd-old-1', fields: { name: 'احمد' } })];
  const r = await push(old.accessToken, 'old-phone-1', ops, { 'X-App-Package': 'ir.vil3ntec.tohid' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body.results));

  const fresh = await login(owner, 'new-phone-1', 'com.vill3n.dailyshop');
  assert.equal(fresh.shop?.id, shopId, 'ورودِ نصبِ تازه دکان را می‌گوید');
  const snap = await h.api('GET', '/api/sync/v1/snapshot', { token: fresh.accessToken, headers: { 'X-App': 'shop', 'X-App-Package': 'com.vill3n.dailyshop' } });
  assert.equal(snap.status, 200, JSON.stringify(snap.body));
  const ids = Object.values(snap.body.tables || {}).flat().map((x) => x.row_id || x.id);
  for (const id of ['p-old-1', 'p-old-2', 'd-old-1']) assert.ok(ids.includes(id), `${id} در Snapshot ِ نصبِ تازه`);
  //  نوشتنِ نصبِ تازه هم پذیرفته (بسته شرط نیست)
  const w = await push(fresh.accessToken, 'new-phone-1', [op({ row_id: 'p-new-1' })], { 'X-App-Package': 'com.vill3n.dailyshop' });
  assert.equal(w.body.results[0].status, 'applied');
});

test('حسابِ بی دکان: ۴۰۳ با کدِ no_shop و پیامِ سادهٔ فارسی (نه ۴۰۱، نه متنِ فنی)', async () => {
  const lone = await h.newUser('حسابِ تازه', 'shop');
  const s = await login(lone, 'new-phone-2', 'com.vill3n.dailyshop');
  assert.equal(s.shop, null);
  for (const [method, path, body] of [
    ['POST', '/api/sync/v1/push', { device_id: 'new-phone-2', schema_version: 3, ops: [op({})] }],
    ['GET', '/api/sync/v1/pull?device_id=new-phone-2&since=0', null],
    ['GET', '/api/sync/v1/snapshot', null],
  ]) {
    const r = await h.api(method, path, { token: s.accessToken, body, headers: { 'X-App': 'shop' } });
    assert.equal(r.status, 403, `${path}: ${r.status}`);
    assert.equal(r.body?.error?.code, 'no_shop', JSON.stringify(r.body));
    assert.match(r.body.error.message, /دکانی ثبت نشده/);
    assert.doesNotMatch(r.body.error.message, /[A-Za-z0-9]/);
  }
});

test('صفِ نرفتهٔ برنامهٔ قدیم از راهِ پشتیبان: نصبِ تازه با همان op_id می‌فرستد، برنامهٔ قدیم بعداً ⇒ همه duplicate', async () => {
  const old = await login(owner, 'old-phone-3', 'ir.vil3ntec.tohid');
  const fresh = await login(owner, 'new-phone-3', 'com.vill3n.dailyshop');
  const pending = [
    op({ row_id: 'p-offline-1', fields: { name: 'کالای بی‌همگام' } }),
    op({ table: 'debtors', row_id: 'd-offline-1', fields: { name: 'مشتریِ بی‌همگام' } }),
  ];
  const carried = await push(fresh.accessToken, 'new-phone-3', pending);
  assert.deepEqual(carried.body.results.map((x) => x.status), ['applied', 'applied']);
  const count = async () => (await many(`SELECT op_id FROM oplog WHERE account_id=$1`, [String(shopId)])).length;
  const before = await count();
  const later = await push(old.accessToken, 'old-phone-3', pending);
  assert.equal(later.status, 200);
  assert.deepEqual(later.body.results.map((x) => x.status), ['duplicate', 'duplicate'], 'برنامهٔ قدیم: تکراری، نه دوباره');
  assert.equal(await count(), before, 'هیچ op ِ تازه‌ای در oplog');
  const rows = await many(`SELECT op_id FROM oplog WHERE account_id=$1 AND row_id=$2`, [String(shopId), 'p-offline-1']);
  assert.equal(rows.length, 1, 'ردیف یک بار');
});
