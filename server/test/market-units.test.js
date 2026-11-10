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
 *   ۶) اندازهٔ واحدِ بی عدد از «واحدِ فروشِ دوم» (`saleUnit` + ضریب) و «بسته‌ها» (`packs`، PRD-04) وقتی نامِ آن‌ها
 *      اندازهٔ روشن دارد و نسبت دقیق است؛ ناسازگاری، دانهٔ اعشاری و دو پایهٔ بی اندازهٔ خودِ واحد ⇒ null.
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

test('S70: اندازهٔ واحدِ بی عدد از واحدِ فروشِ دوم و بسته‌ها (`saleUnit`، `packs`)', () => {
  const pk = (name, sizeMilli) => ({ name, sizeMilli });
  //  «شیشه» خودش اندازه ندارد؛ «کارتن ۱۲ عددی» = ۱۲ شیشه ⇒ یک شیشه یک دانه
  assert.deepEqual(units.sizeOfItem('شیشه', { packs: [pk('کارتن ۱۲ عددی', 12000)] }), { base: 'piece', milli: 1000 });
  //  یک «عدد» = ۰٫۱ بسته ⇒ بسته ۱۰ دانه؛ یک «بستهٔ ۶ تایی» = ۰٫۲۵ کارتن ⇒ کارتن ۲۴ دانه؛ یک کیلو = ۰٫۰۲ کیسه ⇒ کیسه ۵۰ کیلو
  assert.deepEqual(units.sizeOfItem('بسته', { saleUnit: { unit: 'عدد', factorMilli: 100 } }), { base: 'piece', milli: 10000 });
  assert.deepEqual(units.sizeOfItem('کارتن', { saleUnit: { unit: 'بستهٔ ۶ تایی', factorMilli: 250 } }), { base: 'piece', milli: 24000 });
  assert.deepEqual(units.sizeOfItem('کیسه', { saleUnit: { unit: 'کیلو', factorMilli: 20 } }), { base: 'kg', milli: 50000 });
  //  هم‌خوان ⇒ همان؛ اندازهٔ خودِ واحد جلو
  assert.deepEqual(units.sizeOfItem('کارتن ۲۴ عددی', { packs: [pk('عدد', 42)] }), { base: 'piece', milli: 24000 },
    'راهِ نادقیق (۱÷۰٫۰۴۲) کنار می‌رود، نه ناسازگاری');
  assert.deepEqual(units.sizeOfItem('کارتن ۲۴ عددی', { saleUnit: { unit: 'بستهٔ ۶ تایی', factorMilli: 250 } }), { base: 'piece', milli: 24000 });
  //  پایهٔ دیگر کنارِ اندازهٔ خودِ واحد ⇒ خودِ واحد (بطری ۱٫۵ لیتر و یک دانه، هر دو راست)
  assert.deepEqual(units.sizeOfItem('بطری ۱٫۵ لیتری', { packs: [pk('کارتن ۶ تایی', 6000)] }), { base: 'l', milli: 1500 });
  //  ⛔ ناسازگار در همان پایه (۲۴ و ۲۰) ⇒ هیچ، هرگز یکی را برنمی‌گزیند
  assert.equal(units.sizeOfItem('کارتن ۲۴ عددی', { packs: [pk('کارتن ۲۰ تایی', 1000)] }), null);
  assert.equal(units.sizeOfItem('بسته', { saleUnit: { unit: 'عدد', factorMilli: 100 }, packs: [pk('کارتن ۱۲ عددی', 1000)] }), null);
  //  ⛔ دو پایه بی اندازهٔ خودِ واحد ⇒ کدام؟ هیچ
  assert.equal(units.sizeOfItem('شیشه', { saleUnit: { unit: 'میلی‌لیتر', factorMilli: 2 }, packs: [pk('کارتن ۱۲ عددی', 12000)] }), null);
  //  دو راهِ هم‌خوان بی اندازهٔ خودِ واحد ⇒ همان
  assert.deepEqual(units.sizeOfItem('شیشه', { saleUnit: { unit: 'عدد', factorMilli: 1000 }, packs: [pk('کارتن ۱۲ عددی', 12000)] }),
    { base: 'piece', milli: 1000 });
  //  ⛔ نسبتِ نادقیق (۱÷۲۴ ⇐ ۴۲ هزارم ⇒ ۲۳٫۸ دانه) و دانهٔ اعشاری (کارتنِ ۲۵ تایی = ۴ بسته ⇒ ۶٫۲۵) ⇒ null
  assert.equal(units.sizeOfItem('کارتن', { saleUnit: { unit: 'عدد', factorMilli: 42 } }), null);
  assert.equal(units.sizeOfItem('بسته', { packs: [pk('کارتن ۲۵ عددی', 4000)] }), null);
  //  نامِ بی اندازه، اندازهٔ ناجور و ورودیِ ناجور کنار می‌روند
  for (const more of [
    { packs: [pk('کارتن', 24000), pk('شل', 6000)] }, { saleUnit: { unit: 'کارتن', factorMilli: 24000 } },
    { packs: [pk('کارتن ۲۴ عددی', 0)] }, { packs: [pk('کارتن ۲۴ عددی', -24000)] }, { packs: [pk('کارتن ۲۴ عددی', 1.5)] },
    { packs: [pk('کارتن ۲۴ عددی', 2_000_000_000)] }, { saleUnit: { unit: 'عدد', factorMilli: 0 } },
    { saleUnit: { unit: 'عدد' } }, { saleUnit: 'عدد' }, { packs: 'کارتن ۲۴ عددی' }, { packs: [null, 5, 'x'] }, {}, undefined,
  ]) assert.equal(units.sizeOfItem('کارتن', more), null, JSON.stringify(more));
  //  فقط پنج بستهٔ نخست (همان `PackBarcodes.MAX_PACKS`)
  const five = Array.from({ length: 5 }, () => pk('کارتن', 1000));
  assert.equal(units.sizeOfItem('شیشه', { packs: [...five, pk('کارتن ۱۲ عددی', 12000)] }), null);
  //  قیمتِ یک پایه با همین اندازه
  assert.deepEqual(units.perUnit('شیشه', 2500, { packs: [pk('کارتن ۱۲ عددی', 12000)] }), { base: 'piece', priceMinor: 2500 });
  assert.deepEqual(units.perUnit('بسته', 18000, { saleUnit: { unit: 'عدد', factorMilli: 100 } }), { base: 'piece', priceMinor: 1800 });
  assert.deepEqual(units.perUnit('عدد', 2000, { packs: [pk('کارتن ۲۴ عددی', 24000)] }), { base: 'piece', priceMinor: 2000 });
  assert.equal(units.perUnit('کارتن', 30000, { packs: [pk('کارتن', 1000)] }), null);
});
