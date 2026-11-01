'use strict';
/**
 * برداشتنِ `wrap()` از مسیرها رفتارِ خطا را عوض نمی‌کند.
 *
 * Express 5 (router 2) خودش promiseِ ردشدهٔ دستگیره را به `next(err)`
 * می‌دهد؛ پس `wrap(async …)` و `async …` باید همان کدِ وضعیت، همان بدنهٔ
 * پاسخ و همان سطرِ لاگ را بسازند. اگر روزی Express به ۴ برگردد، این
 * آزمون می‌شکند (در ۴ promiseِ ردشده هرگز به errorHandler نمی‌رسد).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const express = require('express');
const { errorHandler, notFoundHandler, badRequest, conflict } = require('../src/middleware/errors');

const ROOT = path.join(__dirname, '..');

test('نسخهٔ نصب‌شده و lock: Express 5 با router 2', () => {
  const pkg = require(path.join(ROOT, 'package.json'));
  const lock = require(path.join(ROOT, 'package-lock.json'));
  assert.match(pkg.dependencies.express, /^\^?5\./);
  assert.match(lock.packages['node_modules/express'].version, /^5\./);
  assert.match(lock.packages['node_modules/router'].version, /^2\./);
  assert.match(require('express/package.json').version, /^5\./);
});

//  همان تعریفی که از ۱۳ فایل برداشته شد
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const handlers = {
  '/bad': async () => { throw badRequest('ورودی نادرست است', 'bad_input'); },
  '/data': async () => { const e = conflict('فقط ۱ عدد', 'stock'); e.data = { lines: [1] }; throw e; },
  '/boom': async () => { throw new Error('secret host db.internal'); },
  '/step': async () => { const e = new Error('x'); e.step = 'charge'; throw e; },
  '/sync': () => { throw badRequest('همزمان'); },
  '/late': async () => { await new Promise((r) => setImmediate(r)); throw conflict('دیر'); },
  '/ok': async (req, res) => { res.json({ ok: true }); },
};

function build(useWrap) {
  const app = express();
  for (const [p, h] of Object.entries(handlers)) app.get(p, useWrap ? wrap(h) : h);
  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}

async function run(app) {
  const server = app.listen(0);
  await new Promise((r) => server.once('listening', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const out = {};
  const orig = console.error;
  try {
    for (const p of Object.keys(handlers)) {
      const logs = [];
      console.error = (...a) => logs.push(a);
      const res = await fetch(base + p);
      const body = await res.json();
      console.error = orig;
      //  ref تصادفی است؛ فقط شکلش را می‌سنجیم
      if (body.error && body.error.ref) {
        assert.ok(body.error.message.includes(body.error.ref));
        body.error.message = body.error.message.replace(body.error.ref, '<ref>');
        body.error.ref = '<ref>';
      }
      out[p] = {
        status: res.status,
        body,
        logs: logs.map(([head, method, url, err]) => [head.replace(/^\[error\] \S+?(?= @|$)/, '[error] <ref>'), method, url, err && err.message]),
      };
    }
  } finally {
    console.error = orig;
    server.close();
  }
  return out;
}

test('wrap() و دستگیرهٔ async خام: همان کدِ وضعیت، پیام و لاگ', async () => {
  const before = await run(build(true));
  const after = await run(build(false));
  assert.deepEqual(after, before);

  //  و آن رفتار همان است که انتظار داریم (نه این‌که هر دو یک‌جور خراب باشند)
  assert.equal(after['/bad'].status, 400);
  assert.deepEqual(after['/bad'].body, { error: { code: 'bad_input', message: 'ورودی نادرست است' } });
  assert.deepEqual(after['/data'].body, { error: { lines: [1], code: 'stock', message: 'فقط ۱ عدد' } });
  assert.equal(after['/boom'].status, 500);
  assert.deepEqual(after['/boom'].body, { error: { code: 'internal', message: 'خطای داخلی سرور (کدِ پیگیری: <ref>)', ref: '<ref>' } });
  assert.deepEqual(after['/boom'].logs, [['[error] <ref>', 'GET', '/boom', 'secret host db.internal']]);
  assert.deepEqual(after['/step'].logs, [['[error] <ref> @charge', 'GET', '/step', 'x']]);
  assert.equal(after['/sync'].status, 400);
  assert.equal(after['/late'].status, 409);
  assert.deepEqual(after['/ok'], { status: 200, body: { ok: true }, logs: [] });
  for (const p of ['/bad', '/data', '/sync', '/late']) assert.deepEqual(after[p].logs, [], p);
});
