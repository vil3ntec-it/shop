'use strict';
/**
 * §۹.۵ — «دورانِ بازیابیِ» همگام‌سازی (`lib/sync-epoch.js`، `GET /api/shop/sync-epoch`،
 * `epoch` در pull ِ v3). سناریوی واقعیِ بازیابی (پشتیبانِ رمزدار ⇐ سرورِ تازه در فرایندِ
 * جدا ⇐ دستگاه‌ها دوباره می‌فرستند و از Snapshot از نو می‌سازند) در `test/e2e/ops.e2e.mjs`.
 *
 *   • بالا آمدنِ عادی ⇒ همان id و همان پایه (برنامه هیچ کاری نمی‌کند).
 *   • نشانِ پشتیبان در دیتابیسِ برگشته یا `rotate('restore')` ⇒ id ِ تازه، پایه = سرِ دفترِ همان لحظه.
 *   • «بازیابی» با شمارندهٔ برگشته (مثلِ پشتیبانِ واقعی): همان op_id دوباره «applied» با همان شماره‌ها.
 *   • خطای دیتابیس دوران را صفر نمی‌کند؛ فقط دکان؛ خاموش‌کن ⇒ ۴۰۴ و pull ِ v3 بی دوران.
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

const getEpoch = async (token) => {
  const r = await h.api('GET', '/api/shop/sync-epoch', { token, headers: { 'X-App': 'shop' } });
  return r;
};
const ep = async () => {
  const r = await getEpoch(owner.accessToken);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.epoch;
};
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, body: { device_id: DEV, schema_version: 1, ops }, headers: { 'X-App': 'shop', 'X-Device': DEV },
});
const markers = async () => (await query("SELECT key FROM app_config WHERE key LIKE 'sync.backup_marker.%'")).rows.length;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دوران', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ دوران' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
});
test.after(async () => { await h.stop(); });

test('بالا آمدنِ سرور دوران می‌نویسد؛ پاسخ: id، پایه و سرِ دفترِ همین حساب', async () => {
  const stored = await one("SELECT value FROM app_config WHERE key='sync.epoch'");
  assert.ok(stored, 'createApp دوران را ننوشت');
  const r = await getEpoch(owner.accessToken);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  const e = r.body.epoch;
  assert.match(e.id, /^ep_[a-z0-9]+$/);
  assert.equal(e.id, JSON.parse(stored.value).id);
  assert.ok(e.base <= e.head);
});

test('بالا آمدنِ عادی (حتی پس از opهای تازه) ⇒ همان id و همان پایه — برنامه هیچ کاری نمی‌کند', async () => {
  const before = await ep();
  const p = await push(owner.accessToken, [v1op('p-ep-1', { name: 'چای' }), v1op('p-ep-2', { name: 'قند' })]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const head = Math.max(...p.body.results.map((x) => x.server_seq));
  const boot1 = JSON.parse((await one("SELECT value FROM app_config WHERE key='sync.epoch'")).value).boot;
  await epoch.rotate('boot');
  await epoch.rotate('boot');
  const after = await ep();
  assert.equal(after.id, before.id, 'بالا آمدنِ عادی بازیابی نیست');
  assert.equal(after.base, before.base);
  assert.equal(after.head, head);
  const boot2 = JSON.parse((await one("SELECT value FROM app_config WHERE key='sync.epoch'")).value).boot;
  assert.notEqual(boot2, boot1, 'هر بالا آمدن شناسهٔ خودش را دارد');
});

test('«بازیابی» با نشانِ پشتیبان و شمارندهٔ برگشته: id ِ تازه، پایه = سرِ پشتیبان، همان op_id دوباره «applied»', async () => {
  const kept = await push(owner.accessToken, [v1op('p-ep-3', { name: 'برنج' })]);
  assert.equal(kept.status, 200);
  const backupHead = kept.body.results[0].server_seq;
  const old = await ep();

  //  پشتیبان: نشان پیش از گرفتن می‌نشیند (پس در فایل هست) و پس از آن از دیتابیسِ زنده می‌رود
  const unmark = await epoch.markBackup();
  assert.equal(await markers(), 1);
  const seqAtBackup = Number((await one("SELECT last_value FROM oplog_server_seq_seq")).last_value);
  await unmark();
  assert.equal(await markers(), 0, 'دیتابیسِ زنده نشان ندارد');
  await epoch.rotate('boot');
  assert.equal((await ep()).id, old.id, 'سرورِ زنده پس از پشتیبان: بالا آمدنِ عادی');

  const late = [v1op('p-ep-4', { name: 'روغن' }), v1op('p-ep-5', { name: 'آرد' })];
  const lp = await push(owner.accessToken, late);
  assert.deepEqual(lp.body.results.map((x) => x.status), ['applied', 'applied']);
  const lateSeqs = lp.body.results.map((x) => x.server_seq);

  //  دیتابیسِ «بازیابی‌شده» = همان لحظهٔ پشتیبان: opهای پس از آن نیستند، شمارنده برگشته و نشان هست
  //  (بازیابیِ واقعی با فایلِ پشتیبان و فرایندِ جدا: E2E-OPS-14.replay)
  await query("DELETE FROM oplog WHERE app='shop' AND account_id=$1 AND server_seq > $2", [shopId, backupHead]);
  await query("DELETE FROM sync_rows WHERE app='shop' AND account_id=$1 AND row_id = ANY($2)", [shopId, ['p-ep-4', 'p-ep-5']]);
  await query("SELECT setval('oplog_server_seq_seq', $1)", [seqAtBackup]);
  await query("INSERT INTO app_config (key, value, updated_at) VALUES ('sync.backup_marker.bk_test', '{}', 1)");
  await epoch.rotate('boot');

  const e = await ep();
  assert.notEqual(e.id, old.id, 'دیتابیسِ برگشته ⇒ دورانِ بازیابیِ تازه');
  assert.equal(e.base, backupHead);
  assert.equal(e.head, backupHead);
  assert.equal(await markers(), 0, 'نشان پس از شناختن پاک می‌شود');
  assert.ok(lateSeqs.every((s) => s > e.base), 'opهای گم‌شده بالای پایه‌اند ⇒ دستگاه همان‌ها را دوباره می‌فرستد');

  //  دستگاه همان opها را با همان op_id دوباره می‌فرستد (`RestoreReplay`)؛ شماره‌ها دوباره همان‌اند
  const again = await push(owner.accessToken, late);
  assert.deepEqual(again.body.results.map((x) => x.status), ['applied', 'applied'], 'سرورِ بازیابی‌شده آن‌ها را ندارد');
  assert.deepEqual(again.body.results.map((x) => x.server_seq), lateSeqs, 'شمارنده مثلِ بازیابیِ واقعی برگشته است');
  const twice = await push(owner.accessToken, late);
  assert.deepEqual(twice.body.results.map((x) => x.status), ['duplicate', 'duplicate'], 'تکرار بی‌خطر است');

  //  بالا آمدنِ بعدی: همان دورانِ بازیابی
  await epoch.rotate('boot');
  const next = await ep();
  assert.equal(next.id, e.id);
  assert.equal(next.base, backupHead);
});

test("rotate('restore') (بازگرداندنِ صریح، بی بالا آمدن) ⇒ id ِ تازه با پایه = سرِ دفتر", async () => {
  const before = await ep();
  await epoch.rotate('restore');
  const after = await ep();
  assert.notEqual(after.id, before.id);
  assert.equal(after.base, after.head);
});

test('خطای دیتابیس (جز «جدول نیست») دوران را صفر نمی‌کند و دورانِ پیشین سرِ جایش می‌ماند', async () => {
  const boom = async () => { throw Object.assign(new Error('canceling statement'), { code: '57014' }); };
  await assert.rejects(() => epoch.maxSeq('oplog', boom));
  assert.equal(await epoch.maxSeq('no_such', async () => { throw Object.assign(new Error('nope'), { code: '42P01' }); }), 0);
  const before = (await one("SELECT value FROM app_config WHERE key='sync.epoch'")).value;
  await assert.rejects(() => epoch.rotate('restore', { seqOf: (t) => epoch.maxSeq(t, boom) }));
  assert.equal((await one("SELECT value FROM app_config WHERE key='sync.epoch'")).value, before);
});

test('دورانِ خراب در app_config ⇒ ۵۰۰ (نه پایهٔ صفر)؛ بالا آمدنِ بعدی دورانِ تازه با پایه = سرِ دفتر', async () => {
  const good = (await one("SELECT value FROM app_config WHERE key='sync.epoch'")).value;
  await query("UPDATE app_config SET value='not-json' WHERE key='sync.epoch'");
  const r = await getEpoch(owner.accessToken);
  assert.equal(r.status, 500);
  assert.equal(r.body?.epoch, undefined);
  await epoch.rotate('boot');
  const e = await ep();
  assert.notEqual(e.id, JSON.parse(good).id);
  assert.equal(e.base, e.head, 'دورانِ تازه از سرِ دفتر: هیچ دستگاهی چیزی دوباره نمی‌فرستد');
});

test('v3: pull کنارِ opها دورانِ v3 را می‌دهد (پایه و سرِ دفترِ sync_v3_ops)', async () => {
  const r = await h.api('GET', `/api/sync/v3/pull?device_id=${DEV}&since=0`, { token: owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const e = r.body.epoch;
  assert.ok(e && typeof e.id === 'string' && e.id.startsWith('ep_'), JSON.stringify(e));
  assert.equal(e.id, (await ep()).id, 'یک دوران برای هر دو');
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
