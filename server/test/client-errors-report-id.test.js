'use strict';
/**
 * OPS-03 — گزارشِ خطای برنامهٔ نصبی با شناسهٔ خودش (`POST /api/errors`، `report_id`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) `report_id` ِ درست (`cer_<ULID>`) شناسهٔ ردیف است و در پاسخ برمی‌گردد — همان کدی
 *      که برنامه به کاربر نشان داده، در پنل پیدا می‌شود.
 *   ۲) فرستادنِ دوباره با همان شناسه (پاسخ گم شد) ردیفِ دوم نمی‌سازد و اولی را عوض نمی‌کند.
 *   ۳) شناسهٔ ناجور ⇒ شناسهٔ سرور، مثلِ پیش؛ بی حساب هم ثبت می‌شود و پوشاندن سرِ جایش است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, many } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const send = (body) => h.api('POST', '/api/errors', {
  headers: { 'X-App': 'shop', 'X-Device': 'dev-crash-1', 'X-App-Version': '2.14.4' }, body,
});

test('report_id ِ برنامه شناسهٔ ردیف است و فرستادنِ دوباره ردیفِ دوم نمی‌سازد', async () => {
  const id = 'cer_01JB2Z3K4M5N6P7Q8R9S0T1V2W';
  const first = await send({ report_id: id, message: 'IllegalStateException: بوم', stack: 'at A.b(A.kt:1)', log_tail: 'count=1' });
  assert.equal(first.status, 202, JSON.stringify(first.body));
  assert.deepEqual(first.body, { ok: true, id });
  const again = await send({ report_id: id, message: 'دیگر', stack: '', log_tail: 'count=9' });
  assert.equal(again.status, 202);
  assert.equal(again.body.id, id);
  const rows = await many('SELECT * FROM client_errors WHERE id = $1', [id]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].message, 'IllegalStateException: بوم', 'ردیفِ اول دست نخورد');
  assert.equal(rows[0].log_tail, 'count=1');
  assert.equal(rows[0].app, 'shop');
  assert.equal(rows[0].device_id, 'dev-crash-1');
  assert.equal(rows[0].app_version, '2.14.4');
  assert.equal(rows[0].account_id, '', 'بی حساب');
});

test('شناسهٔ ناجور ⇒ شناسهٔ سرور؛ پوشاندن سرِ جایش', async () => {
  for (const bad of ['cer_bad', 'cer_01jb2z3k4m5n6p7q8r9s0t1v2w', 'cer_01JB2Z3K4M5N6P7Q8R9S0T1V2', "cer_01JB2Z3K4M5N6P7Q8R9S0T1V2W' OR 1=1", 42, null]) {
    const r = await send({ report_id: bad, message: `برای ali@example.com ${String(bad)}` });
    assert.equal(r.status, 202, JSON.stringify(r.body));
    assert.match(r.body.id, /^cer_[0-9a-f]{24}$/, String(bad));
    const row = await one('SELECT message FROM client_errors WHERE id = $1', [r.body.id]);
    assert.ok(row, String(bad));
    assert.ok(!row.message.includes('ali@example.com'));
  }
  //  بی report_id همان رفتارِ پیشین
  const plain = await send({ message: 'x' });
  assert.equal(plain.status, 202);
  assert.match(plain.body.id, /^cer_[0-9a-f]{24}$/);
});
