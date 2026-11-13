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
 *  • `bearerToken` (‎lib/bearer.js‎): پارسرِ خطی به جای `/^Bearer\s+(.+)$/i` (polynomial ReDoS) — آزمونِ تفاضلی با
 *    regex ِ پیشین، سقفِ طول و زمانِ ورودیِ بدخواه؛ `tokenOf` ِ همگام‌سازی و `bearer` ِ میان‌افزار هم همان.
 *  • `mediaBytes`/`bufferOf` (‎lib/raw-body.js‎) در `putMedia` ِ پشتیبانی (این‌جا)، گفت‌وگو (`shop-chat.test.js`) و
 *    `putEvidence` (`market-trust.test.js`): type confusion.
 *  • `image-pipeline.run`: کارِ مرحله از `Map` ِ بسته (unvalidated dynamic method call).
 *  • `oneLine` (‎lib/log-redact.js‎) در لاگِ «شناسهٔ دستگاهِ ناجور»: `sync-device-bind.test.js` (log injection).
 *  • `/auth/reauth` و وصلِ گوگل (user-controlled bypass): `reauth.test.js` و `google-link.test.js`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const { spawnSync } = require('child_process');
const bank = require('../src/lib/image-bank');
const { rawBody } = require('../src/lib/raw-body');

const code = (fn) => { try { fn(); } catch (e) { return e.code; } return 'no-error'; };

