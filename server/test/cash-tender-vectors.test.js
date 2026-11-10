'use strict';
/**
 * CHK-14 — «با کدام اسکناس پرداخت می‌کنید؟»: بردارِ مشترکِ `core/test-vectors/cash-tender-v1.json`.
 *
 *   ۱) تابعِ `tenders` ِ ویترین (`public/store/checkout.js`، همان متنِ پرونده در vm) برای هر جمع همان گزینه‌ها را می‌دهد.
 *   ۲) ⛔ هر گزینه را قاعدهٔ سرور (`lib/delivery-prefs.checkTender`) برای ارسال + نقد می‌پذیرد؛ اسکناسِ یکی بالاتر
 *      (پول خرد ≥ ۱۰۰۰) و کمتر از جمع رد می‌شوند — پس نه وب و نه برنامه مبلغی نمی‌فرستند که سرور رد کند.
 *   همان فایل را `core/src/jvmTest/.../shopper/ShopperTenderVectorsTest.kt` (فقط CI) روی `CheckoutRules.tenderOptions` ِ
 *   برنامهٔ خرید می‌دواند؛ یکی عوض شد، دو دیگر هم.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const prefs = require('../src/lib/delivery-prefs');

const V = require(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'cash-tender-v1.json'));

/** همان `function tenders(total) {…}` ِ checkout.js، بی هیچ رونوشتِ دستی */
function webTenders() {
  const src = fs.readFileSync(path.join(__dirname, '..', 'public', 'store', 'checkout.js'), 'utf8');
  const m = /var NOTE_MINOR = (\d+);[^\n]*\n\s*(function tenders\(total\) \{[\s\S]*?\n\s*return out\.sort\([^\n]*\n\s*\})/.exec(src);
  assert.ok(m, 'تابعِ tenders در checkout.js پیدا نشد — اگر جابه‌جا شد، همین آزمون را هم به‌روز کنید');
  const ctx = { NOTE_MINOR: Number(m[1]) };
  vm.runInNewContext(`${m[2]}\nthis.tenders = tenders;`, ctx);
  return { tenders: ctx.tenders, note: ctx.NOTE_MINOR };
}

test('گزینه‌های ویترین همان بردار است', () => {
  const w = webTenders();
  assert.equal(w.note, V.noteMinor);
  assert.equal(prefs.LARGEST_NOTE_MINOR, V.noteMinor, 'بزرگ‌ترین اسکناسِ سرور همان');
  assert.ok(V.cases.length >= 15);
  for (const c of V.cases) {
    //  وب فقط برای جمعِ مثبت می‌پرسد (`total > 0 ? tenders(total) : []`)
    const got = c.totalMinor > 0 ? Array.from(w.tenders(c.totalMinor)) : [];
    assert.deepEqual(got, c.options, `جمع ${c.totalMinor}`);
  }
});

test('⛔ هر گزینه را سرور می‌پذیرد؛ پول خردِ ۱۰۰۰ یا کمتر از جمع رد', () => {
  const at = (totalMinor) => ({ fulfillment: 'delivery', paymentMethod: 'cod', totalMinor });
  const code = (fn) => { try { fn(); return 'ok'; } catch (e) { return e.code; } };
  for (const c of V.cases) {
    for (const x of c.options) {
      assert.equal(prefs.parseTender(x), x, `${c.totalMinor}: ${x}`);
      assert.equal(code(() => prefs.checkTender(x, at(c.totalMinor))), 'ok', `${c.totalMinor}: ${x}`);
      assert.ok(x > c.totalMinor, 'برابرِ جمع ⇒ «پول خرد لازم نیست»، نه گزینه');
    }
    if (c.totalMinor <= 0) continue;
    assert.equal(code(() => prefs.checkTender(c.totalMinor + V.noteMinor, at(c.totalMinor))), 'cash_tender_excess');
    assert.equal(code(() => prefs.checkTender(c.totalMinor, at(c.totalMinor + 100))), 'cash_tender_short');
    assert.equal(code(() => prefs.checkTender(c.options[0], { ...at(c.totalMinor), fulfillment: 'pickup' })), 'bad_cash_tender');
  }
});
