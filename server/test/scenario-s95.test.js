'use strict';
/**
 * S95 — پایش و runbook (§۱۹.۱۲): «خطای ساختگی DB، فضای دیسک کم، backup ناموفق، صف sync و سفارش معطل ایجاد شود؛
 * هشدار درست برسد و runbook بازیابی با موفقیت تمرین شود.»
 *
 * پیش‌تر: پشتیبانِ ناموفق (`restore-drill.test.js`)، صفِ گیرکرده (`system-health.test.js`)، db_busy (`ops-paging.test.js`)
 * و تمرینِ بازیابی (`ops.e2e.mjs`، `recovery.e2e.mjs`) سنجیده می‌شدند؛ ولی **خطای خودِ دیتابیس و دیسکِ پُر هرگز در آزمون
 * ساخته نشده بود** و هشدارِ فعال (ایمیل/پوش) از کارتِ سلامت دیده نشده بود — فقط پنل. این‌جا هر دو خطا در خودِ راه‌انداز
 * (pg یا PGlite) و `fs.statfs` تزریق می‌شوند و همان زنجیرهٔ واقعی دنبال می‌شود:
 *
 *   سلامت (`/api/admin/system/health`) ⇒ قاعدهٔ `service_down` ِ هشدارها ⇒ هشدارِ critical ⇒ خبر (ایمیل و پوش) به مسئول
 *   ⇒ برطرف شد ⇒ هشدار خودکار بسته ⇒ «بسته شد» پس از آرامش.
 *
 * درِ ارسال همان درِ آزمونِ `ops-paging` (`_setTransport`) است؛ پرچمِ `ops.critical_alerts` روشن (پیش‌فرض خاموش).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fsp = require('node:fs/promises');

let h; let db; let paging; let alerts; let live;
const PASS = 'Admin!12345';
const MIN = 60_000;
const sent = [];
let owner = '';
let ownerId = '';

const capture = (ch, r, m) => { sent.push({ ch, adminId: r.admin_id, kind: m.kind, level: m.level, alertId: m.alertId, subject: m.subject, text: m.text }); return { ok: true, detail: 'test' }; };
const health = () => h.get('/api/admin/system/health', { token: owner });
const alertOf = (target) => db.one("SELECT * FROM ops_alerts WHERE rule='service_down' AND target=$1 ORDER BY first_seen_at DESC LIMIT 1", [target]);
const of = (id, kind) => sent.filter((s) => s.alertId === id && s.kind === kind);

/** خطای ساختگیِ دیتابیس: فقط پرس‌وجوی سنجشِ سلامت (`SELECT 1 AS ok`) در خودِ راه‌انداز شکست می‌خورد. */
function dbFault() {
  const hit = (text) => String(text).trim() === 'SELECT 1 AS ok';
  const boom = () => Object.assign(new Error('S95: اتصال به دیتابیس افتاد (ساختگی)'), { code: '57P01' });
  if (db.isPglite()) {
    const { PGlite } = require('@electric-sql/pglite');
    const own = Object.getOwnPropertyDescriptor(PGlite.prototype, 'exec');
    const orig = PGlite.prototype.exec;
    PGlite.prototype.exec = function exec(text, ...rest) { return hit(text) ? Promise.reject(boom()) : orig.call(this, text, ...rest); };
    return () => { if (own) Object.defineProperty(PGlite.prototype, 'exec', own); else delete PGlite.prototype.exec; };
  }
  const pool = db.getPool();
  const orig = pool.query;
  pool.query = function query(text, ...rest) { return hit(text) ? Promise.reject(boom()) : orig.call(this, text, ...rest); };
  return () => { pool.query = orig; };
}

/** دیسکِ پُر: `statfs` ِ پوشهٔ پشتیبان [pct] درصد جای خالی برمی‌گرداند. */
function lowDisk(pct) {
  const orig = fsp.statfs;
  fsp.statfs = async () => ({ type: 0, bsize: 4096, blocks: 100_000, bfree: pct * 1000, bavail: pct * 1000, files: 0, ffree: 0 });
  return () => { fsp.statfs = orig; };
}