test('parseCsv: نقل‌قول، CRLF، BOM، سطرِ خالی و row_no همان پیشین', () => {
  const csv = '\ufeffBarcode, Name ,image_path\r\n"6260001", "برنج، ممتاز" ,a.jpg\r\n\r\n,,\n"say ""hi""",x,b.jpg';
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

//  «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): `verifiedPhone` ⇐ به‌جایش `verifiedEmail` (همان سنجش، هویتِ ایمیل)
test('verifiedEmail: بی بلیت و با نشستِ تهی/بی‌شکل ⇒ null (همان پیشین)', async () => {
  const sf = require('../src/lib/storefront');
  for (const session of ['', undefined, null, 'not-a-session', ['sc1.x']]) {
    assert.equal(await sf.verifiedEmail('', session), null);
    assert.equal(await sf.verification('', session), null);
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

//  ── polynomial ReDoS: سرآیندِ Authorization ───────────────────────────────
const { bearerToken, MAX_HEADER } = require('../src/lib/bearer');
const oldBearer = (h) => { const m = /^Bearer\s+(.+)$/i.exec(h.trim()); return m ? m[1].trim() : null; };

test('bearerToken: همان خروجیِ `/^Bearer\\s+(.+)$/i` ِ پیشین برای هر سرآیند (آزمونِ تفاضلی)', () => {
  const fixed = ['Bearer abc', 'bearer abc', 'BEARER  abc  ', '  Bearer\tabc', 'Bearer', 'Bearer ', 'Bearerabc', 'Basic abc',
    'Bearer a b', 'Bearer\nabc', 'Bearer abc\ndef', 'Bearer abc\r', 'Bearer \u2028x', 'Bearer x\u2029y', 'Bearer \u00a0x',
    '\ufeffBearer x', 'Bearer\u3000x', 'Bear er x', '', ' ', 'Bearer vsk_0123456789abcdef', 'Token abc', 'xBearer abc'];
  for (const s of fixed) assert.equal(bearerToken(s), oldBearer(s), JSON.stringify(s));
  //  ورودیِ تصادفیِ تکرارپذیر از نویسه‌های مرزی
  const alpha = ['B', 'b', 'e', 'E', 'a', 'r', 'R', ' ', '\t', '\n', '\r', '\u2028', '\u00a0', '\ufeff', 'x', '\u0130', '\u212a', '\u017f', '.', '='];
  let seed = 20261108;
  const rnd = (n) => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed % n; };
  const pick = (k) => Array.from({ length: k }, () => alpha[rnd(alpha.length)]).join('');
  for (let i = 0; i < 50_000; i++) {
    const s = pick(rnd(3)) + ['Bearer', 'bearer', 'BEARER', 'bEaReR', 'Bear', ''][rnd(6)] + pick(rnd(9));
    assert.equal(bearerToken(s), oldBearer(s), JSON.stringify(s));
  }
});

test('⛔ bearerToken: ورودیِ بدخواه زمانِ خطی؛ سرآیندِ بلندتر از سقف و غیرِرشته ⇒ null', () => {
  const evil = `Bearer${' '.repeat(MAX_HEADER - 10)}x\ny`;
  const t0 = process.hrtime.bigint();
  for (let i = 0; i < 20; i++) assert.equal(bearerToken(evil), null);
  const ms = Number(process.hrtime.bigint() - t0) / 1e6;
  assert.ok(ms < 200, `۲۰ بار ${ms.toFixed(1)}ms`);   //  regex ِ پیشین: هر بار ده‌ها میلی‌ثانیه
  assert.equal(bearerToken(`Bearer ${'a'.repeat(MAX_HEADER)}`), null);
  assert.equal(bearerToken(`Bearer ${'a'.repeat(64)}`), 'a'.repeat(64));
  for (const v of [undefined, null, 7, ['Bearer x'], { toString: () => 'Bearer x' }]) assert.equal(bearerToken(v), null);
});

test('tokenOf ِ همگام‌سازی و bearer ِ میان‌افزار: همان پیشین؛ بی Bearer ⇒ ?token=', () => {
  const { tokenOf } = require('../src/lib/sync-v1-auth');
  const { bearer } = require('../src/middleware/auth');
  assert.equal(tokenOf({ headers: { authorization: 'Bearer  tok-1 ' }, query: { token: 'q' } }), 'tok-1');
  assert.equal(tokenOf({ headers: { authorization: 'Basic x' }, query: { token: ' q-2 ' } }), 'q-2');
  assert.equal(tokenOf({ headers: {}, query: { token: ['a'] } }), '');
  assert.equal(tokenOf({ headers: { authorization: `Bearer${' '.repeat(5000)}x\ny` }, query: {} }), '');
  assert.equal(bearer({ headers: { authorization: 'bearer tok-3' } }), 'tok-3');
  assert.equal(bearer({ headers: {} }), null);
});

//  ── type confusion: بایتِ رسانه ───────────────────────────────────────────
const { bufferOf, mediaBytes } = require('../src/lib/raw-body');

test('bufferOf: فقط Buffer؛ رشته، آرایه، شیء، null، Uint8Array و بی مقدار ⇒ جایگزین', () => {
  const buf = Buffer.from([1, 2, 3]);
  assert.equal(bufferOf(buf), buf);
  assert.equal(bufferOf(buf, null), buf);
  for (const v of ['abc', ['a'], { length: 5 }, null, undefined, 7, new Uint8Array([1])]) {
    assert.equal(bufferOf(v, null), null, `${typeof v}`);
    const out = bufferOf(v);
    assert.ok(Buffer.isBuffer(out) && out.length === 0);
    assert.equal(code(() => mediaBytes(v)), 'bad_media', `${typeof v}`);
  }
  assert.equal(mediaBytes(buf), buf);
  assert.equal(code(() => mediaBytes(Buffer.alloc(0))), 'bad_media');
});

test('⛔ support.putMedia: بایتِ غیرِ Buffer ⇒ ۴۰۰ bad_media پیش از هر نوشتن', async () => {
  const support = require('../src/lib/support');
  for (const buf of ['abcd', ['a', 'b'], { length: 5 }, null, new Uint8Array([0xff, 0xd8])]) {
    await assert.rejects(support.putMedia({ threadId: 'sth_x', uploader: 'user', mime: 'image/jpeg', buf }),
      (e) => e.status === 400 && e.code === 'bad_media', JSON.stringify(buf));
  }
});

//  ── unvalidated dynamic method call: مرحله‌های عکس ────────────────────────
test('⛔ image-pipeline.run: نامِ prototype ⇒ ۴۰۰ unknown_step؛ مرحله‌های محلی همان پیشین', () => {
  const pipeline = require('../src/lib/image-pipeline');
  for (const id of ['__proto__', 'constructor', 'toString', 'hasOwnProperty', 'valueOf', '', 'VALIDATE']) {
    assert.equal(code(() => pipeline.run(id, Buffer.from('x'))), 'unknown_step', id);
  }
  assert.equal(code(() => pipeline.run('enhance', Buffer.from('x'))), 'step_unavailable');
  const b = Buffer.from('hello');
  assert.equal(pipeline.run('dedupe_hash', b), require('crypto').createHash('sha256').update(b).digest('hex'));
});

//  ── log injection ─────────────────────────────────────────────────────────
test('oneLine: CR/LF ِ خام هرگز؛ سقفِ طول؛ مقدارِ عادی همان', () => {
  const { oneLine } = require('../src/lib/log-redact');
  assert.equal(oneLine('dev-1'), 'dev-1');
  assert.equal(oneLine('a\r\n[sync] ساختگی\nb'), 'a[sync] ساختگیb');
  assert.equal(oneLine(undefined), '');
  assert.equal(oneLine('x'.repeat(500)).length, 120);
});
