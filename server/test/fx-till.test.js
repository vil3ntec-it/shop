'use strict';
/**
 * PAY-10 — آینهٔ JS ِ `FxTillVectorsTest.kt`: `license/fx-till.js` و تسویه با ارز در `license/pay-core.js` روی
 * `core/test-vectors/fx-till-v1.json`. هر دو هم‌زاد همان بردار را می‌دهند؛ بردارِ عوض‌شده بی هم‌خوان کردنِ کاتلین
 * همین‌جا یا در CI ِ کاتلین سرخ می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const F = require(path.join(ROOT, 'license', 'fx-till.js'));
const P = require(path.join(ROOT, 'license', 'pay-core.js'));
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'fx-till-v1.json'), 'utf8'));

test('ثابت‌ها با بردار یکی‌اند', () => {
  assert.deepEqual(V.constants, {
    base: F.BASE, maxRates: F.MAX_RATES, maxText: F.MAX_TEXT, maxAgeMs: F.MAX_AGE_MS, skewMs: F.SKEW_MS,
    per: P.FX_PER, defaultPer: { ...F.DEFAULT_PER },
  });
  assert.equal(P.FX, 'fx');
});

test('جدولِ نرخِ دکان: ارز، نرخ، واحد، منبع، زمان، گذارنده', () => {
  assert.ok(V.rates.length >= 20);
  for (const c of V.rates) assert.deepEqual(F.rates(c.input), c.expect, c.name);
});

test('پرداختِ ارز با نرخِ جدول: رکوردِ ثابت، نرخِ کهنه رد', () => {
  for (const c of V.tender) {
    const t = F.rates(c.rates);
    assert.ok(t.ok, c.name);
    const x = c.input;
    assert.deepEqual(F.tender(t.rates, x.currency, x.amount, x.now, x.maxAgeMs), c.expect, c.name);
  }
});

test('تسویه با ارز: فروش به افغانی، ارز یک ردیفِ fx با رکوردش، پولِ پس افغانی', () => {
  for (const c of V.settle) assert.deepEqual(P.settle(c.input), c.expect, c.name);
  for (const c of V.exactCash) assert.deepEqual(P.exactCash(c.input), c.expect, c.name);
});

test('جمعِ ردیف‌ها همان مبلغِ فاکتور است و پولِ پس = دریافتی − قابلِ پرداخت', () => {
  for (const c of V.settle) {
    const s = c.expect;
    if (!s.ok || s.status === 'short') continue;
    const rows = s.payments.reduce((a, p) => a + p.amount, 0);
    assert.equal(rows, s.due, c.name);
    assert.equal(s.change, s.tendered - (s.due - s.toAccount), c.name);
    for (const p of s.payments.filter((r) => r.method === 'fx')) assert.ok(p.amount > 0 && p.amount <= p.fx.to.amount, c.name);
  }
});

test('متن ⇐⇒ واحدِ کوچک', () => {
  for (const [t, c, want] of V.parseAmount) assert.equal(F.parseAmount(t, c), want, `parseAmount(${JSON.stringify(t)}, ${c})`);
  for (const [m, c, want] of V.format) assert.equal(F.format(m, c), want, `format(${m}, ${c})`);
  for (const [r, p, c, want] of V.rateLine) assert.equal(F.rateLine(r, p, c), want, `rateLine(${r}, ${p}, ${c})`);
});

test('نرخِ تازه رکوردِ پیشین را عوض نمی‌کند', () => {
  const at = 1760000000000;
  const old = F.rates([{ currency: 'USD', rate: 7025, per: 1, source: 'x', at, by: 'u' }]).rates;
  const rec = F.tender(old, 'USD', 1000, at + 1).record;
  const fresh = F.rates([{ currency: 'USD', rate: 7400, per: 1, source: 'x', at: at + 2, by: 'u' }]).rates;
  assert.equal(F.tender(fresh, 'USD', 1000, at + 3).record.to.amount, 74000);
  const s = P.settle({ total: 50000, fx: [rec] });
  assert.equal(s.payments[0].fx.to.amount, 70300);
  assert.equal(s.change, 20300);
});

test('هسته خالص است: نه ساعت، نه DOM، نه ذخیره', () => {
  const src = fs.readFileSync(path.join(ROOT, 'license', 'fx-till.js'), 'utf8');
  for (const bad of [/Date\.now|new Date/, /localStorage|document\.|fetch\(/, /Math\.random/]) assert.ok(!bad.test(src), String(bad));
});
