'use strict';
/**
 * PERF-04 — «مهلتِ سراسریِ درخواست نیست»: `REQUEST_TIMEOUT_MS` (`middleware/deadline.js`) و سقفِ پرس‌وجو/تراکنشِ بیکارِ
 * PostgreSQL (`PG_STATEMENT_TIMEOUT_MS`، `PG_IDLE_TX_TIMEOUT_MS`، `db.js`). هر دو پیش‌فرض خاموش.
 *
 *   • مسیرِ کند ⇒ ۵۰۳ ِ `request_timeout` + Retry-After سرِ مهلت؛ نوشتن یا خطای دیرِ همان مسیر بی‌صدا دور ریخته می‌شود
 *   • مسیرِ تند، جریانی که سرآیندش رفته، long-poll ِ `?wait`، پمپ/Sync v1 و بارگذاریِ خوانده‌نشده بریده نمی‌شوند؛ خاموش ⇒ همان پیشین
 *   • PostgreSQL: `pg_sleep` ِ بلند سرِ سقف با 57014 می‌افتد ⇒ ۵۰۳ ِ `db_timeout`؛ استخر سالم می‌ماند؛ مهاجرت‌ها بی سقف
 */
process.env.PG_STATEMENT_TIMEOUT_MS = '400';
process.env.PG_IDLE_TX_TIMEOUT_MS = '800';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const express = require('express');
const h = require('./helpers');   //  پیش از config: نشانیِ پایگاهِ آزمون
const { requestDeadline, FLOOR_MS, LONG_POLL_MS } = require('../src/middleware/deadline');
const { errorHandler } = require('../src/middleware/errors');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const crashes = [];
const onCrash = (e) => crashes.push(e);
process.on('uncaughtException', onCrash);
process.on('unhandledRejection', onCrash);

/** برنامهٔ کوچک با همان میان‌افزار؛ `mount` مسیرها را می‌چیند */
async function serve(opts, mount) {
  const app = express();
  app.use(express.json());   //  همان برنامهٔ واقعی: فقط JSON خوانده می‌شود؛ بدنهٔ خام (octet-stream) برای خودِ مسیر می‌ماند
  app.use('/api', requestDeadline(opts));
  mount(app);
  app.use(errorHandler);
  const srv = http.createServer(app);
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  return { base, close: () => new Promise((r) => srv.close(r)) };
}
async function call(base, p, init) {
  const t = Date.now();
  const r = await fetch(base + p, init);
  const text = await r.text();
  let body = null; try { body = JSON.parse(text); } catch { body = text; }
  return { status: r.status, body, ms: Date.now() - t, retry: r.headers.get('retry-after') };
}

test.after(() => { process.off('uncaughtException', onCrash); process.off('unhandledRejection', onCrash); });

/*
 *  ⚠️ مسیرِ «کند» تا `release()` پاسخ نمی‌دهد، نه با تایمرِ ۴۰۰ms: زیرِ گیرِ حلقهٔ رویداد (CI ِ پربار) Node همهٔ تایمرهای
 *  موعدرسیدهٔ یک فهرستِ هم‌مدت را پیش از فهرستِ دیگر می‌دواند، پس تایمرِ ۴۰۰ms ِ مسیر (هم‌فهرستِ تایمرِ درخواستِ پیشین)
 *  پیش از تایمرِ ۱۵۰ms ِ مهلت اجرا می‌شد و ۲۰۱ ِ دیر زودتر می‌رسید (CI ِ #1021). با «رها» پاسخِ مسیر پیش از ۵۰۳ ممکن نیست.
 */
