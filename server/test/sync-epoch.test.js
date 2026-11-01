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
//  پرچمِ §۹.۵ پیش‌فرض خاموش است؛ این پرونده رفتارِ روشنش را می‌سنجد (خاموش: آزمونِ «خاموش‌کن»)
process.env.SYNC_EPOCH_ENABLED = '1';
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

test('پرچمِ §۹.۵ پیش‌فرض خاموش است: بی SYNC_EPOCH_ENABLED ⇒ config.syncEpoch.enabled = false', () => {
  const { execFileSync } = require('node:child_process');
  const env = { ...process.env };
  delete env.SYNC_EPOCH_ENABLED;
  const out = execFileSync(process.execPath, ['-e', "process.stdout.write(String(require('./src/config').syncEpoch.enabled))"],
    { cwd: require('node:path').join(__dirname, '..'), env }).toString();
  assert.equal(out, 'false');
});

test('خاموش ⇒ هیچ نوشتنی: نه دوران پس از بازیابی، نه نشانِ پشتیبان، نه دوران در بالا آمدن', async () => {
  const backup = require('../src/lib/backup');
  const before = await one('SELECT value FROM app_config WHERE key = $1', [epoch.KEY]);
  const markers = async () => (await query("SELECT key FROM app_config WHERE key LIKE 'sync.backup_marker.%'")).rows.length;
  const m0 = await markers();
  config.syncEpoch.enabled = false;
  try {
    let rotated = false;
    const out = await backup.afterRestore({ ok: true, bytes: 10 }, config.db.url, { rotate: async () => { rotated = true; return { id: 'ep_x' }; } });
    assert.equal(rotated, false, 'پرچمِ خاموش ⇒ rotate صدا زده نمی‌شود');
    assert.deepEqual(out, { ok: true, bytes: 10, epoch: false });
    //  run بی pg_dump شکست می‌خورد — ولی پیش از آن هم نشانی نمی‌نشیند
    const saved = config.backup.pgDump;
    config.backup.pgDump = '/nonexistent/pg_dump-for-test';
    try { await backup.run({ kind: 'manual' }).catch(() => {}); } finally { config.backup.pgDump = saved; }
    assert.equal(await markers(), m0, 'نشانِ پشتیبان ننشست');
    //  بالا آمدنِ سرور با پرچمِ خاموش دوران را دست نمی‌زند
    const { createApp } = require('../src/app');
    await createApp({ runMigrations: false });
    const after = await one('SELECT value FROM app_config WHERE key = $1', [epoch.KEY]);
    assert.deepEqual(after, before, 'دوران همان ماند');
  } finally {
    config.syncEpoch.enabled = true;
  }
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

test('v1: pull و Snapshot ِ دکان با پرچمِ روشن دوران دارند — برنامه فقط آن‌وقت درِ دوران را می‌زند', async () => {
  const hdr = { 'X-App': 'shop', 'X-Device': DEV };
  const p = await h.api('GET', `/api/sync/v1/pull?device_id=${DEV}&since=0`, { token: owner.accessToken, headers: hdr });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const now = await ep();
  assert.deepEqual(p.body.epoch && { id: p.body.epoch.id, base: p.body.epoch.base }, { id: now.id, base: now.base });
  assert.ok(p.body.epoch.base <= p.body.epoch.head);
  const s = await h.api('GET', '/api/sync/v1/snapshot', { token: owner.accessToken, headers: hdr });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  assert.equal(s.body.epoch?.id, now.id);
});

test('v1: پرچمِ خاموش ⇒ pull و Snapshot بی کلیدِ epoch (برنامه درِ ۴۰۴ِ دوران را هرگز نمی‌زند)', async () => {
  config.syncEpoch.enabled = false;
  try {
    const hdr = { 'X-App': 'shop', 'X-Device': DEV };
    const p = await h.api('GET', `/api/sync/v1/pull?device_id=${DEV}&since=0`, { token: owner.accessToken, headers: hdr });
    assert.equal(p.status, 200, JSON.stringify(p.body));
    assert.equal('epoch' in p.body, false, 'pull ِ خاموش کلیدِ epoch دارد');
    const s = await h.api('GET', '/api/sync/v1/snapshot', { token: owner.accessToken, headers: hdr });
    assert.equal(s.status, 200, JSON.stringify(s.body));
    assert.equal('epoch' in s.body, false, 'Snapshot ِ خاموش کلیدِ epoch دارد');
  } finally {
    config.syncEpoch.enabled = true;
  }
});

test('بی توکن ⇒ ۴۰۱؛ توکنِ پمپ ⇒ ۴۰۱ (همان قاعدهٔ همهٔ مسیرهای /api/shop)', async () => {
  assert.equal((await h.api('GET', '/api/shop/sync-epoch')).status, 401);
  const pump = await h.newUser('پمپ‌دارِ دوران', 'pump');
  const r = await h.api('GET', '/api/shop/sync-epoch', { token: pump.accessToken, headers: { 'X-App': 'pump' } });
  assert.equal(r.status, 401, `${r.status} ${JSON.stringify(r.body)}`);
  assert.equal(r.body?.epoch, undefined);
});

/* ---------------------------------------------- چرخهٔ نشانِ پشتیبان (بازبینیِ دوم) */

test('نشانِ پشتیبانِ کشته‌شده (پاک نشد) ⇒ بالا آمدنِ بعدی دورانِ تازه با پایه = سرِ دفتر: بی‌ضرر', async () => {
  const p = await push(owner.accessToken, [v1op('p-ep-k1', { name: 'نمک' })]);
  const head = p.body.results[0].server_seq;
  const old = await ep();
  //  پشتیبان نشان گذاشت و فرایند وسطِ کار افتاد ⇒ نشان در دیتابیسِ زنده ماند
  await epoch.markBackup();
  assert.equal(await markers(), 1);
  await epoch.rotate('boot', { busy: async () => false });
  const e = await ep();
  assert.notEqual(e.id, old.id);
  assert.equal(e.base, head, 'پایه همان سرِ دفترِ زنده');
  assert.equal(e.head, head);
  assert.equal(await markers(), 0);
  //  دستگاهی که تا همین‌جا همه را دیده: cursor ≤ پایه و چیزی بالای آن نفرستاده ⇒ `RestoreReplay.plan`
  //  هیچ کاری نمی‌کند (قرینه در `RestoreReplayTest` و `E2E-OPS-14.crashed-marker`)
});

test('پشتیبانی که هم‌زمان در فرایندِ جدا در جریان است: بالا آمدنِ سرور نشانش را نه می‌شمارد نه پاک می‌کند', async () => {
  const old = await ep();
  const unmark = await epoch.markBackup();
  await epoch.rotate('boot', { busy: async () => true });
  assert.equal((await ep()).id, old.id, 'پشتیبانِ در جریان «بازیابی» نیست');
  assert.equal(await markers(), 1, 'نشانِ پشتیبانِ در جریان سرِ جایش ماند');
  await unmark();
  assert.equal(await markers(), 0);
  //  `rotate('restore')` همیشه بازیابی است
  await epoch.rotate('restore', { busy: async () => true });
  assert.notEqual((await ep()).id, old.id);
  assert.equal(await epoch.backupRunning(), false, 'PGlite: pg_stat_activity نیست ⇒ نه');
});

test('نشانِ پشتیبان ننشست یا پاک نشد ⇒ خبرِ سامانه‌ای در «سابقه»ی پنل (نه فقط لاگ)', async () => {
  const fail = async () => { throw new Error('disk full'); };
  const unmark = await epoch.markBackup({ q: fail });
  await unmark();
  const a = await one("SELECT detail FROM audit_logs WHERE action='backup.sync_marker_failed' ORDER BY created_at DESC LIMIT 1");
  assert.ok(a, 'خبرِ «نشان ننشست» ثبت نشد');
  assert.match(JSON.stringify(a.detail), /disk full/);
  let calls = 0;
  const flaky = async (sql, args) => { calls += 1; if (calls === 2) throw new Error('gone away'); return query(sql, args); };
  const unmark2 = await epoch.markBackup({ q: flaky });
  await unmark2();
  assert.ok(await one("SELECT 1 AS x FROM audit_logs WHERE action='backup.sync_marker_left'"), 'خبرِ «نشان ماند» ثبت نشد');
  await query("DELETE FROM app_config WHERE key LIKE 'sync.backup_marker.%'");
});

test('بازگرداندن: داده نشست ولی دوران تازه نشد ⇒ «بازیابی شد» با epochError ِ جدا، نه شکست', async () => {
  const backup = require('../src/lib/backup');
  const ok = await backup.afterRestore({ ok: true, bytes: 10 }, config.db.url, { rotate: async () => ({ id: 'ep_x' }) });
  assert.deepEqual(ok, { ok: true, bytes: 10, epoch: 'ep_x' });
  const bad = await backup.afterRestore({ ok: true, bytes: 10 }, config.db.url, { rotate: async () => { throw new Error('locked'); } });
  assert.equal(bad.ok, true);
  assert.equal(bad.epoch, false);
  assert.equal(bad.epochError, 'locked');
  assert.ok(await one("SELECT 1 AS x FROM audit_logs WHERE action='backup.sync_epoch_failed'"));
  const other = await backup.afterRestore({ ok: true, bytes: 1 }, 'postgres://elsewhere/db', { rotate: async () => { throw new Error('نباید'); } });
  assert.equal(other.epoch, false, 'دیتابیسِ دیگر ⇒ دورانِ این سرور دست نمی‌خورد');
});

/* ---------------------------------------------- کدام ردیفِ تنهای دستگاه حذف‌شده است */

test('v1: /sync-epoch/deleted فقط ردیف‌های حذف‌شدهٔ همین دکان را می‌گوید', async () => {
  const del = { op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-del-1', type: 'delete' };
  const r = await push(owner.accessToken, [v1op('p-del-1', { name: 'رفته' }), v1op('p-live-1', { name: 'مانده' }), del]);
  assert.deepEqual(r.body.results.map((x) => x.status), ['applied', 'applied', 'applied']);
  const ask = (token, rows) => h.api('POST', '/api/shop/sync-epoch/deleted', { token, body: { rows }, headers: { 'X-App': 'shop' } });
  const out = await ask(owner.accessToken, [
    { table: 'products', row_id: 'p-del-1' }, { table: 'products', row_id: 'p-live-1' },
    { table: 'products', row_id: 'p-never' }, { table: 'nope', row_id: 'p-del-1' }, { table: 'products' },
  ]);
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.deepEqual(out.body.deleted, [{ table: 'products', row_id: 'p-del-1' }]);
  //  دکانِ دیگر همان شناسه را نمی‌بیند
  let other = await h.newUser('صاحبِ دکانِ دیگر', 'shop');
  await h.api('POST', '/api/shop', { token: other.accessToken, body: { name: 'دکانِ دیگرِ دوران' } });
  other = await h.signIn(other, 'shop');
  const o = await ask(other.accessToken, [{ table: 'products', row_id: 'p-del-1' }]);
  assert.equal(o.status, 200, JSON.stringify(o.body));
  assert.deepEqual(o.body.deleted, []);
  assert.equal((await ask(owner.accessToken, new Array(501).fill({ table: 'products', row_id: 'x' }))).status, 400);
  assert.equal((await ask(owner.accessToken, 'x')).status, 400);
  assert.equal((await h.api('POST', '/api/shop/sync-epoch/deleted', { body: { rows: [] } })).status, 401);
  config.syncEpoch.enabled = false;
  try { assert.equal((await ask(owner.accessToken, [])).status, 404); } finally { config.syncEpoch.enabled = true; }
});

test('v3: /api/sync/v3/deleted همان، روی sync_v3_rows', async () => {
  const hlc = require('../src/lib/hlc');
  let tick = 0;
  const H = () => hlc.format({ wall: Date.now() + (++tick), counter: 0, node: 'epoch-v3' });
  const p3 = (ops) => h.api('POST', '/api/sync/v3/push', { token: owner.accessToken, body: { device_id: 'epoch-v3', protocol: 3, ops } });
  const ins = (row) => ({ op_id: opId(), hlc: H(), table: 'products', row_id: row, kind: 'insert', fields: { name: row } });
  const r = await p3([ins('p3-del'), ins('p3-live')]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const d = await p3([{ op_id: opId(), hlc: H(), table: 'products', row_id: 'p3-del', kind: 'delete', fields: {} }]);
  assert.equal(d.status, 200, JSON.stringify(d.body));
  const out = await h.api('POST', '/api/sync/v3/deleted', {
    token: owner.accessToken,
    body: { rows: [{ table: 'products', row_id: 'p3-del' }, { table: 'products', row_id: 'p3-live' }, { table: 'nope', row_id: 'p3-del' }] },
  });
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.deepEqual(out.body.deleted, [{ table: 'products', row_id: 'p3-del' }]);
  assert.equal((await h.api('POST', '/api/sync/v3/deleted', { token: owner.accessToken, body: {} })).status, 400);
});
