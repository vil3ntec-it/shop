'use strict';
/**
 * PRM-13 / STF-03 — مصرفِ امتیاز در JS (`lib/loyalty-balance.js`: `validRule`، `cap`، `valueMinor`، `spend`) همان `Loyalty` ِ
 * هستهٔ برنامه است: همان فایلِ `core/test-vectors/loyalty-spend-v1.json` را `LoyaltySpendVectorsTest.kt` (فقط CI) می‌دواند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const L = require('../src/lib/loyalty-balance');

const V = require(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'loyalty-spend-v1.json'));

test('بردارهای loyalty-spend-v1: قاعده، سهم، ارزش و مصرف', () => {
  assert.equal(V.version, 1);
  for (const c of V.valid) assert.equal(L.validRule(c.rule), c.out, JSON.stringify(c));
  for (const c of V.cap) assert.equal(L.cap(c.rule, c.balance, c.invoiceMinor), c.out, JSON.stringify(c));
  for (const c of V.valueMinor) assert.equal(L.valueMinor(c.rule, c.points), c.out, JSON.stringify(c));
  for (const c of V.spend) {
    const out = L.spend(c.rule, c.balance, c.invoiceMinor, c.points);
    const want = c.out.code ? { ok: false, code: c.out.code } : { ok: true, points: c.out.points, valueMinor: c.out.valueMinor };
    assert.deepEqual(out, want, JSON.stringify(c));
  }
});

test('بیرون از بازهٔ امنِ JS ⇒ null، نه عددِ گردشده (Kotlin همان را با Long دقیق دارد)', () => {
  //  ۱۰^۹ امتیاز × ۱۰^۶ افغانی × ۱۰۰ = ۱۰^۱۷ > ۲^۵۳
  assert.equal(L.valueMinor({ pointAfn: 1000000 }, 1000000000), null);
  const r = { everyAfn: 100, pointAfn: 1000000, maxSharePct: 100, silverAfn: 1, goldAfn: 2, expiryMonths: 0 };
  assert.deepEqual(L.spend(r, 1000000000, Number.MAX_SAFE_INTEGER, 90), { ok: true, points: 90, valueMinor: 9000000000 });
  //  عددِ نادرست (اعشاری، رشته) ⇒ رد، نه «آزاد»
  assert.equal(L.spend(r, 10.5, 1000, 1).code, 'OVER_BALANCE');
  assert.equal(L.spend(r, 10, 1000, '1').code, 'BAD_POINTS');
  assert.equal(L.validRule({ ...r, pointAfn: 1.5 }), false);
});
