'use strict';
/**
 * انبارِ مشترکِ سقفِ نرخ (`RATE_LIMIT_STORE=pg`، مهاجرتِ ۲۱۵؛ docs/SCALING-fa.md) — همان قرارداد که انبارِ حافظه:
 * سقف دقیق، ردشده شمرده نمی‌شود، `Retry-After`، `refund`، کلیدِ درهم (بی ایمیل/IP در پایگاه)، و درخواست‌های
 * هم‌زمان از سقف نمی‌گذرند. پایگاه در دسترس نباشد ⇒ همان درخواست با حافظه (نه ۵۰۰).
 * دو نمونهٔ واقعی روی یک پایگاه: `cluster-instances.test.js`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const h = require('./helpers');
const config = require('../src/config');
const db = require('../src/db');
const { rateLimit, refund, resetAll, sweepStore, storeKey } = require('../src/middleware/ratelimit');
const { errorHandler } = require('../src/middleware/errors');

let server = null;
let base = '';
const store0 = config.rateLimit.store;

test.before(async () => {
  await h.start();
  const app = express();
  app.get('/a', rateLimit({ max: 3, windowMs: 60_000, keyPrefix: 'rs-a' }), (req, res) => res.json({ ok: true }));
  app.get('/k', rateLimit({ max: 2, windowMs: 60_000, keyPrefix: 'rs-k', key: (req) => String(req.query.email || '') }), (req, res) => res.json({ ok: true }));
  app.get('/r', rateLimit({ max: 1, windowMs: 60_000, keyPrefix: 'rs-r', refundable: true }), (req, res) => {
    if (req.query.undo) refund(req, 'rs-r');
    res.json({ ok: true });
  });
  app.use(errorHandler);
  await new Promise((r) => { server = app.listen(0, '127.0.0.1', r); });
  base = `http://127.0.0.1:${server.address().port}`;
});
test.after(async () => {
  config.rateLimit.store = store0;
  await new Promise((r) => server.close(r));
  await h.stop();
});
test.beforeEach(async () => {
  config.rateLimit.store = 'pg';
  resetAll();
  await db.query('DELETE FROM rate_limit_hits');
});

const get = async (p) => { const r = await fetch(base + p); await r.arrayBuffer(); return r; };

test('سقف دقیق؛ ردشده شمرده نمی‌شود؛ Retry-After', async () => {
  const codes = [];
  for (let i = 0; i < 5; i++) codes.push((await get('/a')).status);
  assert.deepEqual(codes, [200, 200, 200, 429, 429]);
  const r = await get('/a');
  const ra = Number(r.headers.get('retry-after'));
  assert.ok(ra > 0 && ra <= 62, `Retry-After ${ra}`);
  //  ردشده‌ها شمرده نشدند: جمعِ ردیف‌ها همان ۳
  const n = await db.one("SELECT COALESCE(sum(n),0)::int AS n FROM rate_limit_hits WHERE key LIKE 'rs-a:%'");
  assert.equal(n.n, 3);
});

test('کلیدِ دلخواه جدا شمرده و درهم ذخیره می‌شود', async () => {
  assert.equal((await get('/k?email=a@x.test')).status, 200);
  assert.equal((await get('/k?email=a@x.test')).status, 200);
  assert.equal((await get('/k?email=a@x.test')).status, 429);
  assert.equal((await get('/k?email=b@x.test')).status, 200);
  //  ردیف = (کلید، برشِ یک‌ثانیه‌ای): دو درخواستِ همان کلید دو سوی مرزِ ثانیه دو ردیفِ همان کلیدند (CI ِ #1020 روی
  //  PostgreSQL: ۳ ردیف برای ۲ کلید) ⇒ کلیدهای جدا شمرده می‌شوند؛ «جدا شمرده» را همان ۴۲۹ ِ a و ۲۰۰ ِ b می‌سنجد
  const keys = (await db.many("SELECT DISTINCT key FROM rate_limit_hits WHERE key LIKE 'rs-k:%'")).map((r) => r.key);
  assert.equal(keys.length, 2);
  for (const k of keys) assert.ok(!k.includes('@') && !k.includes('x.test'), `کلیدِ خام در پایگاه: ${k}`);
  assert.ok(keys.includes(storeKey('rs-k:a@x.test')));
});

test('refund همان سهم را پس می‌دهد', async () => {
  assert.equal((await get('/r?undo=1')).status, 200);
  await new Promise((r) => setTimeout(r, 50));   //  پس دادن پس از پاسخ، بی انتظار
  assert.equal((await get('/r')).status, 200, 'سهمِ پس‌داده دوباره پذیرفته شد');
  assert.equal((await get('/r')).status, 429);
});

test('درخواست‌های هم‌زمان از سقف نمی‌گذرند', async () => {
  //  هم‌زمان‌ها در **یک** برش (۱ ثانیه برای پنجرهٔ ۶۰ثانیه‌ای) زیرِ قفلِ همان ردیف‌اند؛ مرزِ دو برش را کنار می‌گذاریم
  //  (آن‌جا چند میلی‌ثانیه می‌تواند یکی دو درخواستِ بیشتر بپذیرد — docs/SCALING-fa.md)
  while (Date.now() % 1000 > 400) await new Promise((r) => setTimeout(r, 20));
  const all = await Promise.all(Array.from({ length: 20 }, () => get('/a')));
  const ok = all.filter((r) => r.status === 200).length;
  assert.equal(ok, 3, `از ۲۰ درخواستِ هم‌زمان ${ok} پذیرفته شد`);
});

test('پاک‌سازی: برش‌های گذشته از پنجره می‌روند', async () => {
  await get('/a');
  await db.query("INSERT INTO rate_limit_hits (key, slot, n, expires_at) VALUES ('old:x', 1, 4, 1)");
  const removed = await sweepStore(Date.now());
  assert.equal(removed, 1);
  assert.equal((await db.one("SELECT count(*)::int AS n FROM rate_limit_hits WHERE key LIKE 'rs-a:%'")).n, 1);
});

test('پایگاه در دسترس نیست ⇒ سقفِ همین نمونه (حافظه)، نه ۵۰۰', async () => {
  const real = db.query;
  const errs = console.error;
  console.error = () => {};
  db.query = async () => { throw new Error('connection refused (آزمون)'); };
  try {
    const codes = [];
    for (let i = 0; i < 4; i++) codes.push((await get('/a')).status);
    assert.deepEqual(codes, [200, 200, 200, 429]);
  } finally {
    db.query = real;
    console.error = errs;
  }
});

test('انبارِ حافظه (پیش‌فرضِ تک‌نمونه) به پایگاه دست نمی‌زند', async () => {
  config.rateLimit.store = 'memory';
  for (let i = 0; i < 4; i++) await get('/a');
  assert.equal((await db.one('SELECT count(*)::int AS n FROM rate_limit_hits')).n, 0);
  assert.equal(store0, process.env.CLUSTER_ENABLED ? 'pg' : 'memory', 'پیش‌فرض');
});
