'use strict';
/**
 * بازبینیِ امنیتیِ دومِ دسته‌های ۳۷–۳۸ (docs/SECURITY-AUDIT-fa.md، «بازبینیِ ۳۸»). هر آزمون پیش از رفع سرخ بود.
 * ⛔ کلید و رازها همه همین‌جا تصادفی ساخته می‌شوند؛ هیچ کدام چاپ نمی‌شود.
 */
//  SR38-3: سقفِ تراکنشِ بیکارِ استخر (PERF-04، پیشنهادِ تولید ۶۰ ثانیه) — PGlite گزینهٔ استخر ندارد
process.env.PG_IDLE_TX_TIMEOUT_MS ||= '800';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const h = require('./helpers');
const config = require('../src/config');
const { query } = require('../src/db');
const plans = require('../src/lib/plans');
const backup = require('../src/lib/backup');

const onPglite = String(process.env.TEST_DATABASE_URL || '').startsWith('pglite');
const hasPgDump = (() => {
  if (onPglite) return false;
  try { require('node:child_process').execFileSync(config.backup.pgDump, ['--version'], { stdio: 'ignore' }); return true; } catch { return false; }
})();
const BACKUP_DIR = path.join(os.tmpdir(), `shop-secrev38-${process.pid}`);
const KEY = crypto.randomBytes(32).toString('hex');
const MARK = `SR38-${crypto.randomBytes(6).toString('hex')}`;
const PLANTED = {
  vapid_private: `${MARK}-vapid`,
  email_pass: `${MARK}-smtp-pass`,
  email_key: `${MARK}-mail-api`,
  sms_key: `${MARK}-sms`,
};

test.before(async () => {
  await h.start();
  config.backup.dir = BACKUP_DIR;
  config.backup.passphrase = '';
  config.backup.secrets = 'plain';
});
test.after(async () => {
  config.backup.secrets = 'plain';
  delete process.env.BACKUP_SECRETS_KEY;
  await h.stop();
  await fs.rm(BACKUP_DIR, { recursive: true, force: true });
});

/*
 *  SR38-2: پارسرِ همگانیِ JSON پیش از هر سقفِ نرخ و احراز می‌دود (`app.js`). `reviver` ِ NUL (ممیزیِ ۳۵ B4) هر
 *  JSON.parse را ۱۰–۲۵ برابر کند می‌کرد ⇒ هر بدنهٔ ۲MB ِ بی‌نام (`[0,0,…]`) ~۰٫۷ ثانیه حلقهٔ رویداد را می‌بست، بی هیچ سقفی.
 *  حالا هزینهٔ بدنهٔ بی NUL همان JSON.parse است و NUL هنوز همه‌جا (تو در تو، آرایه) می‌افتد.
 */
