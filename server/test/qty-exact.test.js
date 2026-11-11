'use strict';
/**
 * PRD-08 (کالای وزنی، مقدار با ۳ رقمِ اعشار) — هم‌زادِ JS ِ `core/money/QtyExact.kt`: `license/money.js` ⇐ `qtyExact`.
 *
 * همان بردارِ `core/test-vectors/qty-exact-v1.json` که `QtyExactVectorsTest` (هسته) و `QtyExactWiringTest` (موتورِ صندوق)
 * می‌خوانند. ادعاها:
 *  ۱. فایل دقیقاً خروجیِ امروزِ سازنده است (کهنه نیست)؛ «legacy» خودِ `lineTotal` ِ `index.html` است.
 *  ۲. هم‌زادِ JS روی همهٔ ردیف‌ها همان عددِ هسته را می‌دهد (مبلغ، جمع، جای خالی، اعشارِ زیادی).
 *  ۳. دروازه: پرچمِ خاموش ⇒ همیشه همان موتورِ امروز؛ روشن ⇒ قاعدهٔ دقیق برای **هر** مقدار (نسخهٔ ۲، تصمیمِ ۳-۱۲ ِ صاحب‌کار:
 *     مقدارِ درست هم — ۵۰ × ۰٫۲۹ = ۱۵، نه ۱۴)؛ فرق فقط یک افغانی در نیمه‌های دقیق (جاروبِ ۸۹۹٬۱۰۰ جفتِ اعشاری و ۹۰٬۰۰۰ جفتِ درست).
 *  ۴. مبلغِ ردیف با پرچمِ روشن همان `order-states.lineTotalMinor` ِ سفارشِ آنلاینِ سرور است (اعشاری و درست).
 * هیچ پایگاه‌داده‌ای لازم نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const M = require(path.join(__dirname, '..', '..', 'license', 'money.js'));
const Q = M.qtyExact;
const gen = require('../scripts/qty-exact-vectors');
const S = require('../src/lib/order-states');

const V = JSON.parse(fs.readFileSync(gen.FIXTURE, 'utf8'));

test('بردارها همان خروجیِ امروزِ سازنده‌اند (فایل کهنه نیست)', () => {
  assert.equal(fs.readFileSync(gen.FIXTURE, 'utf8'), gen.serialize(gen.build()),
    'بردارها کهنه‌اند: cd server && node scripts/qty-exact-vectors.js --write');
  assert.equal(V.version, 2, 'نسخهٔ ۲: مقدارِ درست هم دقیق (۳-۱۲)');
  assert.equal(Q.DECIMALS, 3);
});

test('مبلغِ ردیف: هم‌زادِ JS = بردار؛ خاموش ⇒ موتورِ امروز، روشن ⇒ دروازهٔ اعشار', () => {
  assert.ok(V.line.length >= 20);
  for (const r of V.line) {
    assert.equal(Q.milli(r.qty), r.qtyMilli, r.name);
    assert.equal(Q.legacyMinor(r.price, r.qty), r.legacyMinor, r.name);
    assert.equal(Q.exactMinor(r.priceMinor, r.qtyMilli), r.exactMinor, r.name);
    assert.equal(Q.lineMinor(r.price, r.qty, false), r.legacyMinor, `${r.name}: پرچمِ خاموش همان امروز نیست`);
    assert.equal(Q.lineMinor(r.price, r.qty, true), r.onMinor, r.name);
    //  ۳-۱۲: روشن ⇒ قاعدهٔ دقیق برای هر مقدار؛ فقط بیرون از بازهٔ دقیق همان امروز
    assert.equal(r.onMinor, r.exactMinor === null ? r.legacyMinor : r.exactMinor, r.name);
  }
  //  S04 و نیمهٔ دقیقی که امروز یک افغانی کم می‌آورد
  const by = (n) => V.line.find((r) => r.name.startsWith(n));
  assert.equal(by('S04: برچسبِ').onMinor, 37500);
  assert.equal(by('S04: وزنِ دستی').onMinor, 9000);
  assert.deepEqual([by('نیمهٔ دقیق: ۱٫۰۱۵').legacyMinor, by('نیمهٔ دقیق: ۱٫۰۱۵').onMinor], [10100, 10200]);
  //  ۳-۱۲: مقدارِ درست با قیمتِ اعشاری هم نیمه را بالا می‌برد (امروز ۱۴)
  assert.deepEqual([by('مقدارِ درست: ۵۰ × ۰٫۲۹').legacyMinor, by('مقدارِ درست: ۵۰ × ۰٫۲۹').onMinor], [1400, 1500]);
  assert.deepEqual([by('مقدارِ درست: ۲ × ۱۱۵').legacyMinor, by('مقدارِ درست: ۲ × ۱۱۵').onMinor], [23000, 23000]);
});

test('روشن ⇒ مبلغِ ردیف (اعشاری و درست) همان سفارشِ آنلاینِ سرور (`order-states.lineTotalMinor`)', () => {
  for (const r of V.line) {
    if (r.exactMinor === null) continue;
    assert.equal(S.lineTotalMinor(r.qtyMilli, r.priceMinor), r.onMinor, r.name);
  }
});

test('جمعِ سبد، جای خالی تا موجودی و پاک‌سازی به هزارمِ دقیق', () => {
  for (const r of V.plus) {
    assert.equal(r.a + r.b, r.legacy, `${r.name}: جمعِ Double ِ امروز`);
    assert.equal(Q.plus(r.a, r.b), r.milli / 1000, r.name);
    assert.equal(Q.milli(Q.plus(r.a, r.b)), r.milli, r.name);
  }
  assert.notEqual(V.plus[1].legacy, 0.3, 'سه بار «+۰٫۱» امروز ۰٫۳۰۰۰۰۰۰۰۰۰۰۰۰۰۰۰۴ است');
  for (const r of V.room) assert.equal(Q.room(r.available, r.inCart), r.milli / 1000, r.name);
  assert.equal(Q.clean(0.1 + 0.2), 0.3);
  assert.equal(Q.clean(Number.NaN), Number.NaN);
  assert.equal(Q.plus(Number.POSITIVE_INFINITY, 1), Number.POSITIVE_INFINITY);
});

test('کادرِ مقدار: بیش از سه رقمِ اعشارِ معنادار (رقمِ فارسی/عربی، «٫» و «٬»)', () => {
  for (const r of V.precise) assert.equal(Q.tooPrecise(r.text), r.over, r.name);
});

test('جاروب: فرق فقط +۱ افغانی در نیمهٔ دقیق — مقدارِ اعشاری و (۳-۱۲) مقدارِ درست', () => {
  const s = V.sweep;
  let pairs = 0; let diffs = 0; let sumOn = 0; let sumLegacy = 0; const first = [];
  for (let q = s.qtyMilli[0]; q <= s.qtyMilli[1]; q++) {
    if (q % 1000 === 0) continue;
    for (let p = s.priceMinor[0]; p <= s.priceMinor[1]; p += s.priceStepMinor) {
      const lg = Q.lineMinor(p / 100, q / 1000, false); const on = Q.lineMinor(p / 100, q / 1000, true);
      pairs++; sumOn += on; sumLegacy += lg;
      if (on !== lg) {
        diffs++;
        assert.equal(on - lg, 100, `${q} × ${p}: فرق جز یک افغانی`);
        assert.equal((q * p) % 100000, 50000, `${q} × ${p}: فرق بیرون از نیمهٔ دقیق`);
        if (first.length < s.first.length) first.push([q, p, lg, on]);
      }
    }
  }
  assert.deepEqual({ pairs, diffs, sumOn, sumLegacy, first }, { pairs: s.pairs, diffs: s.diffs, sumOn: s.sumOn, sumLegacy: s.sumLegacy, first: s.first });
  const w = V.whole;
  let wPairs = 0; let wOn = 0; let wExact = 0;
  for (let q = w.qtyMilli[0]; q <= w.qtyMilli[1]; q += w.qtyStepMilli) {
    for (let p = w.priceMinor[0]; p <= w.priceMinor[1]; p++) {
      const lg = Q.lineMinor(p / 100, q / 1000, false);
      wPairs++;
      const on = Q.lineMinor(p / 100, q / 1000, true);
      if (on !== lg) {
        wOn++;
        assert.equal(on, Q.exactMinor(p, q), `${q} × ${p}: روشن جز قاعدهٔ دقیق`);
        assert.equal(on - lg, 100, `${q} × ${p}: فرق جز یک افغانی`);
      }
      if (Q.exactMinor(p, q) !== lg) wExact++;
    }
  }
  assert.deepEqual({ pairs: wPairs, onDiffs: wOn, exactDiffs: wExact }, { pairs: w.pairs, onDiffs: w.onDiffs, exactDiffs: w.exactDiffs });
  assert.equal(w.onDiffs, w.exactDiffs, '۳-۱۲: روشن ⇒ مقدارِ درست هم همان قاعدهٔ دقیق');
  assert.ok(w.exactDiffs > 0, 'مقدارِ درست با قیمتِ اعشاری گاهی یک افغانی فرق دارد');
});
