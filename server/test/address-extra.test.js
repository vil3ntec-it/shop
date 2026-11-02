'use strict';
/**
 * CHK-08 — شمارهٔ دوم و Plus Code در نشانیِ تحویل (`orders.parseAddress`، مشترکِ سفارشِ کارمند، ویترین و
 * «اصلاحِ نشانی»): هر دو اختیاری؛ نبود یا خالی ⇒ همان نشانیِ پیشین بی کلیدِ تازه؛ نادرست ⇒ ۴۰۰
 * (`bad_phone2`/`bad_plus_code`، نه دور ریختنِ بی‌صدا)؛ شمارهٔ دوم به همان شکلِ شمارهٔ مشتری؛ Plus Code بزرگ‌حرف، فقط متن (بی ژئوکدر).
 * ⛔ کارتِ پیک فقط کلیدهای خودش را برمی‌دارد (`delivery.ADDRESS_KEYS`) ⇒ شمارهٔ دوم برای پیک پوشیده می‌ماند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { parseAddress } = require('../src/lib/orders');

const code = (fn) => { try { fn(); return null; } catch (e) { return e.code; } };

test('بی کلیدِ تازه ⇒ همان نشانیِ پیشین', () => {
  assert.deepEqual(parseAddress({ landmark: 'پشتِ مسجد', text: 'کارته‌سه' }), { landmark: 'پشتِ مسجد', text: 'کارته‌سه' });
  assert.deepEqual(parseAddress({ landmark: 'پشتِ مسجد', phone2: '  ', plusCode: '' }), { landmark: 'پشتِ مسجد' });
});

test('شمارهٔ دوم به شکلِ شمارهٔ مشتری؛ رقمِ فارسی هم', () => {
  assert.equal(parseAddress({ landmark: 'x', phone2: '۰۷۹۱ ۱۱۲ ۲۳۳' }).phone2, '+93791112233');
  assert.equal(code(() => parseAddress({ landmark: 'x', phone2: '12' })), 'bad_phone2');
});

test('Plus Code: کامل یا کوتاه با شهر؛ بزرگ‌حرف؛ نادرست ⇒ ۴۰۰', () => {
  assert.equal(parseAddress({ landmark: 'x', plusCode: '8fvc9g8f+6w' }).plusCode, '8FVC9G8F+6W');
  assert.equal(parseAddress({ landmark: 'x', plusCode: ' 9G8F+6W   کابل ' }).plusCode, '9G8F+6W کابل');
  for (const bad of ['ABC+12', '9G8F6W', '9G8F+', '9G8+6W', 'AAAA+BB']) {
    assert.equal(code(() => parseAddress({ landmark: 'x', plusCode: bad })), 'bad_plus_code', bad);
  }
});

test('ولایت، گذر یا کوچه، منزل و طبقه: متنِ کمکی؛ کدِ پستی دور (LOC-10)', () => {
  const a = parseAddress({ landmark: 'x', province: ' کابل ', street: 'گذرِ سوم', house: 'منزلِ ۱۲، طبقهٔ دوم', postalCode: '1001' });
  assert.deepEqual(a, { landmark: 'x', province: 'کابل', street: 'گذرِ سوم', house: 'منزلِ ۱۲، طبقهٔ دوم' });
  assert.deepEqual(parseAddress({ landmark: 'x', province: ' ', street: '', house: null }), { landmark: 'x' });
  assert.equal(parseAddress({ landmark: 'x', province: 'ک'.repeat(90) }).province.length, 60);
  assert.equal(parseAddress({ landmark: 'x', street: 'گ'.repeat(200) }).street.length, 120);
  //  کمک‌اند، نه جای نشانی: بی پین، نشانه و متن هنوز ۴۰۰
  assert.equal(code(() => parseAddress({ province: 'کابل', street: 'گذرِ سوم', house: 'منزلِ ۱۲' })), 'bad_address');
});

test('کارتِ پیک شمارهٔ دوم را برنمی‌دارد', () => {
  const src = fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'delivery.js'), 'utf8');
  assert.ok(!/phone2/.test(src), 'delivery.js نباید phone2 را بخواند');
  assert.ok(/ADDRESS_KEYS = Object\.freeze\(\['landmark', 'area', 'gateColor', 'text', 'pin'\]\)/.test(src));
});