test('SR38-2 بدنهٔ JSON ِ ۲MB ِ بی NUL: هزینه همان JSON.parse (نه reviver ِ کند پیش از سقفِ نرخ)؛ NUL ِ تو در تو هنوز می‌افتد', async () => {
  const body = `[${'0,'.repeat(1024 * 1024 - 8)}0]`;
  assert.ok(body.length < 2 * 1024 * 1024);
  const parseMs = () => { const t = process.hrtime.bigint(); JSON.parse(body); return Number(process.hrtime.bigint() - t) / 1e6; };
  parseMs();
  const plain = Math.min(parseMs(), parseMs(), parseMs());
  const hit = async () => {
    const t = process.hrtime.bigint();
    const r = await fetch(`${h.base()}/api/sr38-no-such-route`, { method: 'POST', headers: { 'content-type': 'application/json' }, body });
    await r.arrayBuffer();
    return { status: r.status, ms: Number(process.hrtime.bigint() - t) / 1e6 };
  };
  await hit();
  const runs = [await hit(), await hit(), await hit()];
  assert.ok(runs.every((x) => x.status === 401), 'بدنه پارس شد (نه ۴۰۰/۴۱۳) و درِ ناشناختهٔ /api همان ۴۰۱');
  const best = Math.min(...runs.map((x) => x.ms));
  assert.ok(best < plain * 6 + 250, `پارسِ بدنه ${best.toFixed(0)}ms، JSON.parse ِ خالص ${plain.toFixed(0)}ms`);

  //  رفتارِ B4 همان: NUL در هر عمقی و در آرایه از مقدار می‌افتد؛ `\\u0000` ِ متنی (بک‌اسلشِ گریخته) NUL نیست؛
  //  utf-16 هم پیموده می‌شود. همان پارسرِ app.js روی یک برنامهٔ express ِ کوچک که بدنه را برمی‌گرداند
  const express = require('express');
  const echoApp = express().use(require('../src/middleware/json-body').jsonBody())
    .post('/api/x', (req, res) => res.json({ body: req.body, proto: Object.getPrototypeOf(req.body) === Object.prototype }))
    .post('/api/sync/v1/x', (req, res) => res.json({ body: req.body }));
  const srv = await new Promise((ok) => { const s = echoApp.listen(0, '127.0.0.1', () => ok(s)); });
  const at = `http://127.0.0.1:${srv.address().port}`;
  try {
    const send = (p, b, ct = 'application/json') => fetch(`${at}${p}`, { method: 'POST', headers: { 'content-type': ct }, body: b }).then((r) => r.json());
    const raw = '{"a":"x\\u0000y","n":[{"b":"\\u0000z"},"\\u0000",[["q\\u0000"]]],"e":"\\\\u0000","__proto__":"p\\u0000","k\\u0000":1}';
    const want = { a: 'xy', n: [{ b: 'z' }, '', [['q']]], e: '\\u0000', k: '' };
    const got = await send('/api/x', raw);
    assert.equal(got.proto, true, 'نمونهٔ اولیه دست نخورد');
    assert.equal(got.body.a, want.a); assert.deepEqual(got.body.n, want.n); assert.equal(got.body.e, want.e);
    assert.equal(got.body.__proto__, 'p');
    assert.equal(got.body['k\u0000'], 1, 'کلیدها همان (مثلِ reviver ِ پیشین)');
    const u16 = await send('/api/x', Buffer.from('{"a":"x\\u0000y"}', 'utf16le'), 'application/json; charset=utf-16le');
    assert.equal(u16.body.a, 'xy', 'utf-16 هم پیموده می‌شود');
    const sync = await send('/api/sync/v1/x', '{"a":"x\\u0000y"}');
    assert.equal(sync.body.a, 'x\u0000y', 'همگام‌سازی بیت‌به‌بیت همان');
  } finally { await new Promise((ok) => srv.close(ok)); }
});

/*
 *  SR38-1: روی PGlite، `pglite-stats` هر ۶ ساعت (نخستین بار یک دقیقه پس از بالا آمدن) روی **همهٔ** جدول‌ها، از جمله
 *  app_config، ANALYZE می‌زند ⇒ نمونهٔ مقدارها (most_common_vals / histogram_bounds) در pg_statistic ِ پوشهٔ داده.
 *  `sealPglite` فقط خودِ app_config را بازنویسی می‌کرد ⇒ پشتیبانِ «مهرشده» رازِ خام را در کاتالوگِ آمار داشت.
 */
test('SR38-1 seal روی PGlite: پس از ANALYZE ِ دوره‌ای هیچ رازِ خامی در pg_statistic ِ پشتیبان نیست', { skip: onPglite ? false : 'فقط پوشهٔ دادهٔ PGlite (pg_dump آمار نمی‌ریزد)' }, async () => {
  process.env.BACKUP_SECRETS_KEY = KEY;
  for (const [k, v] of Object.entries(PLANTED)) await plans.setConfig(k, v);
  //  همان کاری که `workers.js` ⇐ `pglite-stats.schedule()` در تولید می‌کند
  assert.equal(await require('../src/lib/pglite-stats').analyzeNow(), true);
  const live = (await query(`SELECT coalesce(most_common_vals::text,'') || coalesce(histogram_bounds::text,'') AS s
    FROM pg_stats WHERE tablename = 'app_config' AND attname = 'value'`)).rows.map((r) => r.s).join('');
  assert.ok(live.includes(MARK), 'پیش‌شرط: ANALYZE مقدارِ راز را در آمارِ دیتابیسِ زنده گذاشته است');

  config.backup.secrets = 'seal';
  let out;
  try { out = await backup.run({ kind: 'manual' }); } finally { config.backup.secrets = 'plain'; }
  const raw = await backup.decode(out.file);
  const lite = await require('../src/lib/pglite-create').createPglite({ dataDir: 'memory://', loadDataDir: new Blob([raw]) });
  try {
    const stats = (await lite.query(`SELECT coalesce(most_common_vals::text,'') || coalesce(histogram_bounds::text,'') AS s
      FROM pg_stats WHERE tablename = 'app_config'`)).rows.map((r) => r.s).join('');
    assert.equal(stats.includes(MARK), false, 'رازِ خام در pg_statistic ِ پشتیبانِ مهرشده');
    const rows = (await lite.query('SELECT key FROM app_config WHERE key = ANY($1::text[])', [Object.keys(PLANTED)])).rows;
    assert.equal(rows.length, 0, 'ردیف‌های راز مهر شده‌اند');
  } finally { await lite.close(); }
  assert.equal(raw.indexOf(Buffer.from(MARK)), -1, 'هیچ بایتِ خامی از راز در tar');
});

