'use strict';
/**
 * رفعِ هشدارهای CodeQL ِ بیرون از کدِ پمپ — رفتار همان پیشین است؛ این آزمون‌ها همان را می‌سنجند.
 *
 *  • `parseCsv` (‎lib/image-bank.js‎): حلقه با طولِ سنجیده (loop bound injection) و ردیف با
 *    `Object.fromEntries` (remote property injection)؛ خروجی برای هر CSV ِ واقعی همان است.
 *  • `rawBody` (‎lib/raw-body.js‎): نگهبانِ نوعِ بدنهٔ خام با typeof/Array.isArray (type confusion).
 *  • `verifiedPhone` (‎lib/storefront.js‎): احرازِ نشست بی شرطِ بیرونیِ «نشست هست؟» (user-controlled bypass).
 *  • `scripts/send-test-sms.js`: از کلید هیچ چیز، حتی طولش، چاپ نمی‌شود (clear-text logging).
 *  • CR/LF در لاگِ درخواست: `log-redact.test.js`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const { spawnSync } = require('child_process');
const bank = require('../src/lib/image-bank');
const { rawBody } = require('../src/lib/raw-body');

const code = (fn) => { try { fn(); } catch (e) { return e.code; } return 'no-error'; };

test('parseCsv: نقل‌قول، CRLF، BOM، سطرِ خالی و row_no همان پیشین', () => {
  const csv = '﻿Barcode, Name ,image_path\r\n"6260001", "برنج، ممتاز" ,a.jpg\r\n\r\n,,\n"say ""hi""",x,b.jpg';
  assert.deepEqual(bank.parseCsv(csv), [
    { row_no: 2, barcode: '6260001', name: 'برنج، ممتاز', image_path: 'a.jpg' },
    { row_no: 5, barcode: 'say "hi"', name: 'x', image_path: 'b.jpg' },
  ]);
  assert.deepEqual(bank.parseCsv(''), []);
  assert.deepEqual(bank.parseCsv(null), []);
  assert.deepEqual(bank.parseCsv('a,b'), []);
});

test('⛔ parseCsv: سرآیندِ `__proto__`/`constructor`/`prototype` کنار؛ ستونِ تکراری «آخری می‌بَرد»؛ شیءِ ساده', () => {
  const rows = bank.parseCsv('__proto__,constructor,prototype,name,NAME,row_no,\nx,y,z,a,b,9,q');
  assert.equal(rows.length, 1);
  const r = rows[0];
  assert.deepEqual(Object.keys(r), ['row_no', 'name']);
  assert.equal(r.name, 'b');
  assert.equal(r.row_no, '9');                       //  همان پیشین: ستونِ row_no مقدار را بازمی‌نویسد
  assert.equal(Object.getPrototypeOf(r), Object.prototype);
  assert.equal(Object.prototype.hasOwnProperty.call(r, '__proto__'), false);
  assert.equal(({}).x, undefined, 'هیچ چیز به Object.prototype نرسید');
  assert.equal(JSON.stringify(r), '{"row_no":"9","name":"b"}');
});

test('parseCsv: سقفِ طول فقط ورودیِ دست‌کاری‌شده را رد می‌کند؛ رشتهٔ هم‌اندازهٔ بدنهٔ ۲ مگابایتی می‌گذرد', () => {
  const max = 2 * 1024 * 1024;
  const big = `name\n${'x'.repeat(max - 5)}`;
  assert.equal(big.length, max);
  assert.equal(bank.parseCsv(big).length, 1);
  assert.equal(code(() => bank.parseCsv(`${big}y`)), 'csv_too_large');
  //  آرایه‌ای از شیء که String() اش از هر بدنه‌ای بلندتر است
  assert.equal(code(() => bank.parseCsv(new Array(200_000).fill({}))), 'csv_too_large');
  //  آرایهٔ کوچک مثلِ پیشین با String() خوانده می‌شود
  assert.deepEqual(bank.parseCsv(['name\nx']), [{ row_no: 2, name: 'x' }]);
});

test('rawBody: فقط Buffer؛ رشته، آرایه، شیء، null و بی بدنه ⇒ جایگزین', () => {
  const buf = Buffer.from([1, 2, 3]);
  assert.equal(rawBody({ body: buf }), buf);
  for (const body of ['abc', ['a', 'b'], { length: 5 }, null, undefined, 7, new Uint8Array([1])]) {
    const out = rawBody({ body });
    assert.ok(Buffer.isBuffer(out) && out.length === 0, `${typeof body}`);
    assert.equal(rawBody({ body }, null), null);
  }
});

test('verifiedPhone: بی بلیت و با نشستِ تهی/بی‌شکل ⇒ null (همان پیشین)', async () => {
  const sf = require('../src/lib/storefront');
  for (const session of ['', undefined, null, 'not-a-session', ['sc1.x']]) {
    assert.equal(await sf.verifiedPhone('', session), null);
  }
});

test('⛔ send-test-sms: کلید فقط «تنظیم شده»؛ نه مقدار، نه طول', () => {
  const key = 'test-sms-key-not-real';
  const out = spawnSync(process.execPath, [path.join(__dirname, '..', 'scripts', 'send-test-sms.js'), '0790000000'], {
    env: {
      ...process.env, NODE_ENV: 'test', OTP_PROVIDER: 'sms', SMS_API_URL: 'http://127.0.0.1:9/send',
      SMS_API_KEY: key, DATABASE_URL: 'pglite:memory',
    },
    encoding: 'utf8', timeout: 60_000,
  });
  const text = `${out.stdout}\n${out.stderr}`;
  assert.match(out.stdout, /کلید {6}: تنظیم شده\n/);
  assert.ok(!text.includes(key), 'کلید در خروجی');
  assert.ok(!text.includes(`${key.length} نویسه`) && !/\(\d+ نویسه\)/.test(text), 'طولِ کلید در خروجی');
});
