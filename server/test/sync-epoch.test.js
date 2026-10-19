'use strict';
/**
 * §۹.۵ — «دورانِ» همگام‌سازی پس از بازیابی (`lib/sync-epoch.js`، `GET /api/shop/sync-epoch`،
 * `epoch` در pull ِ v3). سناریوی واقعیِ بازیابی (پشتیبانِ رمزدار ⇐ سرورِ تازه در فرایندِ
 * جدا ⇐ دستگاه دوباره می‌فرستد) در `test/e2e/ops.e2e.mjs` (E2E-OPS-14.replay) است.
 *
 *   • هر بالا آمدن دورانِ تازه؛ بالا آمدنِ عادی ⇒ پایه = سرِ دفتر (برای دستگاه هیچ کاری نمی‌ماند).
 *   • opِ پس از دوران شمارهٔ بالاتر از پایه دارد؛ «بازیابی» ⇒ پایه پایین‌تر از opهای گم‌شده
 *     و همان op_id دوباره «applied» می‌شود، نه «duplicate».
 *   • فقط دکان؛ خاموش‌کن ⇒ ۴۰۴ و pull ِ v3 بی دوران؛ `lib/sync-v1.js` دست نخورده.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, query } = require('../src/db');
const config = require('../src/config');
const syncV1 = require('../src/lib/sync-v1');
const epoch = require('../src/lib/sync-epoch');

const DEV = 'epoch-dev-a';
let owner;
let shopId;
let seq = 0;
const opId = () => `01JEP${String(Date.now()).slice(-8)}${String(++seq).padStart(6, '0')}`;
const v1op = (row, fields) => ({ op_id: opId(), ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });

const getEpoch = (token) => h.api('GET', '/api/shop/sync-epoch', { token, headers: { 'X-App': 'shop' } });
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, body: { device_id: DEV, schema_version: 1, ops }, headers: { 'X-App': 'shop', 'X-Device': DEV },
});

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دوران', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ دوران' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
});
test.after(async () => { await h.stop(); });

test('بالا آمدنِ سرور دوران می‌سازد؛ پاسخ: id، پایه و سرِ دفترِ همین حساب', async () => {
  const stored = await one("SELECT value FROM app_config WHERE key='sync.epoch'");
  assert.ok(stored, 'createApp دوران را ننوشت');
  const r = await getEpoch(owner.accessToken);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  const e = r.body.epoch;
  assert.match(e.id, /^ep_[a-z0-9]+$/);
  assert.equal(e.id, JSON.parse(stored.value).id);
  assert.equal(e.base, e.head, 'هنوز هیچ opی پس از بالا آمدن نیامده');
  assert.ok(e.at > 0);
});

test('op ِ پس از دوران بالای پایه است؛ بالا آمدنِ دوباره ⇒ دورانِ تازه با پایه = سرِ دفتر', async () => {
  const before = (await getEpoch(owner.accessToken)).body.epoch;
  const p = await push(owner.accessToken, [v1op('p-ep-1', { name: 'چای' }), v1op('p-ep-2', { name: 'قند' })]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const seqs = p.body.results.map((x) => x.server_seq);
  assert.ok(seqs.every((s) => s > before.base), `server_seq ${seqs} باید بالای پایهٔ ${before.base} باشد`);

  const mid = (await getEpoch(owner.accessToken)).body.epoch;
  assert.equal(mid.id, before.id, 'بی بالا آمدن، دوران همان است');
  assert.equal(mid.base, before.base);
  assert.equal(mid.head, Math.max(...seqs));

  await epoch.rotate('boot');
  const after = (await getEpoch(owner.accessToken)).body.epoch;
  assert.notEqual(after.id, before.id, 'هر بالا آمدن دورانِ تازه');
  assert.equal(after.base, mid.head, 'بالا آمدنِ عادی: پایه = سرِ دفتر ⇒ دستگاه چیزی دوباره نمی‌فرستد');
  assert.equal(after.head, mid.head);
});

test('«بازیابی»: opهای پس از پشتیبان از دفترِ سرور می‌روند ⇒ پایه زیرِ آن‌ها؛ همان op_id دوباره «applied»', async () => {
  const kept = await push(owner.accessToken, [v1op('p-ep-3', { name: 'برنج' })]);
  assert.equal(kept.status, 200);
  const backupHead = kept.body.results[0].server_seq;
  const late = [v1op('p-ep-4', { name: 'روغن' }), v1op('p-ep-5', { name: 'آرد' })];
  const lp = await push(owner.accessToken, late);
  assert.deepEqual(lp.body.results.map((x) => x.status), ['applied', 'applied']);
  const lateSeqs = lp.body.results.map((x) => x.server_seq);

  //  دیتابیسِ «بازیابی‌شده» همانی است که در لحظهٔ پشتیبان بود: opهای پس از آن نیستند
  //  (بازیابیِ واقعی با فایلِ پشتیبان و فرایندِ جدا: E2E-OPS-14.replay)
  await query("DELETE FROM oplog WHERE app='shop' AND account_id=$1 AND server_seq > $2", [shopId, backupHead]);
  await query("DELETE FROM sync_rows WHERE app='shop' AND account_id=$1 AND row_id = ANY($2)", [shopId, ['p-ep-4', 'p-ep-5']]);
  await epoch.rotate('boot');

  const e = (await getEpoch(owner.accessToken)).body.epoch;
  assert.equal(e.base, backupHead);
  assert.ok(lateSeqs.every((s) => s > e.base), 'opهای گم‌شده بالای پایه‌اند ⇒ دستگاه همان‌ها را دوباره می‌فرستد');

  //  دستگاه همان opها را با همان op_id دوباره می‌فرستد (`RestoreReplay`)
  const again = await push(owner.accessToken, late);
  assert.deepEqual(again.body.results.map((x) => x.status), ['applied', 'applied'], 'سرورِ بازیابی‌شده آن‌ها را ندارد');
  const twice = await push(owner.accessToken, late);
  assert.deepEqual(twice.body.results.map((x) => x.status), ['duplicate', 'duplicate'], 'تکرار بی‌خطر است');
  const row = await one("SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND row_id='p-ep-4'", [shopId]);
  assert.equal((typeof row.data === 'string' ? JSON.parse(row.data) : row.data).name, 'روغن');
});

test('v3: pull کنارِ opها دورانِ v3 را می‌دهد (پایه و سرِ دفترِ sync_v3_ops)', async () => {
  const r = await h.api('GET', `/api/sync/v3/pull?device_id=${DEV}&since=0`, { token: owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const e = r.body.epoch;
  assert.ok(e && typeof e.id === 'string' && e.id.startsWith('ep_'), JSON.stringify(e));
  assert.equal(e.id, (await getEpoch(owner.accessToken)).body.epoch.id, 'یک دوران برای هر دو');
  assert.equal(typeof e.base, 'number');
  assert.equal(typeof e.head, 'number');
  assert.ok(e.base <= e.head);
});

test('خاموش‌کن (SYNC_EPOCH_ENABLED=false) ⇒ ۴۰۴ و pull ِ v3 بی دوران — همان رفتارِ پیشین', async () => {
  config.syncEpoch.enabled = false;
  try {
    const r = await getEpoch(owner.accessToken);
    assert.equal(r.status, 404);
    const p = await h.api('GET', `/api/sync/v3/pull?device_id=${DEV}&since=0`, { token: owner.accessToken });
    assert.equal(p.status, 200);
    assert.equal(p.body.epoch, undefined);
  } finally {
    config.syncEpoch.enabled = true;
  }
});

test('بی توکن ⇒ ۴۰۱؛ حسابِ پمپ دوران نمی‌گیرد', async () => {
  assert.equal((await h.api('GET', '/api/shop/sync-epoch')).status, 401);
  const pump = await h.newUser('پمپ‌دارِ دوران', 'pump');
  const r = await h.api('GET', '/api/shop/sync-epoch', { token: pump.accessToken, headers: { 'X-App': 'pump' } });
  assert.ok([403, 404].includes(r.status), `${r.status} ${JSON.stringify(r.body)}`);
  assert.equal(r.body?.epoch, undefined);
});

test('ردیفِ خراب در app_config ⇒ دورانِ تازه، نه خطا', async () => {
  await query("UPDATE app_config SET value='not-json' WHERE key='sync.epoch'");
  const r = await getEpoch(owner.accessToken);
  assert.equal(r.status, 200);
  assert.match(r.body.epoch.id, /^ep_/);
});
