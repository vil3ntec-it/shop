'use strict';
/**
 * INV-16 — ارزشِ موجودی به بها و سودِ بالقوه در نسخهٔ وب (`license/stock-value-core.js`).
 *
 *  ۱) بردارهای مرجعِ مشترک (`core/test-vectors/stock-value-v1.json`): همان پرونده را `StockValueVectorsTest.kt` روی
 *     `StockAnalysis.valuation` ِ هستهٔ Kotlin می‌دواند (آینهٔ آزمونِ پرونده‌خوانِ کاتلین).
 *  ۲) `fromLedger` روی دفترِ خامِ برنامه، با عددهایی که با دست حساب شده‌اند: ارزش = جمعِ دفترِ میانگینِ موزون
 *     (۱ به ۱۰ + ۲ به ۵ ⇒ ۲۰٫۰۰، نه ۳ × ۶٫۶۷)، کیت، غیرفعالِ بی موجودی، کالای پاک‌شده، پایهٔ نامعلوم ⇒ `purchasePrice`.
 *  ۳) همان عددِ «بهای میانگین» (`report-core.stockCost`) برای هر کالای حساب‌شده؛ و ES5.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const FILE = path.join(ROOT, 'license', 'stock-value-core.js');
const V = require(FILE);
const R = require(path.join(ROOT, 'license', 'report-core.js'));
const vectors = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'stock-value-v1.json'), 'utf8'));

test('بردارهای مرجع: هر مورد عیناً همان expect (همان StockAnalysis.valuation ِ Kotlin)', () => {
  assert.equal(vectors.format, V.VECTORS_FORMAT);
  assert.equal(vectors.max_exact, V.MAX_EXACT);
  assert.ok(vectors.cases.length >= 15, `cases: ${vectors.cases.length}`);
  for (const c of vectors.cases) {
    assert.deepEqual(JSON.parse(JSON.stringify(V.valuation(c.lines))), c.expect, c.name);
  }
});

test('بی «صفرِ منفی» و بی عددِ غیرصحیح در خروجی', () => {
  const v = V.valuation([{ id: 'm', onHandMilli: 1000, unitCostMinor: 20001, unitPriceMinor: 20000 }]);
  assert.ok(Object.is(v.marginBp, 0), 'حاشیهٔ −۰٫۵ بیسیس‌پوینت ⇒ ۰، نه −۰');
  for (const c of vectors.cases) {
    const v2 = V.valuation(c.lines);
    for (const k of ['costMinor', 'retailMinor', 'potentialProfitMinor']) assert.ok(Number.isSafeInteger(v2[k]), `${c.name}: ${k}`);
  }
});

test('ES5: بی let/const/arrow/backtick — همان قیدِ report-core', () => {
  const src = fs.readFileSync(FILE, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  assert.doesNotMatch(src, /=>|`|\blet\s|\bconst\s|\bclass\s/);
});

//  دفترِ خامِ برنامه (شکلِ `tohidReportEnv().ledger` ِ index.html)
const T0 = Date.UTC(2026, 9, 1, 6);
const entry = (id, productId, units, price, extra = {}) => ({ id, productId, units, price, date: '2026-10-01', createdAt: T0 + Number(id.slice(1)) * 1000, ...extra });
const LEDGER = {
  products: [
    { id: 'A', name: 'صابون', salePrice: 9, purchasePrice: 5 },
    { id: 'B', name: 'شکر', salePrice: 4, purchasePrice: 5 },
    { id: 'C', name: 'نمک', salePrice: 0, purchasePrice: 3 },
    { id: 'N', name: 'بی ورود', salePrice: 10, purchasePrice: 2 },
    { id: 'K', name: 'بستهٔ هدیه', salePrice: 30, kit: [{ productId: 'A', qty: 1 }] },
    { id: 'I', name: 'کهنه', salePrice: 5, purchasePrice: 1, active: false },
    { id: 'U', name: 'شمرده', salePrice: 8, purchasePrice: 6 },
  ],
  warehouseEntries: [
    entry('e1', 'A', 1, 10), entry('e2', 'A', 2, 5),
    entry('e3', 'B', 2, 5),
    entry('e4', 'C', 1, 3),
    //  شمارشِ مثبتِ بی بها ⇒ پایهٔ نامعلوم
    entry('e5', 'U', 2, 0, { isAdjustment: true }),
  ],
  sales: [{ id: 's1', status: 'completed', date: '2026-10-02', createdAt: T0 + 86400000 }],
  saleItems: [
    { id: 'i1', saleId: 's1', productId: 'N', quantity: 2, purchasePrice: 2, totalPrice: 20 },
    { id: 'i2', saleId: 's1', productId: 'K', quantity: 1, purchasePrice: 0, totalPrice: 30 },
    { id: 'i3', saleId: 's1', productId: 'I', quantity: 1, purchasePrice: 1, totalPrice: 5 },
    //  کالای پاک‌شده: در دفتر هست، در فهرستِ کالاها نه
    { id: 'i4', saleId: 's1', productId: 'X', quantity: 1, purchasePrice: 1, totalPrice: 5 },
  ],
  returns: [],
};

test('fromLedger با دست: ارزش = جمعِ دفترِ میانگینِ موزون، سودِ بالقوه فقط با بها و قیمت', () => {
  const { valuation: v, rows } = V.fromLedger(LEDGER, R);
  //  A: ۱ × ۱۰ + ۲ × ۵ = ۲۰٫۰۰ (نه ۳ × ۶٫۶۷ = ۲۰٫۰۱)، به قیمتِ ۹ ⇒ ۲۷٫۰۰ و سودِ ۷٫۰۰
  //  B: ۲ × ۵ = ۱۰٫۰۰ به قیمتِ ۴ ⇒ ۸٫۰۰ و سودِ −۲٫۰۰ (زیرِ بها)
  //  C: ۱ × ۳ = ۳٫۰۰ بی قیمتِ فروش ⇒ در سود نیست
  //  U: پایهٔ نامعلوم ⇒ قیمتِ خریدِ ۶: ۲ × ۶ = ۱۲٫۰۰ به قیمتِ ۸ ⇒ ۱۶٫۰۰ و سودِ ۴٫۰۰ («تقریبی»)
  assert.equal(v.costMinor, 2000 + 1000 + 300 + 1200);
  assert.equal(v.retailMinor, 2700 + 800 + 0 + 1600);
  assert.equal(v.potentialProfitMinor, 700 - 200 + 400);
  assert.equal(v.marginBp, 1765); // ۹۰۰ ÷ ۵۱۰۰ = ۱۷٫۶۴۷٪
  assert.equal(v.inStock, 4);
  assert.deepEqual(v.missingCost, []);
  assert.deepEqual(v.missingPrice, ['C']);
  assert.deepEqual(v.belowCost, ['B']);
  //  N فروخته شد بی ورود ⇒ منفی و بیرون از جمع؛ کیت (K)، غیرفعالِ بی موجودی (I) و کالای پاک‌شده (X) نه
  assert.deepEqual(v.negative, ['N']);
  assert.equal(v.complete, false);
  assert.deepEqual(rows, [
    { productId: 'A', name: 'صابون', qtyMilli: 3000, unitCostMinor: 667, costMinor: 2000, retailMinor: 2700, profitMinor: 700, approximate: false },
    { productId: 'B', name: 'شکر', qtyMilli: 2000, unitCostMinor: 500, costMinor: 1000, retailMinor: 800, profitMinor: -200, approximate: false },
    { productId: 'C', name: 'نمک', qtyMilli: 1000, unitCostMinor: 300, costMinor: 300, retailMinor: 0, profitMinor: null, approximate: false },
    { productId: 'U', name: 'شمرده', qtyMilli: 2000, unitCostMinor: 600, costMinor: 1200, retailMinor: 1600, profitMinor: 400, approximate: true },
  ]);
});

test('fromLedger همان «بهای میانگین» ِ report-core: ارزشِ هر کالای بازپخش‌شده همان valueMinor', () => {
  const sc = R.stockCost(LEDGER);
  const { rows } = V.fromLedger(LEDGER, R);
  for (const r of rows.filter((x) => !x.approximate)) {
    const s = sc.rows.find((x) => x.productId === r.productId);
    assert.equal(r.costMinor, s.valueMinor, r.productId);
    assert.equal(r.unitCostMinor, s.unitCostMinor, r.productId);
    assert.equal(r.qtyMilli, s.qtyMilli, r.productId);
  }
  //  کیت با فروشش در بازپخش منفی است ولی این‌جا نیست (اجزایش شمرده می‌شوند)
  assert.ok(sc.rows.some((x) => x.productId === 'K' && x.qtyMilli < 0));
});

test('fromLedger: کالای غیرفعالِ موجود می‌آید، تکراری یک بار، دفترِ خالی و بی‌شکل بی خطا', () => {
  const L = JSON.parse(JSON.stringify(LEDGER));
  L.products.push({ ...L.products[0] });
  L.products[0].active = false;
  const { valuation: v, rows } = V.fromLedger(L, R);
  assert.equal(rows.filter((r) => r.productId === 'A').length, 1);
  assert.equal(v.costMinor, 4500);
  const empty = V.fromLedger({}, R);
  assert.deepEqual(empty.rows, []);
  assert.equal(empty.valuation.marginBp, null);
  assert.equal(empty.valuation.complete, true);
  assert.deepEqual(V.fromLedger({ products: [{ id: '' }, { name: 'بی شناسه' }] }, R).rows, []);
});
