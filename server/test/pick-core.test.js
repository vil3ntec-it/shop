'use strict';
/**
 * PCK — منطقِ خالصِ جمع‌آوری (`lib/pick-core.js`) در برابرِ بردارِ مرجعِ
 * `core/test-vectors/pick-v1.json` (همان که `PickCore.kt` می‌خواند)، و برابریِ گردکردن با
 * `order-states.lineTotalMinor` (یک قاعده برای سفارش و برگهٔ جمع‌آوری).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const P = require('../src/lib/pick-core');
const S = require('../src/lib/order-states');

const V = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'pick-v1.json'), 'utf8'));

test('pick-v1: گردکردنِ مبلغِ قلم همان order-states است', () => {
  assert.ok(V.lineTotal.length >= 5);
  for (const c of V.lineTotal) {
    assert.equal(P.lineTotalMinor(c.qtyMilli, c.unitPriceMinor), c.want, JSON.stringify(c));
    assert.equal(S.lineTotalMinor(c.qtyMilli, c.unitPriceMinor), c.want, `order-states ${JSON.stringify(c)}`);
  }
});

test('PCK-01 pick-v1: ترتیبِ برگه به جای قفسه', () => {
  for (const c of V.pickOrder) assert.deepEqual(P.pickOrder(c.lines), c.want, c.name);
});

test('PCK-01: ترتیب پایدار است — جابه‌جاییِ ورودی همان خروجی', () => {
  for (const c of V.pickOrder) assert.deepEqual(P.pickOrder([...c.lines].reverse()), c.want, c.name);
});

test('PCK-02 pick-v1: فقط بارکدِ همان کالا', () => {
  for (const c of V.barcode) assert.equal(P.barcodeMatches(c.barcodes, c.scanned), c.want, JSON.stringify(c));
});

test('PCK-04 pick-v1: واحدِ وزنی و حدِ مجازِ وزن', () => {
  for (const c of V.weighedUnit) assert.equal(P.isWeighedUnit(c.unit), c.want, JSON.stringify(c));
  for (const c of V.weigh) assert.equal(P.weighVerdict(c.orderedMilli, c.actualMilli, c.toleranceBp), c.want, JSON.stringify(c));
});

test('PCK-07 pick-v1: جمعِ تازه و کسرِ خودکار', () => {
  for (const c of V.settle) assert.deepEqual(P.settle(c.input), c.want, c.name);
});

test('PCK-07: مشتری هرگز بیش از جمعِ سفارشِ اولیه + حدِ وزن نمی‌پردازد و جمع هرگز منفی نیست', () => {
  for (const c of V.settle) {
    const out = P.settle(c.input);
    assert.ok(out.totalMinor >= 0);
    assert.ok(out.discountMinor <= out.subtotalMinor);
  }
});

test('PCK-13 pick-v1: شاخص‌ها بی عددِ ساختگی', () => {
  for (const c of V.metrics) assert.deepEqual(P.metrics(c.picks), c.want, c.name);
});
