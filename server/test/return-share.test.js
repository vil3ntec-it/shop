'use strict';
/**
 * SEC-DATA-01 — برگشتِ تکه‌تکهٔ یک قلم بیش از پولِ گرفته‌شده پس نمی‌دهد (`returnShare` ِ `license/money.js`).
 *
 * همان فایلِ بردارِ `ReturnShareVectorsTest` ِ core (`license/test-vectors/return-share-v1.json`)،
 * و همان ویژگیِ `ReturnShareTest`: جمعِ هر شکستنِ مقدار = برگشتِ یک‌جا = round(جمعِ قلم × سهم).
 * صفحهٔ وب (`index.html`، «مرجوعی جزئی») همین تابع را صدا می‌زند؛ آزمونِ آخر همین را می‌پاید.
 * هیچ پایگاه‌داده‌ای لازم نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const M = require(path.join(ROOT, 'license', 'money.js'));
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'license', 'test-vectors', 'return-share-v1.json'), 'utf8'));

function run(c) {
  let before = 0;
  return c.steps.map((q) => {
    const a = M.returnShare(c.lineTotal, c.quantity, before, q, c.paidRatio);
    if (q > 0) before += q;
    return a;
  });
}

test('همهٔ بردارهای return-share-v1', () => {
  assert.ok(V.cases.length >= 10);
  for (const c of V.cases) assert.deepEqual(run(c), c.amounts, c.name);
});

test('نشت پیش از این: برگشتِ جداجدا بیش از برگشتِ یک‌جا پس می‌داد', () => {
  //  همان فرمولِ پیشینِ index.html و SalesEngine.recordReturn — برای ثبتِ نشت، نه برای رفتار
  //  (مقدار به «قابلِ برگشت» بریده می‌شد، همان کاری که صفحه و موتور هنوز می‌کنند)
  const legacy = (c) => {
    let before = 0;
    return c.steps.map((raw) => {
      const q = Math.min(raw, c.quantity - before);
      if (!(q > 0) || !(c.quantity > 0)) return 0;
      before += q;
      return Math.round((c.lineTotal / c.quantity) * q * c.paidRatio);
    });
  };
  const leaking = V.cases.filter((c) => legacy(c).reduce((s, x) => s + x, 0) > run(c).reduce((s, x) => s + x, 0));
  assert.ok(leaking.length >= 3, 'بردارها باید نشتِ پیشین را نشان دهند');
  for (const c of V.cases) assert.deepEqual(legacy(c), c.legacy, `legacy: ${c.name}`);
});

test('هر شکستنِ مقدار = برگشتِ یک‌جا، و هرگز بیش از round(جمعِ قلم × سهم)', () => {
  let seed = 20261030n;
  const next = (n) => { seed = (seed * 6364136223846793005n + 1442695040888963407n) & 0xFFFFFFFFFFFFFFFFn; return Number((seed >> 33n) % BigInt(n)); };
  for (let i = 0; i < 4000; i++) {
    const units = 1 + next(40);
    const weighed = next(4) === 0;
    const quantity = weighed ? units * 0.25 : units;
    const lineTotal = 1 + next(5000);
    const paidRatio = next(3) === 0 ? 1 : (1 + next(99)) / 100;
    const parts = [];
    let left = units;
    while (left > 0) { const p = 1 + next(left); parts.push(weighed ? p * 0.25 : p); left -= p; }
    const amounts = run({ lineTotal, quantity, paidRatio, steps: parts });
    const oneShot = M.returnShare(lineTotal, quantity, 0, quantity, paidRatio);
    assert.equal(amounts.reduce((s, x) => s + x, 0), oneShot, `${lineTotal}×${paidRatio} / ${quantity} ⇒ ${parts}`);
    assert.equal(oneShot, Math.round(lineTotal * paidRatio));
    assert.ok(amounts.every((x) => x >= 0));
  }
});

test('index.html در «مرجوعی جزئی» همین تابع را صدا می‌زند، نه فرمولِ پیشین', () => {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const start = html.indexOf("document.getElementById('btn-confirm-return')");
  assert.ok(start > 0, 'دکمهٔ مرجوعیِ جزئی پیدا نشد');
  const body = html.slice(start, start + 4000);
  //  مبنا پولِ واقعاً پس‌دادهٔ همین قلم (#586): جمعِ amount و quantity ِ برگشت‌های پیشینِ همان saleItemId
  assert.match(body, /TohidMoney\.returnShare\(\s*si\.totalPrice,\s*si\.quantity,\s*si\.returnedQty\s*\|\|\s*0,\s*qty,\s*paidRatio,\s*priorAmount,\s*priorQty\s*\)/);
  assert.match(body, /const prior = returns\.filter\(r=>r\.saleItemId===si\.id\)/);
  assert.match(body, /priorAmount = prior\.reduce\(\(s,r\)=>s\+\(r\.amount\|\|0\), 0\)/);
  assert.match(body, /priorQty = prior\.reduce\(\(s,r\)=>s\+\(r\.quantity\|\|0\), 0\)/);
  //  حتی با money.js ِ کهنه: هرگز بیش از «پولِ قلم منهای پس‌داده»
  assert.match(body, /Math\.round\(si\.totalPrice \* paidRatio\) - priorAmount/);
  //  فرمولِ پیشین فقط وقتی که money.js ِ کهنهٔ کش returnShare ندارد
  assert.match(body, /typeof TohidMoney\.returnShare === 'function'\s*\?\s*TohidMoney\.returnShare\(/);
  assert.equal(body.split('Math.round((si.totalPrice/si.quantity)').length - 1, 1);
  //  money.js پیش از این کد بار می‌شود (در <head>) و نسخهٔ پرسش‌نامه‌اش بالا رفت تا کشِ کهنه جا نماند
  assert.ok(html.indexOf('license/money.js') < start);
  assert.ok(!html.includes('license/money.js?v=1'), 'money.js?v=1 کهنه است');
});

test('#586 تاریخچهٔ قاطی: برگشت‌های کهنهٔ جداجدا + برگشتِ تازه هرگز بیش از پولِ قلم', () => {
  //  مثالِ بازبین: قلمِ ۲ افغانی برای ۳ عدد، دو برگشتِ کهنهٔ ۱ + ۱ = ۲؛ upTo(3) − upTo(2) یکِ دیگر می‌داد ⇒ ۳ > ۲
  assert.equal(M.returnShare(2, 3, 2, 1, 1, 2, 2), 0);
  //  کمتر پس داده شده (۴ برای ۳ عدد: ۱ + ۱) ⇒ برگشتِ آخر کسری را می‌دهد، جمع ۴
  assert.equal(M.returnShare(4, 3, 2, 1, 1, 2, 2), 2);
  //  ۴ عدد به جمعِ ۶، کهنهٔ ۲ + ۲ ⇒ سومی ۵ − ۴ = ۱ (نه ۵ − ۳ = ۲، که جمعِ آخر را ۷ می‌کرد)
  assert.equal(M.returnShare(6, 4, 2, 1, 1, 4, 2), 1);
  assert.equal(M.returnShare(6, 4, 3, 1, 1, 5, 3), 1);
  //  ردیفِ کهنه بی saleItemId (ردیف‌ها کمتر از returnedQty) ⇒ بخشِ بی‌ردیف «تا پیش از این»، نه صفر
  assert.equal(M.returnShare(500, 2, 1, 1, 1, 0, 0), 250);
  //  تاریخچهٔ یک‌دست همان پنج‌ورودی است؛ مقدارِ نامعتبر هیچ
  assert.equal(M.returnShare(15, 3, 1, 1, 0.9, 5, 1), M.returnShare(15, 3, 1, 1, 0.9));
  assert.equal(M.returnShare(2, 3, 2, -1, 1, 0, 2), 0);

  //  ویژگی (همان ReturnShareTest.anyLegacyPrefixThenNewNeverExceedsTheLine ِ core)
  let seed = 5860586n;
  const next = (n) => { seed = (seed * 6364136223846793005n + 1442695040888963407n) & 0xFFFFFFFFFFFFFFFFn; return Number((seed >> 33n) % BigInt(n)); };
  for (let i = 0; i < 3000; i++) {
    const quantity = 1 + next(30);
    const lineTotal = 1 + next(3000);
    const paidRatio = next(3) === 0 ? 1 : (1 + next(99)) / 100;
    const legacyUnits = next(quantity + 1);
    const legacyOne = Math.round((lineTotal / quantity) * 1 * paidRatio);
    let refunded = legacyUnits * legacyOne;
    let before = legacyUnits;
    while (before < quantity) {
      const q = Math.min(1 + next(4), quantity - before);
      const a = M.returnShare(lineTotal, quantity, before, q, paidRatio, refunded, before);
      assert.ok(a >= 0);
      refunded += a; before += q;
    }
    const line = Math.round(lineTotal * paidRatio);
    const expected = legacyUnits === quantity ? legacyUnits * legacyOne : Math.max(line, legacyUnits * legacyOne);
    assert.equal(refunded, expected, `${lineTotal}×${paidRatio} / ${quantity}، کهنه ${legacyUnits}`);
  }
});
