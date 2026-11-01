'use strict';
/**
 * NTF-02 — هزینهٔ پیامِ مشتری و سقفِ روزانهٔ هر دکان و هر کانال (`lib/notify-cost.js`، خالص).
 * بخش‌بندیِ پیامک (§۴.۳)، قیمت و سقفِ پیش‌فرض و env، و شمردنِ هزینهٔ ثبت‌شده.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const C = require('../src/lib/notify-cost');

test('NTF-02: بخشِ پیامک — لاتین ۱۶۰/۱۵۳ با نویسهٔ دوتاییِ GSM، یونیکد ۷۰/۶۷', () => {
  assert.equal(C.segments(''), 1);
  assert.equal(C.segments('a'.repeat(160)), 1);
  assert.equal(C.segments('a'.repeat(161)), 2);
  assert.equal(C.segments('a'.repeat(306)), 2);
  assert.equal(C.segments('a'.repeat(307)), 3);
  assert.equal(C.segments('a'.repeat(159) + '{'), 2, '{ دو جا می‌گیرد');
  assert.equal(C.segments('س'.repeat(70)), 1);
  assert.equal(C.segments('س'.repeat(71)), 2);
  assert.equal(C.segments('س'.repeat(134)), 2);
  assert.equal(C.segments('س'.repeat(135)), 3);
  //  یک حرفِ غیرِ ASCII کلِ متن را یونیکد می‌کند
  assert.equal(C.segments('a'.repeat(100) + '«'), 2);
});

test('NTF-02: قیمت و سقفِ پیش‌فرض — فقط پیامک پولی است؛ پیامکِ دوبخشی دو برابر', () => {
  const env = {};
  assert.equal(C.costMicros('push', 'x', env), 0);
  assert.equal(C.costMicros('whatsapp', 'x', env), 0);
  assert.equal(C.costMicros('telegram', 'x', env), 0);
  assert.equal(C.costMicros('sms', 'سفارش آماده است', env), 446_000);
  assert.equal(C.costMicros('sms', 'س'.repeat(71), env), 892_000);
  assert.equal(C.costMicros('fax', 'x', env), 0);
  assert.equal(C.capMicros('sms', env), 2_230_000);
  assert.equal(C.capMicros('whatsapp', env), 1_000_000);
});

test('NTF-02: env قیمت و سقف را عوض می‌کند؛ مقدارِ نادرست ⇒ پیش‌فرض؛ سقفِ صفر ⇒ هیچ پیامِ پولی', () => {
  const env = {
    CUSTOMER_NOTIFY_COST_MICROS_WHATSAPP: '30000',
    CUSTOMER_NOTIFY_COST_MICROS_SMS: '-5',
    CUSTOMER_NOTIFY_DAILY_CAP_MICROS_SMS: '0',
    CUSTOMER_NOTIFY_DAILY_CAP_MICROS_WHATSAPP: '1.5',
  };
  assert.equal(C.costMicros('whatsapp', 'x', env), 30_000);
  assert.equal(C.costMicros('sms', 'x', env), 446_000, 'منفی ⇒ پیش‌فرض');
  assert.equal(C.capMicros('whatsapp', env), 1_000_000, 'اعشار ⇒ پیش‌فرض');
  assert.deepEqual(C.allows('sms', 'x', 0, env), { ok: false, costMicros: 446_000, capMicros: 0 });
  //  کانالِ بی هزینه هرگز به سقف نمی‌خورد
  assert.equal(C.allows('push', 'x', 99_000_000, env).ok, true);
});

test('NTF-02: سقف با هزینهٔ همین پیام سنجیده می‌شود — مرز پذیرفته است', () => {
  const env = {};
  assert.equal(C.allows('sms', 'x', 0, env).ok, true);
  assert.equal(C.allows('sms', 'x', 2_230_000 - 446_000, env).ok, true, 'دقیقاً تا سقف');
  assert.equal(C.allows('sms', 'x', 2_230_000 - 446_000 + 1, env).ok, false);
  assert.equal(C.allows('sms', 'س'.repeat(71), 2_230_000 - 446_000, env).ok, false, 'پیامِ دوبخشی جا ندارد');
  assert.equal(C.allows('sms', 'x', -100, env).ok, true, 'هزینهٔ منفی صفر است');
});

test('NTF-02: هزینهٔ ثبت‌شده فقط از «پذیرفت» ِ همان کانال؛ ردیفِ پیشین بی costMicros ⇒ قیمتِ یک پیام', () => {
  const env = {};
  const rows = [
    [{ channel: 'push', result: 'rejected' }, { channel: 'sms', result: 'accepted', costMicros: 892_000 }],
    [{ channel: 'sms', result: 'accepted' }],
    [{ channel: 'sms', result: 'retry', costMicros: 446_000 }],
    [{ channel: 'whatsapp', result: 'accepted', costMicros: 30_000 }],
    'خراب',
    [null],
  ];
  assert.equal(C.spentOf(rows, 'sms', env), 892_000 + 446_000);
  assert.equal(C.spentOf(rows, 'whatsapp', env), 30_000);
  assert.equal(C.spentOf([], 'sms', env), 0);
});