test('مسیرِ کند ⇒ ۵۰۳ ِ request_timeout سرِ مهلت؛ نوشتن و خطای دیر دور ریخته می‌شوند و فرایند نمی‌افتد', async () => {
  let release = null;
  const gate = new Promise((r) => { release = r; });
  const s = await serve({ ms: 150, floorMs: 0 }, (app) => {
    app.get('/api/slow', async (req, res) => { await gate; res.json({ late: true }); });
    app.get('/api/slow-throw', async () => { await gate; throw new Error('دیر'); });
    app.get('/api/slow-cb', (req, res) => { gate.then(() => res.status(201).set('X-Late', '1').send('دیر')); });
    app.get('/api/fast', (req, res) => res.json({ ok: true }));
  });
  try {
    const r = await call(s.base, '/api/slow');
    assert.equal(r.status, 503);
    assert.equal(r.body.error.code, 'request_timeout');
    assert.equal(r.retry, '5');
    assert.ok(r.ms >= 140, `سرِ مهلت، نه زودتر (${r.ms}ms)؛ کارِ مسیر هنوز رها نشده`);
    assert.equal((await call(s.base, '/api/slow-throw')).status, 503);
    assert.equal((await call(s.base, '/api/slow-cb')).status, 503);
    release();          //  حالا کارِ هر سه مسیر تمام می‌شود: نوشتن‌ها و خطای دیر پس از ۵۰۳
    await sleep(300);
    assert.deepEqual(crashes.map(String), [], '⛔ بی ERR_HTTP_HEADERS_SENT و بی افتادنِ فرایند');
    const f = await call(s.base, '/api/fast');
    assert.deepEqual([f.status, f.body], [200, { ok: true }], 'سرور سالم و مسیرِ تند دست‌نخورده');
  } finally { await s.close(); }
});

test('بریده نمی‌شوند: جریانی که سرآیندش رفته، long-poll، پمپ و Sync v1، بارگذاریِ خوانده‌نشده؛ خاموش و کف همان پیشین', async () => {
  const slow = (app, p) => app.all(p, async (req, res) => { await sleep(350); res.json({ ok: true }); });
  //  آن‌چه باید بریده شود تا `release()` پاسخ نمی‌دهد (همان علتِ آزمونِ بالا: تایمرِ مسیر پیش از مهلت زیرِ گیرِ CI)
  let release = null;
  const gate = new Promise((r) => { release = r; });
  const s = await serve({ ms: 120, floorMs: 0 }, (app) => {
    app.get('/api/stream', async (req, res) => { res.write('a'); await sleep(350); res.end('b'); });
    for (const p of ['/api/pump/device/rate', '/api/v1/pump/x', '/api/sync/v1/pull']) slow(app, p);
    app.all('/api/chat/messages', async (req, res) => { await (req.query.wait === '0' ? gate : sleep(350)); res.json({ ok: true }); });
    app.all('/api/json-slow', async (req, res) => { await gate; res.json({ ok: true }); });
    app.post('/api/upload', async (req, res) => { let n = 0; for await (const c of req) n += c.length; await sleep(350); res.json({ n }); });
  });
  try {
    assert.deepEqual([(await call(s.base, '/api/stream')).body], ['ab']);
    for (const p of ['/api/pump/device/rate', '/api/v1/pump/x', '/api/sync/v1/pull']) {
      assert.equal((await call(s.base, p)).status, 200, `${p}: کدِ مشترکِ پمپ بیرون است`);
    }
    assert.equal((await call(s.base, '/api/chat/messages?wait=25000')).status, 200, 'long-poll ِ `?wait` مهلتِ بیشتر دارد');
    assert.equal((await call(s.base, '/api/chat/messages?wait=0')).status, 503, 'بی صبر ⇒ همان مهلت');
    //  بارگذاریِ کند: بدنه تکه‌تکه در ۳۰۰ms می‌رسد (بیش از مهلتِ ۱۲۰ms) ⇒ زمانِ شبکهٔ مشتری شمرده نمی‌شود
    const up = await new Promise((resolve, reject) => {
      const rq = http.request(`${s.base}/api/upload`, { method: 'POST', headers: { 'content-type': 'application/octet-stream', 'content-length': 1000 } }, (rs) => {
        let b = ''; rs.on('data', (c) => { b += c; }); rs.on('end', () => resolve({ status: rs.statusCode, body: JSON.parse(b) }));
      });
      rq.on('error', reject);
      rq.write('x'.repeat(500));
      setTimeout(() => rq.end('x'.repeat(500)), 300);
    });
    assert.deepEqual([up.status, up.body], [200, { n: 1000 }], 'بارگذاری با شبکهٔ کند کارِ سرور نیست');
    //  ولی بدنهٔ JSON ِ خوانده‌شده (ورود، ثبتِ سفارش، push) شمرده می‌شود
    const js = await call(s.base, '/api/json-slow', { method: 'POST', body: JSON.stringify({ a: 1 }), headers: { 'content-type': 'application/json' } });
    assert.deepEqual([js.status, js.body.error?.code], [503, 'request_timeout'], 'POST ِ JSON هم مهلت دارد');
  } finally { release(); await s.close(); }

  for (const opts of [{ ms: 0 }, { ms: 1, floorMs: 1000 }]) {
    const o = await serve(opts, (app) => slow(app, '/api/slow'));
    try {
      assert.equal((await call(o.base, '/api/slow')).status, 200, `ms=${opts.ms}: خاموش یا کفِ ${FLOOR_MS}ms`);
    } finally { await o.close(); }
  }
  assert.equal(FLOOR_MS, 5_000);
  assert.ok(LONG_POLL_MS > 25_000, 'بالای بلندترین long-poll ِ امروز (۲۵ ثانیه)');
});

