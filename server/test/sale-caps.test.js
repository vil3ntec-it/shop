'use strict';
/**
 * STF-03 — `lib/sale-caps.js` روی همهٔ `core/test-vectors/sale-caps-v1.json`؛ همان فایل را `SaleCapsVectorsTest` ِ core
 * برای `SaleCaps.kt` می‌خواند. صندوق (پیش‌سنجی) و سرور (داوری) پس با یک قاعده سقف را می‌سنجند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const fs = require('fs');
const C = require('../src/lib/sale-caps');
const P = require('../src/lib/perm-core');

const V = JSON.parse(fs.readFileSync(path.join(__dirname, '../../core/test-vectors/sale-caps-v1.json'), 'utf8'));

test('ثابت‌ها همان بردار', () => {
  assert.equal(V.version, C.VERSION);
  assert.equal(V.round_max_minor, C.ROUND_MAX_MINOR);
  assert.equal(V.tol_minor, C.TOL_MINOR);
  assert.equal(V.loyalty_spend, C.LOYALTY_SPEND);
});

test('پخشِ امتیاز میانِ ردیف‌ها — همان Loyalty.split ِ صندوق', () => {
  assert.ok(V.split.length >= 5);
  for (const r of V.split) assert.deepEqual(C.split(r.lines, r.value), r.out, JSON.stringify(r));
});

test('مبلغِ ردیف و قیمت‌های فهرست', () => {
  for (const r of V.gross) assert.equal(C.gross(r.unit, r.qty), r.out, JSON.stringify(r));
  //  تصمیمِ ۱-۱۱ (۳): `wholesale` ِ بردار (نیامده ⇒ درست، همان پیش‌فرضِ هسته) = قیمتِ عمده فهرست هست یا نه
  for (const r of V.refs) assert.deepEqual(C.refUnits(r.product, r.date, r.wholesale !== false), r.out, JSON.stringify(r));
  assert.ok(V.refs.some((r) => r.wholesale === false), 'بردارِ فاکتورِ خرده با کالای عمده‌دار');
});

test('سنجشِ ردیف، پایهٔ تخفیف و نیازهای فاکتور', () => {
  assert.ok(V.lines.length >= 15 && V.needs.length >= 8);
  for (const r of V.lines) assert.deepEqual(C.line(r.line, r.product, r.date, r.wholesale !== false), r.out, r._);
  for (const r of V.basis) assert.deepEqual(C.basis(r.discount, r.total, r.final, r.rounding, r.reduction, r.tax), r.out, JSON.stringify(r));
  for (const r of V.needs) {
    assert.deepEqual(C.needs(r.sale, r.lines, r.refs, { enforcePrice: r.enforcePrice, enforceCredit: r.enforceCredit }), r.out, r._);
  }
});

test('تصمیمِ ۱-۱۱: قلمِ آزاد نیازِ sales.free_line دارد و قیمتِ عمده فقط در فاکتورِ لیستِ عمده فهرست است', () => {
  const free = V.needs.filter((r) => r.out.some((n) => n.perm === C.FREE_LINE));
  assert.ok(free.length >= 4, 'بردارهای قلمِ آزاد');
  for (const r of free) assert.equal(r.enforcePrice, true, `${r._}: فقط با پرچمِ قیمت`);
  const ws = V.needs.filter((r) => r.lines.some((l) => r.refs[l.productId] && l.unitMinor === r.refs[l.productId].wholesaleMinor));
  assert.ok(ws.some((r) => r.sale.wholesale === true && r.out.length === 0), 'مشتریِ عمده بی نیاز');
  assert.ok(ws.some((r) => r.sale.wholesale !== true && r.enforcePrice && r.out.some((n) => n.perm === C.DISCOUNT)), 'فاکتورِ خرده ⇒ تخفیفِ پنهان');
  assert.equal(C.isWholesaleDebtor({ priceList: ' wholesale ' }), true);
  for (const d of [{ priceList: 'retail' }, { priceList: 'Wholesale' }, {}, null, { priceList: 5 }]) assert.equal(C.isWholesaleDebtor(d), false, JSON.stringify(d));
});

test('تصمیمِ perm-core برای همان نیازها', () => {
  for (const r of V.judge) {
    assert.deepEqual(r.needs.map((n) => ({ perm: n.perm, decision: P.evaluate(r.role, r.layers, n.perm, C.req(n)).decision })), r.out, r._);
  }
});

test('شکلِ دادهٔ سرور ⇒ ورودیِ هسته (v3 و راهِ قدیم)', () => {
  const v3 = C.refOf({ salePriceMinor: 100000, wholesalePriceMinor: 80000, priceWindows: [{ from: '2026-11-01', to: '2026-11-30', price: 900 }],
    packs: [{ sizeMilli: 12000, priceMinor: 108000 }, { sizeMilli: 6000 }] });
  assert.deepEqual(v3, { saleMinor: 100000, wholesaleMinor: 80000, windows: [{ from: '2026-11-01', to: '2026-11-30', priceMinor: 90000 }],
    packs: [{ sizeMilli: 12000, priceMinor: 108000 }, { sizeMilli: 6000, priceMinor: 0 }] });
  assert.deepEqual(C.refOf({ salePrice: 1000.5, wholesalePrice: 0 }, { legacy: true }).saleMinor, 100050);
  assert.deepEqual(C.lineOf({ productId: 'p', unitPriceMinor: 9000, quantityMilli: 2500, promo: [{ discountMinor: 300 }, { discountMinor: -5 }] }),
    { productId: 'p', unitMinor: 9000, qtyMilli: 2500, promoMinor: 300 });
  assert.deepEqual(C.lineOf({ productId: 'p', unitPrice: 90.5, quantity: 2.5 }, { legacy: true }), { productId: 'p', unitMinor: 9050, qtyMilli: 2500, promoMinor: 0 });
  const s = C.saleOf('s1', { totalMinor: 100, discountMinor: 0, date: '2026-11-08' });
  assert.equal(s.finalTotalMinor, null, 'جمعِ نهاییِ نیامده شکاف نمی‌سازد');
  assert.equal(C.saleOf('s1', { total: 10, finalTotal: 9, debtGiven: 4 }, { legacy: true }).finalTotalMinor, 900);
});
