'use strict';
/**
 * POS-37 — آینهٔ JS ِ `SaleTaxVectorsTest.kt`: `license/sale-tax.js` روی `core/test-vectors/sale-tax-v1.json`. هر دو
 * هم‌زاد همان بردار را می‌دهند؛ بردارِ عوض‌شده بی هم‌خوان کردنِ کاتلین همین‌جا یا در CI ِ کاتلین سرخ می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const T = require(path.join(ROOT, 'license', 'sale-tax.js'));
const caps = require('../src/lib/sale-caps');
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'sale-tax-v1.json'), 'utf8'));

test('ثابت‌ها با بردار یکی‌اند؛ هیچ نرخِ پیش‌فرضی نیست (D-11)', () => {
  assert.deepEqual(V.constants, { fullBp: T.FULL_BP, step: T.STEP, maxRates: T.MAX_RATES, modes: T.MODES });
  assert.equal(T.clean(undefined), null);
  assert.equal(T.clean({ mode: 'invoice' }), null);
});

test('گردِ نیمه رو به بالا به افغانیِ کامل', () => {
  assert.ok(V.ofMinor.length >= 8);
  for (const c of V.ofMinor) assert.equal(T.ofMinor(c.base, c.rate), c.out, `${c.base} × ${c.rate}`);
});

test('تنظیم: نادرست ⇒ خاموش، نرخِ کالا فقط در حالتِ ردیف', () => {
  for (const c of V.clean) assert.deepEqual(T.clean(c.input), c.out, c._);
});

test('مالیاتِ سبد: فاکتور یک بار گرد، ردیف هر ردیف جدا با سهمِ تخفیف', () => {
  assert.ok(V.compute.length >= 15);
  for (const c of V.compute) assert.deepEqual(T.compute(c.lines, c.discount, c.config), c.out, c._);
});

test('سهمِ تخفیف همان «بیشترین باقی‌مانده» ِ sale-caps.split (هم‌زادِ Loyalty.split) است', () => {
  for (const c of [[[900, 450], 150], [[100, 100], 1], [[3, 3, 3], 2], [[100000, 50000], 15000], [[7, 0, 5], 6]]) {
    assert.deepEqual(T.split(c[0], c[1]), caps.split(c[0], c[1]), JSON.stringify(c));
  }
});