test('برنامهٔ واقعی: `REQUEST_TIMEOUT_MS` خاموش ⇒ میان‌افزارِ خاموش؛ روشن ⇒ همان میان‌افزار روی `/api`', async () => {
  const config = require('../src/config');
  assert.equal(config.http.requestTimeoutMs, 0, 'پیش‌فرض خاموش');
  assert.equal(requestDeadline().name, 'requestDeadlineOff');
  assert.equal(requestDeadline({ ms: 45_000 }).name, 'requestDeadline');
  const src = require('node:fs').readFileSync(require.resolve('../src/app.js'), 'utf8');
  assert.match(src, /app\.use\('\/api', require\('\.\/middleware\/deadline'\)\.requestDeadline\(\)\)/);
});

const pgOnly = !String(process.env.TEST_DATABASE_URL || '').startsWith('postgres');
test('PostgreSQL: پرس‌وجوی بلند سرِ سقف می‌افتد ⇒ ۵۰۳ ِ db_timeout، استخر سالم، مهاجرت‌ها بی سقف', { skip: pgOnly && 'PGlite یک اتصال است و گزینهٔ استخر ندارد' }, async () => {
  await h.start();   //  همهٔ مهاجرت‌ها با سقفِ ۴۰۰ms ِ استخر — باید بی سقف بروند
  try {
    const db = require('../src/db');
    assert.equal((await db.one('SHOW statement_timeout')).statement_timeout, '400ms', 'پس از مهاجرت RESET ⇒ همان سقفِ استخر');
    const t = Date.now();
    await assert.rejects(db.query('SELECT pg_sleep(3)'), (e) => e.code === '57014');
    assert.ok(Date.now() - t < 2000, 'سرِ سقف، نه پس از ۳ ثانیه');
    assert.equal((await db.one('SELECT 1 AS n')).n, 1, 'اتصال به استخر برگشت');
    await assert.rejects(db.tx(async (c) => { await c.query('SELECT 1'); await sleep(1200); await c.query('SELECT 2'); }));
    assert.equal((await db.one('SELECT 2 AS n')).n, 2, 'تراکنشِ رهاشده بسته شد و استخر سالم است');

    const s = await serve({ ms: 0 }, (app) => app.get('/api/q', async (req, res) => res.json(await db.one('SELECT pg_sleep(2)'))));
    try {
      const r = await call(s.base, '/api/q');
      assert.deepEqual([r.status, r.body.error.code, r.retry], [503, 'db_timeout', '2']);
    } finally { await s.close(); }
  } finally { await h.stop(); }
});