/*
 *  SR38-3: `openSnapshot` (`backup.js`) اتصالی از استخر را در تراکنشِ باز و بیکار نگه می‌دارد تا pg_dump تمام شود. pg-pool
 *  شنوندهٔ `error` را هنگامِ بیرون‌کشیدن برمی‌دارد ⇒ با `PG_IDLE_TX_TIMEOUT_MS` (دستهٔ ۳۷) و پشتیبانِ بلندتر از آن، PostgreSQL
 *  همان نشست را می‌بُرد، `error` شنونده‌ای نداشت ⇒ uncaughtException ⇒ `index.js` سرور را خاموش می‌کرد (هر شب، سرِ پشتیبان).
 *  pg_dump ِ واقعی snapshot را همان آغاز برمی‌دارد؛ پوسته فقط پس از آن ۱٫۵ ثانیه خروجی را باز نگه می‌دارد (= dump ِ بلند).
 */
test('SR38-3 PostgreSQL: pg_dump بلندتر از PG_IDLE_TX_TIMEOUT_MS ⇒ پشتیبان (plain و seal) می‌نشیند و فرایند نمی‌افتد', { skip: onPglite ? 'PGlite گزینهٔ استخر ندارد' : !hasPgDump && 'pg_dump روی این ماشین نیست' }, async () => {
  assert.equal(config.db.idleTxTimeoutMs, 800);
  await fs.mkdir(BACKUP_DIR, { recursive: true });
  const wrap = path.join(BACKUP_DIR, 'slow-pg-dump.sh');
  await fs.writeFile(wrap, `#!/bin/sh\n"${config.backup.pgDump}" "$@"\nrc=$?\nsleep 1.5\nexit $rc\n`, { mode: 0o755 });
  const crashes = [];
  const onCrash = (e) => crashes.push(e);
  process.prependListener('uncaughtException', onCrash);
  const saved = config.backup.pgDump;
  config.backup.pgDump = wrap;
  process.env.BACKUP_SECRETS_KEY = KEY;
  for (const [k, v] of Object.entries(PLANTED)) await plans.setConfig(k, v);
  try {
    for (const mode of ['plain', 'seal']) {
      config.backup.secrets = mode;
      const out = await backup.run({ kind: 'manual' });
      assert.ok((await fs.stat(out.file)).size > 0, `${mode}: پشتیبان نشست`);
      assert.ok(out.manifest, `${mode}: manifest ِ همان snapshot`);
      if (mode === 'seal') assert.ok(out.secrets.sealed >= Object.keys(PLANTED).length, 'seal: رازها مهر و شمرده شدند');
    }
    await new Promise((r) => setTimeout(r, 200));
    assert.deepEqual(crashes.map((e) => String(e.message || e)), [], '⛔ بی uncaughtException (index.js سرور را خاموش می‌کند)');
    assert.equal((await query('SELECT 1 AS n')).rows[0].n, 1, 'استخر سالم');
  } finally {
    process.off('uncaughtException', onCrash);
    config.backup.pgDump = saved;
    config.backup.secrets = 'plain';
  }
});
