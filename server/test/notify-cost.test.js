'use strict';
/**
 * NTF-02 — هزینهٔ پیامِ مشتری و سقفِ روزانهٔ هر دکان و هر کانال (`lib/notify-cost.js`، خالص).
 * «فقط ایمیل» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳): پیامک از زنجیره رفت ⇒ بخش‌بندیِ پیامک (§۴.۳) و قیمت/سقفِ پیش‌فرضِ آن هم رفت
 * (⇐ به‌جایش ایمیلِ رایگان). قیمت و سقفِ پیش‌فرض، env، مرزِ سقف و شمردنِ هزینهٔ ثبت‌شده همان می‌ماند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const C = require('../src/lib/notify-cost');

test('«فقط ایمیل»: هیچ کانالِ پیامکی، هیچ بخش‌بندیِ پیامکی؛ همهٔ کانال‌های زنجیره پیش‌فرض رایگان', () => {
  assert.equal(C.segments, undefined, 'بخش‌بندیِ پیامک رفت');
  assert.deepEqual(Object.keys(C.UNIT_MICROS).sort(), ['email', 'push', 'telegram', 'whatsapp']);
  const env = {};
  for (const ch of ['push', 'email', 'whatsapp', 'telegram']) assert.equal(C.costMicros(ch, 'س'.repeat(500), env), 0, ch);
  //  کانالِ ناشناخته (از جمله پیامکِ کهنه) قیمت و سقف ندارد
  assert.equal(C.costMicros('sms', 'سفارش آماده است', env), 0);
  assert.equal(C.capMicros('sms', env), 0);
  assert.equal(C.costMicros('fax', 'x', env), 0);
  assert.equal(C.capMicros('email', env), 1_000_000);
  assert.equal(C.capMicros('whatsapp', env), 1_000_000);
});

test('NTF-02: env قیمت و سقف را عوض می‌کند؛ مقدارِ نادرست ⇒ پیش‌فرض؛ سقفِ صفر ⇒ هیچ پیامِ پولی', () => {
  const env = {
    CUSTOMER_NOTIFY_COST_MICROS_WHATSAPP: '30000',
    CUSTOMER_NOTIFY_COST_MICROS_EMAIL: '-5',
    CUSTOMER_NOTIFY_DAILY_CAP_MICROS_TELEGRAM: '0',
    CUSTOMER_NOTIFY_COST_MICROS_TELEGRAM: '1000',
    CUSTOMER_NOTIFY_DAILY_CAP_MICROS_WHATSAPP: '1.5',
    CUSTOMER_NOTIFY_COST_MICROS_SMS: '446000',
  };
  assert.equal(C.costMicros('whatsapp', 'x', env), 30_000);
  assert.equal(C.costMicros('email', 'x', env), 0, 'منفی ⇒ پیش‌فرض');
  assert.equal(C.capMicros('whatsapp', env), 1_000_000, 'اعشار ⇒ پیش‌فرض');
  assert.deepEqual(C.allows('telegram', 'x', 0, env), { ok: false, costMicros: 1000, capMicros: 0 });
  assert.equal(C.costMicros('sms', 'x', env), 0, 'env ِ پیامک دیگر اثری ندارد');
  //  کانالِ بی هزینه هرگز به سقف نمی‌خورد
  assert.equal(C.allows('push', 'x', 99_000_000, env).ok, true);
});

test('NTF-02: سقف با هزینهٔ همین پیام سنجیده می‌شود — مرز پذیرفته است', () => {
  const env = { CUSTOMER_NOTIFY_COST_MICROS_WHATSAPP: '446000', CUSTOMER_NOTIFY_DAILY_CAP_MICROS_WHATSAPP: '2230000' };
  assert.equal(C.allows('whatsapp', 'x', 0, env).ok, true);
  assert.equal(C.allows('whatsapp', 'x', 2_230_000 - 446_000, env).ok, true, 'دقیقاً تا سقف');
  assert.equal(C.allows('whatsapp', 'x', 2_230_000 - 446_000 + 1, env).ok, false);
  assert.equal(C.allows('whatsapp', 'x', -100, env).ok, true, 'هزینهٔ منفی صفر است');
});

test('NTF-02: هزینهٔ ثبت‌شده فقط از «پذیرفت» ِ همان کانال؛ ردیفِ پیشین بی costMicros ⇒ قیمتِ یک پیام', () => {
  const env = { CUSTOMER_NOTIFY_COST_MICROS_WHATSAPP: '30000' };
  const rows = [
    [{ channel: 'push', result: 'rejected' }, { channel: 'whatsapp', result: 'accepted', costMicros: 892_000 }],
    [{ channel: 'whatsapp', result: 'accepted' }],
    [{ channel: 'whatsapp', result: 'retry', costMicros: 446_000 }],
    [{ channel: 'email', result: 'accepted', costMicros: 0 }],
    [{ channel: 'sms', result: 'accepted', costMicros: 446_000 }],
    'خراب',
    [null],
  ];
  assert.equal(C.spentOf(rows, 'whatsapp', env), 892_000 + 30_000);
  assert.equal(C.spentOf(rows, 'email', env), 0);
  assert.equal(C.spentOf([], 'whatsapp', env), 0);
});
