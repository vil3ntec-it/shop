/* ==========================================================
   فروشگاه من | مالیاتِ فاکتور — POS-37 (§۶.۱، D-11)
   ----------------------------------------------------------
   هم‌زادِ `core/src/commonMain/.../core/sale/SaleTax.kt`. هر دو با
   `core/test-vectors/sale-tax-v1.json` سنجیده می‌شوند؛ یکی را عوض کنی، دیگری و بردار هم.

   - `clean`: تنظیمِ درست یا null (= خاموش؛ هیچ نرخِ پیش‌فرضی نیست).
   - `ofMinor`: round(پول × نرخ ÷ ۱۰۰۰۰) نیمه رو به بالا به افغانیِ کامل.
   - `compute`: مالیاتِ سبد — «فاکتور» یک نرخ روی (جمع − تخفیف)، «ردیف» نرخِ هر کالا روی سهمِ خودش
     (سهمِ تخفیف با «بیشترین باقی‌مانده»، همان `Loyalty.split` / `sale-caps.split`).
   مبلغ‌ها به پول (یک‌صدمِ افغانی) و نرخ به صدمِ درصد؛ عددِ صحیح، بی اعشارِ شناور.

   خالص است: نه DOM، نه ساعت، نه ذخیره.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidSaleTax = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidSaleTax = api;
})(function () {
  'use strict';

  var FULL_BP = 10000;
  var STEP = 100;
  var MAX_RATES = 200;
  var MAX_EXACT = 9007199254740991;
  var MODES = ['invoice', 'line'];

  function isObj(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
  function rateOk(bp) { return Number.isSafeInteger(bp) && bp >= 0 && bp <= FULL_BP; }
  function int(v) { return Number.isSafeInteger(v) ? v : 0; }

  /** تنظیمِ درست یا null؛ همان قاعدهٔ `SaleTax.clean` */
  function clean(c) {
    if (!isObj(c) || MODES.indexOf(c.mode) < 0 || !rateOk(c.rateBp)) return null;
    var rates = {};
    if (c.mode === 'line' && isObj(c.rates)) {
      var keys = Object.keys(c.rates).filter(function (k) { return k.trim() !== '' && rateOk(c.rates[k]); }).sort();
      keys.slice(0, MAX_RATES).forEach(function (k) { rates[k] = c.rates[k]; });
    }
    var any = c.rateBp > 0 || Object.keys(rates).some(function (k) { return rates[k] > 0; });
    return any ? { mode: c.mode, rateBp: c.rateBp, rates: rates } : null;
  }

  function ofMinor(baseMinor, rateBp) {
    if (!(baseMinor > 0) || !(rateBp > 0)) return 0;
    var halves = (BigInt(baseMinor) * BigInt(rateBp)) / BigInt(FULL_BP * STEP / 2);
    return Number((halves + 1n) / 2n) * STEP;
  }

  /** «بیشترین باقی‌مانده» — همان `Loyalty.split` (برابر ⇒ ردیفِ جلوتر) */
  function split(lines, value) {
    var v = BigInt(value);
    var ls = lines.map(function (x) { return BigInt(x); });
    var whole = ls.reduce(function (a, x) { return a + x; }, 0n);
    if (v === 0n) return lines.map(function () { return 0; });
    var base = ls.map(function (a) { return (v * a) / whole; });
    var rest = ls.map(function (a) { return (v * a) % whole; });
    var left = v - base.reduce(function (a, x) { return a + x; }, 0n);
    var order = ls.map(function (_, i) { return i; })
      .sort(function (a, b) { return rest[b] > rest[a] ? 1 : rest[b] < rest[a] ? -1 : a - b; });
    for (var k = 0; k < order.length && left > 0n; k++) {
      var i = order[k];
      if (base[i] < ls[i]) { base[i] += 1n; left -= 1n; }
    }
    return left === 0n ? base.map(Number) : null;
  }

  /**
   * `lines` = [{productId, grossMinor}]؛ ⇒ {taxMinor, lineTaxMinor} یا null (جمع یا نهاییِ بیرون از ۲^۵۳−۱).
   * تنظیمِ خاموش ⇒ {taxMinor: 0, lineTaxMinor: []}.
   */
  function compute(lines, discountMinor, config) {
    var none = { taxMinor: 0, lineTaxMinor: [] };
    var c = clean(config);
    if (!c) return none;
    var list = Array.isArray(lines) ? lines : [];
    var gross = list.map(function (l) { return Math.max(0, int(isObj(l) ? l.grossMinor : 0)); });
    var whole = 0;
    for (var g = 0; g < gross.length; g++) {
      if (gross[g] > MAX_EXACT || whole > MAX_EXACT - gross[g]) return null;
      whole += gross[g];
    }
    if (whole === 0) return c.mode === 'line' ? { taxMinor: 0, lineTaxMinor: gross.map(function () { return 0; }) } : none;
    var d = Math.min(whole, Math.max(0, int(discountMinor)));
    var r;
    if (c.mode === 'invoice') r = { taxMinor: ofMinor(whole - d, c.rateBp), lineTaxMinor: [] };
    else {
      var shares = split(gross, d);
      if (!shares) return null;
      var each = list.map(function (l, i) {
        var id = isObj(l) && typeof l.productId === 'string' && l.productId.trim() !== '' ? l.productId : null;
        var rate = id !== null && Object.prototype.hasOwnProperty.call(c.rates, id) ? c.rates[id] : c.rateBp;
        return ofMinor(gross[i] - shares[i], rate);
      });
      r = { taxMinor: each.reduce(function (a, x) { return a + x; }, 0), lineTaxMinor: each };
    }
    //  نهایی (جمع − تخفیف + مالیات) هم باید در بازهٔ دقیق بماند
    return r.taxMinor > MAX_EXACT - (whole - d) ? null : r;
  }

  return {
    VERSION: 1, FULL_BP: FULL_BP, STEP: STEP, MAX_RATES: MAX_RATES, MODES: MODES.slice(),
    clean: clean, ofMinor: ofMinor, split: split, compute: compute,
  };
});