/**
 *  دیسکِ «سالم» هم تزریقی است، نه دیسکِ واقعیِ میزبان: روی ماشینی با کمتر از ۲٪ جای خالی (CI یا سیزنِ محلیِ تنگ)
 *  S95.0 و S95.recover سرخ می‌شدند، چون بیرون از پنجره‌های `lowDisk` همان `statfs` ِ واقعی خوانده می‌شد. حالا کلِ
 *  این پرونده روی ۵۰٪ ِ ساختگی است و `lowDisk` فقط موقتاً عوضش می‌کند؛ قاعدهٔ آستانه (`storage()` ِ `system-health`) همان.
 */
const HEALTHY_PCT = 50;
let realDisk = null;

test.before(async () => {
  realDisk = lowDisk(HEALTHY_PCT);
  h = require('./helpers');
  await h.start();
  db = require('../src/db');
  paging = require('../src/lib/ops-paging');
  alerts = require('../src/lib/ops-alerts');
  live = require('../src/lib/live-config');
  const pw = require('../src/lib/password');
  ownerId = db.newId('adm');
  await db.query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'s95_owner','مسئولِ شب',$2,'superadmin','active',$3)`,
    [ownerId, await pw.hashPassword(PASS), db.now()]);
  owner = (await h.post('/api/admin/login', { username: 's95_owner', password: PASS })).body.token;
  assert.ok(owner, 'ورودِ مدیر');
  const r = await h.api('PUT', '/api/admin/ops/alerts/paging', { token: owner, body: { recipients: [{ adminId: ownerId, tier: 'primary', email: 'oncall@example.com' }] } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  await live.set('ops', 'ops.critical_alerts', true, 'test');
  paging._setTransport(capture);
});
test.after(async () => { paging._setTransport(null); paging.stop(); await h.stop(); realDisk?.(); });

test('S95.limit: آستانه‌های واقعیِ دیسک — زیرِ ۲٪ «down»، زیرِ ۱۰٪ «warn»، لبه‌ها دقیق', async () => {
  const { LIMITS } = require('../src/lib/system-health');
  assert.deepEqual([LIMITS.diskDownPct, LIMITS.diskWarnPct], [2, 10]);
  //  bfree به هزارم: ۱٫۹ ⇒ down، ۲ ⇒ warn، ۹٫۹ ⇒ warn، ۱۰ ⇒ ok
  for (const [pct, want] of [[1.9, 'down'], [2, 'warn'], [9.9, 'warn'], [10, 'ok']]) {
    const undo = lowDisk(pct);
    try {
      const r = await health();
      assert.equal(r.body.storage.status, want, `${pct}%`);
      assert.equal(r.body.storage.freePct, pct);
    } finally {
      undo();
    }
  }
});

test('S95.0: پیش از خطا دیتابیس و دیسک سالم‌اند و هشدارِ «از کار افتاده» ِ آن‌ها نیست', async () => {
  const r = await health();
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.database.status, 'ok');
  assert.equal(r.body.storage.status, 'ok');
  assert.equal(r.body.storage.freePct, HEALTHY_PCT, 'دیسکِ سالمِ تزریقی، نه دیسکِ واقعیِ میزبان');
  await alerts.evaluate({ force: true });
  assert.equal(await alertOf('database'), null);
  assert.equal(await alertOf('storage'), null);
});

test('S95.db: خطای ساختگیِ دیتابیس ⇒ سلامت «down» بی متنِ خطا ⇒ هشدارِ critical ⇒ ایمیل و پوش به مسئول، یک بار', async () => {
  const undo = dbFault();
  try {
    const r = await health();
    assert.equal(r.status, 200, 'شکستِ یک بخش پاسخ را ۵۰۰ نمی‌کند');
    assert.equal(r.body.database.status, 'down');
    assert.equal(r.body.status, 'down');
    assert.doesNotMatch(JSON.stringify(r.body), /ساختگی|57P01|postgres:\/\//, 'متنِ خطا و نشانیِ دیتابیس بیرون نمی‌رود');

    const rep = await alerts.evaluate({ force: true });
    assert.equal(rep.rules.find((x) => x.id === 'service_down').state, 'ok');
    assert.equal(rep.paging.state, 'ok', JSON.stringify(rep.paging));
    const a = await alertOf('database');
    assert.equal(a.severity, 'critical');
    assert.equal(a.status, 'open');
    const got = of(a.id, 'alert');
    assert.deepEqual(got.map((s) => `${s.ch}:${s.adminId}`).sort(), [`email:${ownerId}`, `push:${ownerId}`]);
    assert.match(got[0].subject, /database/);
    assert.doesNotMatch(`${got[0].subject} ${got[0].text}`, /ساختگی|57P01/);
    const log = await db.many('SELECT channel, ok FROM ops_alert_page_log WHERE alert_id=$1 ORDER BY channel', [a.id]);
    assert.deepEqual(log.map((x) => `${x.channel}:${x.ok}`), ['email:true', 'push:true']);

    //  خطا هنوز هست: ارزیابیِ دوباره همان هشدار است، نه خبرِ دوم
    await alerts.evaluate({ force: true });
    assert.equal((await alertOf('database')).id, a.id);
    assert.equal(of(a.id, 'alert').length, 2);
  } finally {
    undo();
  }
});

test('S95.disk: دیسکِ ۵٪ خالی فقط «هشدار» ِ کارت است (خبر نه)؛ ۱٪ ⇒ «down» ⇒ خبرِ بحرانی؛ هشدارِ دیتابیس خودکار بسته می‌شود', async () => {
  let undo = lowDisk(5);
  try {
    const r = await health();
    assert.equal(r.body.storage.status, 'warn');
    assert.equal(r.body.storage.freePct, 5);
    assert.equal(r.body.database.status, 'ok', 'دیتابیس دوباره سالم');
    await alerts.evaluate({ force: true });
    assert.equal(await alertOf('storage'), null, '«هشدار» ِ دیسک خبرِ بحرانی نمی‌سازد');
  } finally {
    undo();
  }
  const db1 = await alertOf('database');
  assert.equal(db1.status, 'resolved');
  assert.equal(db1.resolution, 'auto');

  undo = lowDisk(1);
  try {
    const r = await health();
    assert.equal(r.body.storage.status, 'down');
    assert.equal(r.body.storage.freePct, 1);
    assert.doesNotMatch(JSON.stringify(r.body.storage), /\/|\\/, 'مسیرِ پوشه بیرون نمی‌رود');
    const rep = await alerts.evaluate({ force: true });
    assert.equal(rep.paging.state, 'ok', JSON.stringify(rep.paging));
    const a = await alertOf('storage');
    assert.equal(a.severity, 'critical');
    assert.deepEqual(of(a.id, 'alert').map((s) => s.ch).sort(), ['email', 'push']);
    assert.match(of(a.id, 'alert')[0].subject, /storage/);
  } finally {
    undo();
  }
});

test('S95.recover: پس از رفعِ دیسک هشدار خودکار بسته می‌شود و «بسته شد» پس از آرامش یک بار به مسئول می‌رسد', async () => {
  await alerts.evaluate({ force: true });
  const disk = await alertOf('storage');
  const base = await alertOf('database');
  assert.equal(disk.status, 'resolved');
  assert.equal(disk.resolution, 'auto');
  const later = Number(disk.resolved_at) + 11 * MIN;
  await paging.run({ t: later });
  for (const a of [base, disk]) {
    assert.deepEqual(of(a.id, 'resolved').map((s) => `${s.ch}:${s.adminId}`).sort(), [`email:${ownerId}`, `push:${ownerId}`], a.target);
  }
  await paging.run({ t: later + MIN });
  assert.equal(of(disk.id, 'resolved').length, 2, '«بسته شد» دو بار نمی‌رسد');
});
