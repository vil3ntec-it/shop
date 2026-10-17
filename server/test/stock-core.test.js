'use strict';
/**
 * هستهٔ موجودی و بهای تمام‌شده — PRD-15، PRD-16، INV-02، INV-19 (بخشِ ۳.۶).
 *
 * کدِ زیرِ آزمون همان `license/stock-core.js` است که در مرورگر و سرور
 * می‌دود. بردارهای مرجع (`license/test-vectors/stock-v1.json`) را
 * `StockCoreVectorsTest.kt`ِ اندروید/کامپیوتر هم عیناً می‌دواند؛ هر
 * دو باید یک عدد بدهند.
 *
 * قاعدهٔ اجرای بردار (در Kotlin هم همین):
 *   doc = { id: 'd' + (i + 1), at: 1700000000000 + i * 1000 } ∪ defaults ∪ step.doc
 *   فقط کلیدهایی که در expect آمده سنجیده می‌شوند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const core = require(path.join(__dirname, '..', '..', 'license', 'stock-core.js'));
const vectors = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', '..', 'license', 'test-vectors', 'stock-v1.json'), 'utf8'));

function subset(actual, expected, where) {
  for (const k of Object.keys(expected)) {
    // لات‌ها فقط با شناسه و مقدار سنجیده می‌شوند (تاریخ‌ها در خودِ سند آمده‌اند)
    const got = k === 'lots' ? actual[k].map((l) => ({ lotId: l.lotId, qtyMilli: l.qtyMilli })) : actual[k];
    assert.deepEqual(got, expected[k], `${where}: ${k}`);
  }
}

test('stock-v1: نسخهٔ بردارها', () => {
  assert.equal(vectors.format, 'stock-v1');
  assert.equal(core.VECTORS_FORMAT, 'stock-v1');
});

test('stock-v1: mulDivRound — نیمه به سمتِ +∞، دقیق تا ۵۳ بیت', () => {
  for (const [a, b, c, want] of vectors.mulDivRound) {
    assert.equal(core.mulDivRound(a, b, c), want, `mulDivRound(${a}, ${b}, ${c})`);
  }
});

for (const fc of vectors.fefo) {
  test(`stock-v1 FEFO: ${fc.name}`, () => {
    for (const p of fc.picks) {
      const got = core.pickFefo(fc.lots, p.qtyMilli, {
        today: fc.today, includeExpired: p.includeExpired, preferLotId: p.preferLotId || null,
      });
      subset(got, p.expect, `${fc.name} q=${p.qtyMilli}`);
    }
  });
}

for (const lc of vectors.ledger) {
  test(`stock-v1 دفتر: ${lc.name}`, () => {
    let book = core.emptyBook();
    lc.steps.forEach((step, i) => {
      const doc = Object.assign({ id: 'd' + (i + 1), at: 1700000000000 + i * 1000 }, lc.defaults, step.doc);
      const before = book;
      const r = core.apply(book, doc, { mode: step.mode || 'local', today: lc.today || null });
      subset(r.result, step.expect, `${lc.name} گام ${i + 1}`);
      if (!r.result.ok) assert.equal(r.book, before, 'سندِ ردشده دفتر را عوض نمی‌کند');
      book = r.book;
    });
    const positions = core.positions(book);
    assert.equal(positions.length, lc.final.positions.length, 'شمارِ جایگاه‌ها');
    lc.final.positions.forEach((want, i) => subset(positions[i], want, `جایگاه ${i}`));
    subset(core.bookTotals(book), lc.final.totals, 'جمع‌ها');
    for (const p of positions) {
      const rec = core.reconcile(p);
      assert.ok(rec.ok, `آزمونِ تطبیق: ${p.productId}/${p.warehouseId} ${JSON.stringify(rec)}`);
    }
  });
}

test('stock-v1 دفترِ حرکت (INV-02): علامت، موجودی، فیلتر، خروجی', () => {
  const v = vectors.ledgerView;
  assert.deepEqual(v.docs.map(core.signedQtyMilli), v.signedQtyMilli);
  for (const o of v.onHand) {
    assert.equal(core.onHandMilli(v.docs, o.productId, o.warehouseId), o.qtyMilli, `${o.productId}/${o.warehouseId}`);
  }
  for (const f of v.filters) {
    assert.deepEqual(core.filterDocs(v.docs, f.filter).map((d) => d.id), f.ids, JSON.stringify(f.filter));
  }
  assert.deepEqual(core.EXPORT_HEADER, v.exportHeader);
  assert.deepEqual(core.exportRows(v.docs), v.exportRows);
});

/* ---- فقط JS: چیزهایی که در Kotlin از نظرِ نوع ممکن نیست ---- */

test('JS: مقدار و مبلغِ غیرِ صحیح یا بیرون از ۵۳ بیت رد می‌شود', () => {
  const base = { id: 'x', type: 'sale', productId: 'P', warehouseId: 'W', userId: 'u', at: 1 };
  assert.equal(core.validate(Object.assign({}, base, { qtyMilli: 1.5 })), 'bad_qty');
  assert.equal(core.validate(Object.assign({}, base, { qtyMilli: '1000' })), 'bad_qty');
  assert.equal(core.validate(Object.assign({}, base, { qtyMilli: 2 ** 53 })), 'bad_qty');
  assert.equal(core.validate(Object.assign({}, base, { type: 'purchase_receipt', qtyMilli: 1, costMinor: 0.5 })), 'bad_cost');
  assert.equal(core.validate(Object.assign({}, base, { at: 1.5, qtyMilli: 1 })), 'time_required');
  assert.throws(() => core.mulDivRound(1, 1, 0), RangeError);
  assert.throws(() => core.mulDivRound(0.5, 1, 1), RangeError);
});

test('JS: apply ورودی را تغییر نمی‌دهد (دفتر تغییرناپذیر است)', () => {
  const b0 = core.emptyBook();
  const r1 = core.apply(b0, { id: 'a', type: 'purchase_receipt', productId: 'P', warehouseId: 'W', userId: 'u', at: 1, qtyMilli: 1000, costMinor: 500 });
  const snap = JSON.stringify(core.positions(r1.book));
  const r2 = core.apply(r1.book, { id: 'b', type: 'sale', productId: 'P', warehouseId: 'W', userId: 'u', at: 2, qtyMilli: 400 });
  assert.equal(JSON.stringify(core.positions(r1.book)), snap);
  assert.deepEqual(core.positions(b0), []);
  assert.equal(core.position(r2.book, 'P', 'W').qtyMilli, 600);
  assert.equal(core.position(r2.book, 'P', 'Q'), null);
});

test('JS: replay همان نتیجهٔ apply پشتِ سرِ هم را می‌دهد', () => {
  const docs = [
    { id: 'a', type: 'purchase_receipt', productId: 'P', warehouseId: 'W', userId: 'u', at: 1, qtyMilli: 3000, costMinor: 1000 },
    { id: 'b', type: 'sale', productId: 'P', warehouseId: 'W', userId: 'u', at: 2, qtyMilli: 4000 },
    { id: 'c', type: 'purchase_receipt', productId: 'P', warehouseId: 'W', userId: 'u', at: 3, qtyMilli: 3000, costMinor: 1500 },
  ];
  const r = core.replay(docs, { mode: 'recorded' });
  assert.deepEqual(r.results.map((x) => x.ok), [true, true, true]);
  assert.equal(r.results[1].reviewTask.shortMilli, 1000);
  const p = core.position(r.book, 'P', 'W');
  assert.equal(p.qtyMilli, 2000);
  assert.ok(core.reconcile(p).ok);
});
