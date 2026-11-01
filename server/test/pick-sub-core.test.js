'use strict';
/**
 * PCK-05/06 — منطقِ خالصِ جایگزینی (`lib/pick-core.js`) در برابرِ بردارِ مرجعِ
 * `core/test-vectors/pick-sub-v1.json` (همان که `PickSubstituteVectorsTest` با `PickSubstitute.kt` می‌خواند).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const P = require('../src/lib/pick-core');

const V = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'pick-sub-v1.json'), 'utf8'));

test('pick-sub-v1: نسخه و پوششِ هر بخش', () => {
  assert.equal(V.version, 1);
  for (const k of ['block', 'charge', 'rank', 'timeout', 'proposable', 'settle']) assert.ok(V[k].length >= 1, k);
  const codes = new Set(V.block.map((c) => c.want));
  for (const c of [null, 'same_product', 'not_found', 'category_unknown', 'category_mismatch', 'subcategory_mismatch',
    'halal_mismatch', 'unit_mismatch', 'no_price']) assert.ok(codes.has(c), `بردار برای ${c}`);
});

test('PCK-06 pick-sub-v1: فقط جایگزینِ امن — نه دستهٔ دیگر، نه حلال با غیرحلال/نامعلوم، نه وزنی با شمارشی', () => {
  for (const c of V.block) assert.equal(P.substituteBlock(c.orig, c.cand), c.want, c.name);
});

test('PCK-06 pick-sub-v1: قیمتِ شفاف — قیمتِ واحدِ کمتر، با همان گردکردن', () => {
  for (const c of V.charge) {
    assert.deepEqual(P.substituteCharge(c.origUnitPriceMinor, c.candUnitPriceMinor, c.qtyMilli), c.want, JSON.stringify(c));
  }
});

test('PCK-06: جمعِ جایگزین هرگز بیش از قلمِ اصل نیست (مقدار ≤ سفارش)', () => {
  for (let i = 0; i < 400; i++) {
    const ordered = 1 + ((i * 7919) % 20000);
    const qty = 1 + ((i * 104729) % ordered);
    const orig = 1 + ((i * 15485863) % 900000);
    const cand = 1 + ((i * 32452843) % 900000);
    const c = P.substituteCharge(orig, cand, qty);
    assert.ok(c.chargedUnitPriceMinor <= orig && c.chargedUnitPriceMinor <= cand);
    assert.ok(c.totalMinor <= P.lineTotalMinor(ordered, orig), JSON.stringify({ ordered, qty, orig, cand }));
  }
});

test('PCK-05 pick-sub-v1: دو پیشنهاد — هم‌برند، نزدیک‌ترین قیمت، نام، شناسه؛ ناامن هرگز', () => {
  for (const c of V.rank) {
    assert.deepEqual(P.rankSubstitutes(c.orig, c.cands, c.limit), c.want, c.name);
    assert.deepEqual(P.rankSubstitutes(c.orig, [...c.cands].reverse(), c.limit), c.want, `پایدار: ${c.name}`);
  }
});

test('PCK-05 pick-sub-v1: پس از مهلت ترجیحِ مشتری؛ «جایگزین نه» پیشنهاد نمی‌گیرد', () => {
  for (const c of V.timeout) assert.equal(P.timeoutDecision(c.preference), c.want, c.preference);
  for (const c of V.proposable) assert.equal(P.proposable(c.preference), c.want, c.preference);
  for (const p of P.PREFERENCES) assert.ok(['accept', 'reject'].includes(P.timeoutDecision(p)));
});

test('PCK-05/07 pick-sub-v1: جمعِ تازه با جایگزین — قیمتِ کمتر، «تغییر» حتی هم‌قیمت، کسرِ خودکار', () => {
  for (const c of V.settle) assert.deepEqual(P.settle(c.input), c.want, c.name);
});
