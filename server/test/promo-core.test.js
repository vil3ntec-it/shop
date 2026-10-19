'use strict';
/**
 * هستهٔ تخفیف (PRM-01..09، 11، 12، 17) — بردارهای مشترکِ `core/test-vectors/promo-v1.json`.
 *
 * همان فایل را `PromoCore.kt` (core/commonMain) در آزمونِ اندروید و کامپیوتر
 * می‌دواند. این‌جا: `license/promo-core.js` (وب) روی همهٔ بردارها، و دو ویژگیِ
 * پولی روی سبدهای تصادفی (§۱۶ بندِ ۳): جمعِ تخفیفِ ردیف‌ها = تخفیفِ کل، و
 * هیچ ردیفی زیرِ بها یا زیرِ صفر نمی‌رود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const P = require(path.join(ROOT, 'license', 'promo-core.js'));
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'promo-v1.json'), 'utf8'));

test('ثابت‌ها همان بردارند', () => {
  assert.equal(P.VERSION, V.version);
  assert.deepEqual(P.KINDS, V.kinds);
  assert.deepEqual(P.STACKS, V.stacks);
  assert.deepEqual(P.CHANNELS, V.channels);
});

test('normalizeCode', () => {
  for (const c of V.normalizeCode) assert.equal(P.normalizeCode(c.in), c.out, JSON.stringify(c));
});

test('isoDow', () => {
  for (const c of V.isoDow) assert.equal(P.isoDow(c.date), c.out, JSON.stringify(c));
});

test('بازهٔ زمانی (PRM-09)', () => {
  for (const c of V.window) assert.equal(P.inWindow(c.window, c.at), c.out, JSON.stringify(c));
});

test('اعتبارسنجی (PRM-17)', () => {
  for (const c of V.validate) assert.deepEqual(P.validate(c.rule, c.step), c.out, JSON.stringify(c));
});

for (const c of V.evaluate) {
  test(`evaluate — ${c.name}`, () => {
    if (c.error) { assert.throws(() => P.evaluate(c.cart, c.rules)); return; }
    assert.deepEqual(P.evaluate(c.cart, c.rules), c.out);
  });
}

test('ruleOf پایدار است (دو بار = یک بار)', () => {
  for (const c of V.evaluate) for (const r of c.rules) assert.deepEqual(P.ruleOf(P.ruleOf(r)), P.ruleOf(r));
});

// ── ویژگی‌محور ─────────────────────────────────────────────
function rng(seed) {
  let s = seed >>> 0;
  return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
}

test('ویژگی: جمعِ سهم‌ها = تخفیفِ کل؛ هیچ ردیفی زیرِ بها/صفر نمی‌رود', () => {
  const rand = rng(20261019);
  const pick = (a) => a[Math.floor(rand() * a.length)];
  const int = (lo, hi) => lo + Math.floor(rand() * (hi - lo + 1));
  const cats = ['drinks', 'snacks', 'fruit'];
  for (let n = 0; n < 3000; n++) {
    const lines = [];
    const count = int(1, 6);
    for (let i = 0; i < count; i++) {
      const unit = int(1, 500) * 100 + (rand() < 0.2 ? int(1, 99) : 0);
      lines.push({
        key: 'l' + i, productId: 'P' + int(1, 5), qtyMilli: rand() < 0.2 ? int(100, 5000) : int(1, 9) * 1000,
        unitMinor: unit, costMinor: rand() < 0.3 ? 0 : Math.floor(unit * (0.3 + rand() * 0.8)),
        category: pick(cats), noPromo: rand() < 0.05,
      });
    }
    const rules = [];
    const rc = int(0, 4);
    for (let i = 0; i < rc; i++) {
      const kind = pick(P.KINDS);
      const r = { id: 'r' + i, name: 'r' + i, kind, stack: pick(P.STACKS), priority: int(0, 3), allowBelowCost: rand() < 0.2 };
      if (rand() < 0.3) r.scope = { categories: [pick(cats)] };
      if (kind === 'percent') r.bp = int(1, 10000);
      if (kind === 'amount') r.amountMinor = int(1, 50) * 100;
      if (kind === 'price') r.prices = [{ productId: 'P' + int(1, 5), unitMinor: int(0, 300) * 100 }];
      if (kind === 'tiers') r.tiers = [{ min: 2000, bp: 500 }, { min: 5000, bp: 1500 }];
      if (kind === 'bxgy') { r.buy = int(1, 3); r.get = int(1, 2); r.bp = pick([5000, 10000]); }
      if (kind === 'bundle') { r.qty = int(2, 4); r.priceMinor = int(1, 600) * 100; }
      if (kind === 'threshold') r.tiers = rand() < 0.5 ? [{ min: 10000, bp: 500 }, { min: 50000, bp: 1000 }] : [{ min: 20000, amountMinor: 2000 }];
      rules.push(r);
    }
    const out = P.evaluate({ lines }, rules);
    const sumLines = out.lines.reduce((s, l) => s + l.discountMinor, 0);
    const sumApplied = out.applied.reduce((s, a) => s + a.discountMinor, 0);
    assert.equal(sumLines, out.discountMinor);
    assert.equal(sumApplied, out.discountMinor);
    assert.equal(out.totalMinor, out.subtotalMinor - out.discountMinor);
    const anyBelow = rules.some((r) => r.allowBelowCost);
    out.lines.forEach((l, i) => {
      const src = lines[i];
      assert.ok(l.discountMinor >= 0 && l.discountMinor % 100 === 0, JSON.stringify(l));
      assert.ok(l.netMinor >= 0, JSON.stringify({ l, rules }));
      if (src.noPromo) assert.equal(l.discountMinor, 0);
      if (!anyBelow && l.discountMinor > 0 && src.costMinor > 0) {
        assert.ok(l.netMinor * 1000 >= src.qtyMilli * src.costMinor, JSON.stringify({ l, src, rules }));
      }
    });
    if (rules.length === 0) assert.equal(out.discountMinor, 0);
  }
});
