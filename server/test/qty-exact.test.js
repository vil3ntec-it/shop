'use strict';
/**
 * PRD-08 (کالای وزنی، مقدار با ۳ رقمِ اعشار) — هم‌زادِ JS ِ `core/money/QtyExact.kt`: `license/money.js` ⇐ `qtyExact`.
 *
 * همان بردارِ `core/test-vectors/qty-exact-v1.json` که `QtyExactVectorsTest` (هسته) و `QtyExactWiringTest` (موتورِ صندوق)
 * می‌خوانند. ادعاها:
 *  ۱. فایل دقیقاً خروجیِ امروزِ سازنده است (کهنه نیست)؛ «legacy» خودِ `lineTotal` ِ `index.html` است.
 *  ۲. هم‌زادِ JS روی همهٔ ردیف‌ها همان عددِ هسته را می‌دهد (مبلغ، جمع، جای خالی، اعشارِ زیادی).
 *  ۳. دروازه: پرچمِ خاموش ⇒ همیشه همان موتورِ امروز؛ مقدارِ درست ⇒ همان امروز حتی با پرچمِ روشن؛ فرق فقط در
 *     مقدارِ اعشاری و فقط یک افغانی در نیمه‌های دقیق (جاروبِ ۸۹۹٬۱۰۰ جفت).
 *  ۴. مبلغِ ردیفِ اعشاری با پرچمِ روشن همان `order-states.lineTotalMinor` ِ سفارشِ آنلاینِ سرور است.
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
  assert.equal(V.version, 1);
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
    if (!Q.fractional(r.qtyMilli)) assert.equal(r.onMinor, r.legacyMinor, `${r.name}: مقدارِ درست عوض شد`);
    else if (r.exactMinor !== null) assert.equal(r.onMinor, r.exactMinor, r.name);
  }
  //  S04 و نیمهٔ دقیقی که امروز یک افغانی کم می‌آورد
  const by = (n) => V.line.find((r) => r.name.startsWith(n));
  assert.equal(by('S04: برچسبِ').onMinor, 37500);
  assert.equal(by('S04: وزنِ دستی').onMinor, 9000);
  assert.deepEqual([by('نیمهٔ دقیق: ۱٫۰۱۵').legacyMinor, by('نیمهٔ دقیق: ۱٫۰۱۵').onMinor], [10100, 10200]);
});

test('روشن ⇒ مبلغِ ردیفِ اعشاری همان سفارشِ آنلاینِ سرور (`order-states.lineTotalMinor`)', () => {
  for (const r of V.line) {
    if (r.exactMinor === null || !Q.fractional(r.qtyMilli)) continue;
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

test('جاروب: فرق فقط در مقدارِ اعشاری، فقط +۱ افغانی، و مقدارِ درست هرگز', () => {
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
      if (Q.lineMinor(p / 100, q / 1000, true) !== lg) wOn++;
      if (Q.exactMinor(p, q) !== lg) wExact++;
    }
  }
  assert.deepEqual({ pairs: wPairs, onDiffs: wOn, exactDiffs: wExact }, { pairs: w.pairs, onDiffs: 0, exactDiffs: w.exactDiffs });
  assert.ok(w.exactDiffs > 0, 'قاعدهٔ دقیق برای مقدارِ درست هم فرق می‌داشت — دروازه همین را نگه می‌دارد');
});
