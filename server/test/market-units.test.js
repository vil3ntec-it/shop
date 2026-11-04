'use strict';
/**
 * S70 (§۱۹.۱۲) — «دو فروشگاه با واحدهای متفاوت»: قاعدهٔ خالصِ `lib/market-units.js`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) اندازهٔ واحد فقط وقتی بی حدس روشن است: پایه‌ها، و بسته با **یک** عدد و واحدِ پایه.
 *   ۲) «بسته»/«کارتن» ِ بی عدد، دو عدد، واحدِ محلی (سیر، پاو، من) و عددِ مبهم (۱,۰۰۰) ⇒ null.
 *   ۳) قیمتِ یک پایه (دانه، کیلو، لیتر، متر)، گرد به نزدیک‌ترین یک‌صدم.
 *   ۴) «ارزان‌ترین» فقط درونِ همان پایه و فقط با دست‌کم دو پیشنهاد؛ بی واحدِ روشن هرگز.
 *   ۵) ترتیب کامل است (لغت‌نامه‌ای) و قیمتِ خامِ دو واحدِ ناهم‌سنج را مقایسه نمی‌کند مگر برای ترتیبِ پایانی.
 * آزمونِ HTTP ِ همان قاعده روی `/api/market/same/:id`: `market-same-units.test.js`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const units = require('../src/lib/market-units');

test('اندازهٔ واحدهای پایه و بسته با عددِ روشن', () => {
  const cases = [
    ['عدد', { base: 'piece', milli: 1000 }],
    ['دانه', { base: 'piece', milli: 1000 }],
    [' Pcs ', { base: 'piece', milli: 1000 }],
    ['درجن', { base: 'piece', milli: 12000 }],
    ['کیلو', { base: 'kg', milli: 1000 }],
    ['كيلوگرام', { base: 'kg', milli: 1000 }],   // ی و ک ِ عربی
    ['کیلو گرم', { base: 'kg', milli: 1000 }],
    ['گرم', { base: 'kg', milli: 1 }],
    ['لیتر', { base: 'l', milli: 1000 }],
    ['میلی‌لیتر', { base: 'l', milli: 1 }],
    ['میلی لیتر', { base: 'l', milli: 1 }],
    ['متر', { base: 'm', milli: 1000 }],
    ['سانتی‌متر', { base: 'm', milli: 10 }],
    ['کارتن ۲۴ عددی', { base: 'piece', milli: 24000 }],
    ['کارتن 24 عدد', { base: 'piece', milli: 24000 }],
    ['بستهٔ ۶ تایی', { base: 'piece', milli: 6000 }],
    ['۶تایی', { base: 'piece', milli: 6000 }],
    ['۲ درجن', { base: 'piece', milli: 24000 }],
    ['کیسه ۵۰ کیلویی', { base: 'kg', milli: 50000 }],
    ['شیشه ۵۰۰ گرمی', { base: 'kg', milli: 500 }],
    ['بطری ۱٫۵ لیتری', { base: 'l', milli: 1500 }],
    ['قوطی 330ml', { base: 'l', milli: 330 }],
    ['5kg', { base: 'kg', milli: 5000 }],
    ['۲.۵ متر', { base: 'm', milli: 2500 }],
  ];
  for (const [unit, want] of cases) assert.deepEqual(units.sizeOf(unit), want, unit);
});

test('واحدِ بی اندازهٔ روشن ⇒ null، هرگز حدس', () => {
  const unclear = [
    '', '   ', null, undefined, 42, 'بسته', 'کارتن', 'شل', 'باکس', 'تا',
    'سیر', 'پاو', 'من', 'خروار', '۲ سیر',            // واحدِ محلی: اندازه از شهری به شهری فرق دارد
    'کارتن ۲۴ تایی ۳۳۰ میلی‌لیتری', '6x330ml',       // دو عدد ⇒ مبهم
    'کیسه 1,000 گرمی', 'کیسه ۱٬۰۰۰ گرمی',            // جداکنندهٔ هزار یا اعشار؟ مبهم
    'کارتن ۲۴', '24', 'عدد ۲۴',                       // عدد بی واحد، یا واحد پیش از عدد
    '۱٫۵ عدد', '۰ کیلو', 'کیسه ۰ گرمی',               // دانهٔ اعشاری، اندازهٔ صفر
    'کارتن ۲۴ عدد در بسته', 'x'.repeat(41),
    'L', 'M', 'g',                                     // تک‌حرفِ تنها (اندازهٔ پوشاک هم هست) فقط پس از عدد
  ];
  for (const u of unclear) assert.equal(units.sizeOf(u), null, String(u));
});

test('قیمتِ یک پایه از قیمتِ واحدِ کالا', () => {
  assert.deepEqual(units.perUnit('عدد', 2000), { base: 'piece', priceMinor: 2000 });
  assert.deepEqual(units.perUnit('کارتن ۲۴ عددی', 45000), { base: 'piece', priceMinor: 1875 });
  assert.deepEqual(units.perUnit('شیشه ۵۰۰ گرمی', 12345), { base: 'kg', priceMinor: 24690 });
  assert.deepEqual(units.perUnit('بسته ۳ تایی', 1000), { base: 'piece', priceMinor: 333 }, 'گرد به نزدیک‌ترین');
  assert.deepEqual(units.perUnit('بسته ۳ تایی', 2000), { base: 'piece', priceMinor: 667 });
  assert.deepEqual(units.perUnit('گرم', 15), { base: 'kg', priceMinor: 15000 });
  assert.equal(units.perUnit('کارتن', 30000), null, 'تعدادِ داخلِ کارتن روشن نیست');
  assert.equal(units.perUnit('کیلو', 0), null);
  assert.equal(units.perUnit('کیلو', -5), null);
  assert.equal(units.perUnit('کیلو', null), null);
  assert.equal(units.perUnit('کیلو', 1.5), null);
  assert.equal(units.perUnit('کیسه ۵۰ کیلویی', 1), null, 'کمتر از یک یک‌صدم برای هر کیلو ⇒ نه صفر');
});

test('«ارزان‌ترین» فقط درونِ همان پایه و با دست‌کم دو پیشنهاد', () => {
  const a = { base: 'piece', priceMinor: 2000 };
  const b = { base: 'piece', priceMinor: 1875 };
  const c = null;                                    // «کارتن» ِ بی عدد
  const d = { base: 'kg', priceMinor: 9000 };        // تنها پیشنهادِ کیلویی
  const groups = units.compare([a, b, c, d]);
  assert.deepEqual(groups, [{ base: 'piece', lowestMinor: 1875, count: 2 }]);
  assert.equal(units.cheapest(b, groups), true);
  assert.equal(units.cheapest(a, groups), false);
  assert.equal(units.cheapest(c, groups), false, 'بی واحدِ روشن هرگز ارزان‌ترین نیست');
  assert.equal(units.cheapest(d, groups), false, 'یک پیشنهاد در پایهٔ خودش ⇒ مقایسه‌ای نیست');
  assert.deepEqual(units.compare([a]), []);
  assert.deepEqual(units.compare([]), []);
  assert.deepEqual(units.compare([null, null]), []);
  //  برابر ⇒ هر دو
  const e = { base: 'piece', priceMinor: 1875 };
  const g2 = units.compare([b, e]);
  assert.equal(units.cheapest(b, g2) && units.cheapest(e, g2), true);
  //  ترتیبِ پایه‌ها ثابت: دانه، کیلو، لیتر، متر
  assert.deepEqual(units.compare([{ base: 'l', priceMinor: 5 }, { base: 'kg', priceMinor: 7 }, { base: 'l', priceMinor: 4 },
    { base: 'kg', priceMinor: 6 }]).map((g) => g.base), ['kg', 'l']);
});

test('ترتیب: هم‌سنج‌ها با قیمتِ یک پایه، بعد بقیه؛ کامل و پایدار', () => {
  const x = (unit, priceMinor) => ({ per: units.perUnit(unit, priceMinor), priceMinor });
  const list = [x('عدد', 2000), x('کارتن', 30000), x('کارتن ۲۴ عددی', 45000), x('کیلو', 9000), x('بسته', 100)];
  const sorted = list.slice().sort(units.order).map((o) => `${o.per ? o.per.base : '-'}:${o.priceMinor}`);
  assert.deepEqual(sorted, ['piece:45000', 'piece:2000', 'kg:9000', '-:100', '-:30000']);
  for (const p of list) for (const q of list) {
    assert.equal(Math.sign(units.order(p, q)) + Math.sign(units.order(q, p)), 0, 'پادتقارن');
  }
});
