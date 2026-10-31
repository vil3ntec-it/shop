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
  assert.match(body, /TohidMoney\.returnShare\(\s*si\.totalPrice,\s*si\.quantity,\s*si\.returnedQty\s*\|\|\s*0,\s*qty,\s*paidRatio\s*\)/);
  //  فرمولِ پیشین فقط وقتی که money.js ِ کهنهٔ کش returnShare ندارد
  assert.match(body, /typeof TohidMoney\.returnShare === 'function'\s*\?\s*TohidMoney\.returnShare\(/);
  assert.equal(body.split('Math.round((si.totalPrice/si.quantity)').length - 1, 1);
  //  money.js پیش از این کد بار می‌شود (در <head>) و نسخهٔ پرسش‌نامه‌اش بالا رفت تا کشِ کهنه جا نماند
  assert.ok(html.indexOf('license/money.js') < start);
  assert.ok(!html.includes('license/money.js?v=1'), 'money.js?v=1 کهنه است');
});
