'use strict';
/**
 * فازِ تفاضلیِ هسته‌های دوقلو — دوندهٔ JS (`server/scripts/core-fuzz.js`).
 *
 *  ۱) پیکرهٔ `core/test-vectors/core-fuzz-v1.json` بایت‌به‌بایت همان خروجیِ امروزِ مولّد و هسته‌های JS است؛
 *     هر تغییرِ رفتارِ JS این‌جا می‌شکند و با `--write` پیکره نو می‌شود — آن‌گاه Kotlin
 *     (`CoreFuzzTest.kt` و `CoreFuzzAppTest.kt`) باید عیناً همان را بدهد (روشِ «الف»: JS = expect).
 *  ۲) قاعده‌های گردکردن با BigInt ِ مستقل از هسته (بخشِ ۳.۴ و ۳.۶): نیمه به +∞ با floorDiv،
 *     رد کردنِ هر عددِ بیرون از ۲^۵۳−۱، پخشِ تخفیف بی یک پول کم و زیاد، رفت‌وبرگشتِ تقویم.
 *  اجرای ژرف (بیرون از CI):
 *     CORE_FUZZ_CASES=20000 [CORE_FUZZ_SEED=…] [CORE_FUZZ_OUT=/tmp/deep.json] node --test test/core-fuzz.test.js
 *     سپس همان پرونده: CORE_FUZZ_CORPUS=/tmp/deep.json برای jvmTest ِ core و test ِ desktop.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const F = require('../scripts/core-fuzz.js');

const MAX = 9007199254740991n;
const safe = (x) => x >= -MAX && x <= MAX;
const isInt = (x) => typeof x === 'number' && Number.isInteger(x);
function floorDiv(a, b) { const q = a / b; return (a % b !== 0n && (a < 0n) !== (b < 0n)) ? q - 1n : q; }

/** پاسخِ مستقل یا undefined (قاعده‌ای برای این تابع نیست) */
function oracle(c) {
  const x = c.in;
  const B = (v) => BigInt(v);
  const refuse = { throws: true };
  switch (c.fn) {
    case 'money.roundHalfUp': {
      if (![x.n, x.d].every(isInt) || !safe(B(x.n)) || !safe(B(x.d)) || x.d <= 0) return refuse;
      const s = B(x.n) + B(x.d) / 2n;
      return safe(s) ? Number(floorDiv(s, B(x.d))) : refuse;
    }
    case 'money.lineTotalMinor': {
      if (![x.q, x.p, x.step].every((v) => safe(B(v))) || x.step <= 0) return refuse;
      const raw = B(x.q) * B(x.p), d = 1000n * B(x.step);
      if (!safe(raw) || !safe(d)) return refuse;
      const s = raw + d / 2n;
      if (!safe(s)) return refuse;
      const out = floorDiv(s, d) * B(x.step);
      return safe(out) ? Number(out) : refuse;
    }
    case 'money.cashRounding': {
      if (!safe(B(x.total)) || !safe(B(x.step)) || x.step <= 0) return refuse;
      const s = B(x.total) + B(x.step) / 2n;
      if (!safe(s)) return refuse;
      const rounded = floorDiv(s, B(x.step)) * B(x.step);
      if (!safe(rounded)) return refuse;
      const adj = rounded - B(x.total);
      return !safe(adj) ? refuse : adj === 0n ? null : Number(adj);
    }
    case 'stock.mulDivRound': {
      if (![x.a, x.b, x.c].every((v) => safe(B(v))) || x.c <= 0) return refuse;
      const out = floorDiv(2n * B(x.a) * B(x.b) + B(x.c), 2n * B(x.c));
      return safe(out) ? Number(out) : refuse;
    }
    default: return undefined;
  }
}

