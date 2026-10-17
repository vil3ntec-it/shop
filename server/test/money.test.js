'use strict';
/**
 * پول و مقدار (بخشِ ۳.۴) — سمتِ وب: `license/money.js`.
 *
 * همان فایلِ بردارِ مرجعی را می‌خواند که `MoneyTest.kt`ِ اندروید و کامپیوتر
 * می‌خواند (`license/test-vectors/money-v1.json`). سه ادعا:
 *  ۱. فایل دقیقاً خروجیِ امروزِ `lineTotal`ِ `index.html` و قاعدهٔ ۳.۴ است (کهنه نیست).
 *  ۲. `license/money.js` روی همهٔ بردارها همان عددِ قاعده را می‌دهد.
 *  ۳. موتورِ امروز و قاعدهٔ تازه فقط در نیمه‌های دقیق فرق دارند، و فهرستِ
 *     همهٔ فرق‌ها در بازهٔ جاروب همان است که در فایل ثبت شده (تغییرِ رفتارِ
 *     مستند، نه بی‌صدا).
 * هیچ پایگاه‌داده‌ای لازم نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const M = require(path.join(__dirname, '..', '..', 'license', 'money.js'));
const gen = require('../scripts/money-vectors');

const V = JSON.parse(fs.readFileSync(gen.FIXTURE, 'utf8'));
const legacy = gen.webLineTotal();
const legacyMinor = (q, p) => Math.round(legacy(p / 100, q / 1000) * 100) + 0;
const isTie = (q, p) => Math.abs((q * p) % 100000) === 50000;

test('بردارها همان خروجیِ امروزِ موتورِ وب و قاعدهٔ ۳.۴اند (فایل کهنه نیست)', () => {
  assert.equal(fs.readFileSync(gen.FIXTURE, 'utf8'), gen.serialize(gen.build()),
    'بردارها کهنه‌اند: cd server && node scripts/money-vectors.js --write');
});

test('جدولِ ارزها با بردارها یکی است', () => {
  const table = Object.keys(M.CURRENCIES).map((k) => {
    const c = M.CURRENCIES[k];
    return { code: c.code, decimals: c.decimals, stepMinor: c.stepMinor };
  });
  assert.deepEqual(table, V.currencies);
  assert.equal(M.currency('AFN').stepMinor, 100);
  assert.equal(M.currency('AFN', 500).stepMinor, 500);
  assert.equal(M.currency('AFN').minorPerMajor, 100);
  assert.throws(() => M.currency('XYZ'), RangeError);
});

test('مبلغِ ردیف: money.js روی همهٔ بردارها همان قاعده را می‌دهد', () => {
  for (const [q, p, step, , minor] of V.lineTotal.rows) {
    assert.equal(M.lineTotalMinor(q, p, step), minor, `${q} × ${p} (پله ${step})`);
    const t = M.lineTotal(M.qty(q), M.money(p, M.currency('AFN', step)));
    assert.equal(t.minor, minor);
    assert.equal(t.currency.code, 'AFN');
  }
  // نمونهٔ مشخصات: ۱٫۵ × ۱۱۵ = ۱۷۲٫۵ ⇒ ۱۷۳ ؛ منفی: −۱۷۲٫۵ ⇒ −۱۷۲ (نیمه به سمتِ +∞)
  assert.equal(M.lineTotalMinor(1500, 11500, 100), 17300);
  assert.equal(M.lineTotalMinor(-1500, 11500, 100), -17200);
  assert.equal(M.lineTotalMinor(1500, -11500, 100), -17200);
});

test('موتورِ امروز (lineTotalِ index.html) = ستونِ legacy؛ فرق فقط در نیمه‌های دقیق و فقط یک افغانی', () => {
  let diffs = 0;
  for (const [q, p, step, old, minor] of V.lineTotal.rows) {
    if (old === null) continue;
    assert.equal(step, 100);
    assert.equal(legacyMinor(q, p), old, `${q} × ${p}`);
    if (old !== minor) {
      diffs++;
      assert.ok(isTie(q, p), `فرقِ بی‌نیمه: ${q} × ${p}`);
      assert.equal(minor - old, 100, 'قاعده فقط نیمه را بالا می‌برد');
    }
  }
  assert.ok(diffs > 0, 'بردارها باید نمونهٔ فرق را هم داشته باشند');
});

test('جاروبِ کامل: همهٔ فرق‌های موتورِ امروز با قاعده همان فهرستِ ثبت‌شده است', () => {
  const { priceMinor, qtyMilli, stepMinor } = V.sweep;
  const found = [];
  for (let p = priceMinor[0]; p <= priceMinor[1]; p++) {
    for (let q = qtyMilli[0]; q <= qtyMilli[1]; q++) {
      const a = legacyMinor(q, p);
      const b = M.lineTotalMinor(q, p, stepMinor);
      if (a !== b) {
        assert.ok(isTie(q, p), `فرقِ بی‌نیمه: ${q} × ${p}`);
        found.push([q, p, a, b]);
      }
    }
  }
  assert.deepEqual(found, V.sweep.diffs);
});

test('بیرون از بازهٔ دقیق خطا می‌دهد، نه گردکردنِ بی‌صدا', () => {
  for (const [q, p, step] of V.lineTotalOverflow.rows) {
    assert.throws(() => M.lineTotalMinor(q, p, step), RangeError, `${q} × ${p}`);
  }
  assert.throws(() => M.money(2 ** 53, 'AFN'), RangeError);
  assert.throws(() => M.money(1.5, 'AFN'), RangeError);
  assert.throws(() => M.qty(0.5), RangeError);
});

test('مرزِ دادهٔ امروز: fromLegacy همان Math.round است', () => {
  for (const [x, minor] of V.fromLegacy.money) assert.equal(M.moneyFromLegacy(x, 'AFN').minor, minor, String(x));
  for (const [x, milli] of V.fromLegacy.qty) assert.equal(M.qtyFromLegacy(x).milli, milli, String(x));
  assert.ok(Object.is(M.moneyFromLegacy(-0.004, 'AFN').minor, 0), '-0 نباید بماند');
  assert.throws(() => M.moneyFromLegacy(NaN, 'AFN'), RangeError);
  assert.throws(() => M.qtyFromLegacy(Infinity), RangeError);
});

test('تخفیفِ فاکتور: بزرگ‌ترین باقی‌مانده، جمعِ دقیق، مضربِ پله', () => {
  for (const c of V.allocateDiscount) {
    const cur = M.currency('AFN', c.stepMinor);
    const shares = M.allocateDiscount(M.money(c.discount, cur), c.lines.map((l) => M.money(l, cur)));
    const got = shares.map((s) => s.minor);
    assert.deepEqual(got, c.shares, JSON.stringify(c));
    assert.equal(got.reduce((a, b) => a + b, 0), c.discount);
    got.forEach((s, i) => {
      assert.equal(s % c.stepMinor, 0);
      assert.ok(s >= 0 && s <= c.lines[i]);
      if (c.lines[i] === 0) assert.equal(s, 0);
    });
  }
  const afn = (v) => M.money(v, 'AFN');
  assert.throws(() => M.allocateDiscount(afn(500), [afn(100), afn(300)]), RangeError);   // بیش از جمع
  assert.throws(() => M.allocateDiscount(afn(150), [afn(1000)]), RangeError);            // مضربِ پله نیست
  assert.throws(() => M.allocateDiscount(afn(100), [afn(-100), afn(300)]), RangeError);  // ردیفِ منفی
  assert.throws(() => M.allocateDiscount(afn(100), [M.money(300, 'USD')]), (e) => e.code === 'CURRENCY_MISMATCH');
});

test('گردکردنِ نقدی: ردیفی جدا با نوعِ rounding', () => {
  const afn = M.currency('AFN');
  for (const [t, step, adjust] of V.cashRounding.rows) {
    const line = M.cashRounding(M.money(t, afn), step);
    if (adjust === 0) { assert.equal(line, null, `${t} / ${step}`); continue; }
    assert.equal(line.type, 'rounding');
    assert.equal(line.type, M.ROUNDING_LINE_TYPE);
    assert.equal(line.amount.minor, adjust, `${t} / ${step}`);
    assert.equal(((t + adjust) % step) + 0, 0);
    assert.ok(Math.abs(adjust) * 2 <= step);
  }
  // ۱۷۲٫۵ افغانی به پلهٔ ۵ ⇒ ۱۷۵ (ردیفِ +۲٫۵)
  assert.equal(M.cashRounding(M.money(17250, afn), 500).amount.minor, 250);
});

test('دو ارزِ متفاوت خطای زمانِ اجراست؛ جمع و مقایسه دقیق‌اند', () => {
  const a = M.money(17300, 'AFN');
  const b = M.money(200, 'AFN');
  assert.equal(M.add(a, b).minor, 17500);
  assert.equal(M.sub(b, a).minor, -17100);
  assert.ok(Object.is(M.neg(M.money(0, 'AFN')).minor, 0));
  assert.equal(M.compare(a, b), 1);
  assert.equal(M.compare(b, a), -1);
  assert.equal(M.compare(a, M.money(17300, M.currency('AFN', 500))), 0, 'هویتِ ارز فقط کد است');
  const usd = M.money(1, 'USD');
  for (const f of [M.add, M.sub, M.compare]) assert.throws(() => f(a, usd), (e) => e.code === 'CURRENCY_MISMATCH');
  assert.throws(() => M.add(M.money(M.MAX_EXACT, 'AFN'), M.money(1, 'AFN')), RangeError);
});

test('floorDiv و roundHalfUp با BigInt یکی‌اند، حتی نزدیکِ ۲^۵۳ و برای منفی', () => {
  let s = 12345;
  const rnd = () => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648; };
  const big = (a, b) => { const q = a / b; return (a % b !== 0n && (a < 0n) !== (b < 0n)) ? q - 1n : q; };
  const samples = [[-1, 2], [1, 2], [-3, 2], [M.MAX_EXACT, 100000], [-M.MAX_EXACT, 100000], [M.MAX_EXACT - 1, 3], [7, -2], [-7, -2]];
  for (let i = 0; i < 2000; i++) {
    const a = Math.floor((rnd() * 2 - 1) * M.MAX_EXACT);
    const b = 1 + Math.floor(rnd() * (i % 2 ? 100000 : 2 ** 40));
    samples.push([a, i % 3 ? b : -b]);
  }
  for (const [a, b] of samples) {
    assert.equal(M.floorDiv(a, b), Number(big(BigInt(a), BigInt(b))), `${a} / ${b}`);
  }
  assert.equal(M.roundHalfUp(5, 2), 3);    //  ۲٫۵ ⇒ ۳
  assert.equal(M.roundHalfUp(-5, 2), -2);  // −۲٫۵ ⇒ −۲
  assert.equal(M.roundHalfUp(7, 5), 1);    //  ۱٫۴ ⇒ ۱ (مقسوم‌علیهِ فرد)
  assert.equal(M.roundHalfUp(8, 5), 2);    //  ۱٫۶ ⇒ ۲
  assert.throws(() => M.roundHalfUp(1, 0), RangeError);
});
