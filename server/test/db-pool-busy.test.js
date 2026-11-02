'use strict';
/**
 * PERF-04 (§۱۵.۹ بندِ ۴، §۲۹.۶ بندِ ۷): استخرِ پرِ PostgreSQL ⇒ پاسخِ کنترل‌شده.
 *
 * درخواستی که تا `PG_CONNECT_TIMEOUT_MS` اتصالی از استخر نگیرد، پیش‌تر ۵۰۰ ِ «خطای داخلیِ سرور»
 * می‌گرفت (پیامِ خامِ pg-pool). حالا ۵۰۳ ِ `db_busy` با `Retry-After` است: برنامه‌ها می‌دانند
 * «شلوغ است، کمی بعد» (retry ِ محدود)، نه «خراب است». پس از آزاد شدنِ استخر همان درخواست بی
 * راه‌اندازیِ دوباره کار می‌کند. سنجهٔ زیرِ بارِ واقعی: نمایهٔ `overload` ِ `scripts/load-test.mjs`.
 * PGlite تک‌اتصال و بی استخر است ⇒ فقط روی PostgreSQL.
 */
process.env.PG_POOL_MAX = '1';
process.env.PG_CONNECT_TIMEOUT_MS = '300';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');

const PG = !db.isPglite();

test.before(async () => { if (PG) await h.start(); });
test.after(async () => { if (PG) await h.stop(); });

test('استخرِ پر ⇒ ۵۰۳ ِ db_busy با Retry-After، و پس از آزاد شدن دوباره ۲۰۰', { skip: !PG && 'PGlite: استخرِ اتصال ندارد' }, async () => {
  const held = await db.getPool().connect();   //  تنها اتصالِ استخر در دستِ آزمون
  let busy;
  try {
    const t0 = Date.now();
    busy = await h.get('/api/store/hich-dokani-nist');
    assert.ok(Date.now() - t0 < 5000, 'منتظرِ ابدی نماند — مهلتِ استخر');
  } finally {
    held.release();
  }
  assert.equal(busy.status, 503, JSON.stringify(busy.body));
  assert.equal(busy.body.error.code, 'db_busy');
  assert.match(busy.body.error.message, /شلوغ/);
  assert.ok(!/timeout exceeded/.test(JSON.stringify(busy.body)), 'پیامِ خامِ pg بیرون نمی‌رود');
  assert.equal(busy.headers.get('retry-after'), '2');

  const after = await h.get('/api/store/hich-dokani-nist');
  assert.equal(after.status, 404, 'پس از آزاد شدنِ استخر همان مسیر پاسخِ عادیِ خودش را می‌دهد');
});

test('خطای دیگرِ پایگاه همان ۵۰۰ ِ internal می‌ماند', async () => {
  const { errorHandler } = require('../src/middleware/errors');
  const out = {};
  const res = {
    set(k, v) { (out.headers ||= {})[k] = v; return this; },
    status(s) { out.status = s; return this; },
    json(b) { out.body = b; return this; },
  };
  const orig = console.error;
  console.error = () => {};
  try {
    errorHandler(new Error('relation "x" does not exist'), { method: 'GET', originalUrl: '/api/x' }, res);
  } finally { console.error = orig; }
  assert.equal(out.status, 500);
  assert.equal(out.body.error.code, 'internal');
  assert.equal(out.headers, undefined);
});