/** ناوردایی‌هایی که پاسخِ یکتا ندارند ولی باید برقرار باشند */
function invariants(c, msg) {
  const x = c.in, o = c.out;
  if (c.fn === 'money.allocateDiscount' && !o.throws) {
    assert.equal(o.reduce((a, b) => a + b, 0), x.discount, msg);
    //  سهم مضربِ پله است، پس سقفش مبلغِ ردیف گرد به بالا تا پله (ردیفِ ۱۵۹ افغانی با پلهٔ ۵ تا ۱۶۰ سهم می‌گیرد)
    o.forEach((s, i) => { assert.ok(s >= 0 && s <= Math.ceil(x.lines[i] / x.step) * x.step && s % x.step === 0, msg); });
  }
  if (c.fn === 'cal.toSolar' && o.solar) {
    assert.equal(F.runCase('cal.fromSolar', { y: o.solar.y, m: o.solar.m, d: o.solar.d }).iso, x.iso, msg);
  }
  //  لبهٔ مشترکِ دو سو (نه تفاوت): دی تا حوتِ ۳۱۷۷ در میلادیِ ۳۷۹۹ می‌افتد و toSolar آن را بیرون از بازه می‌شمارد
  if (c.fn === 'cal.fromSolar' && o.iso && x.y < 3177) {
    assert.deepEqual(F.runCase('cal.toSolar', { iso: o.iso }).solar, { y: x.y, m: x.m, d: x.d }, msg);
  }
  if (c.fn === 'money.returnShare') assert.ok(Number.isInteger(o), msg);  // همیشه افغانیِ کامل
  if (c.fn === 'stock.ledger') for (const p of o.positions) assert.ok(p.reconciles, `${msg} ${p.productId}/${p.warehouseId}`);
}

function check(corpus) {
  let checked = 0;
  for (const c of corpus.cases) {
    const msg = `#${c.id} ${c.fn} ${JSON.stringify(c.in).slice(0, 300)}`;
    const want = oracle(c);
    if (want !== undefined) { assert.deepEqual(c.out, want, msg); checked++; }
    invariants(c, msg);
  }
  return checked;
}

test('پیکرهٔ فاز همان خروجیِ امروزِ مولّد و هسته‌های JS است (بایت‌به‌بایت)', () => {
  const want = F.serialize(F.generate(F.SEED, F.COUNT));
  const have = fs.readFileSync(F.CORPUS, 'utf8');
  assert.ok(have === want, 'پیکره کهنه است: اگر تغییرِ رفتارِ JS عمدی است `node server/scripts/core-fuzz.js --write` و همان را در Kotlin هم');
});

test('پیکره: همهٔ خانواده‌ها هستند و هر دو سو چیزی برای رد کردن دارند', () => {
  const corpus = JSON.parse(fs.readFileSync(F.CORPUS, 'utf8'));
  assert.equal(corpus.cases.length, F.COUNT);
  const fams = new Set(corpus.cases.map((c) => c.fn.split('.')[0]));
  assert.deepEqual([...fams].sort(), ['cal', 'cap', 'debt', 'money', 'promo', 'report', 'stock', 'text']);
  assert.ok(corpus.cases.filter((c) => c.out && c.out.throws).length >= 5);
});

test('گردکردن و رد کردن با BigInt ِ مستقل؛ ناورداها', () => {
  const corpus = JSON.parse(fs.readFileSync(F.CORPUS, 'utf8'));
  assert.ok(check(corpus) >= 8);
});

test('مولّد قطعی است: یک دانه، یک پیکره', () => {
  assert.equal(F.serialize(F.generate(77, 40)), F.serialize(F.generate(77, 40)));
  assert.notEqual(F.serialize(F.generate(77, 40)), F.serialize(F.generate(78, 40)));
});

const deep = Number(process.env.CORE_FUZZ_CASES) || 0;
test(`اجرای ژرف (CORE_FUZZ_CASES=${deep || '—'})`, { skip: deep ? false : 'فقط محلی' }, () => {
  const seed = Number(process.env.CORE_FUZZ_SEED) || F.SEED;
  const corpus = F.generate(seed, deep);
  const n = check(corpus);
  if (process.env.CORE_FUZZ_OUT) fs.writeFileSync(process.env.CORE_FUZZ_OUT, F.serialize(corpus));
  assert.ok(n > 0);
});
