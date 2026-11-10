/* ==========================================================
   فروشگاه من | ارزشِ موجودی به بها و سودِ بالقوه (INV-16) — هستهٔ خالصِ وب
   ----------------------------------------------------------
   ‹`valuation`› دوقلوی `core/stock/StockAnalysis.valuation` (Kotlin، commonMain) است: همان قاعده و همان
   عدد، با بردارِ مشترکِ `core/test-vectors/stock-value-v1.json` (JS: `server/test/stock-value-core.test.js`،
   Kotlin: `core/src/jvmTest/.../stock/StockValueVectorsTest.kt`). یکی را بی دیگری و بردار عوض نکن.

   ‹`fromLedger`› ورودیِ همان را از دفترِ برنامه می‌سازد (زبانهٔ «ارزش موجودی» ِ گزارشِ تازهٔ وب):
     • مقدار و ارزشِ هر کالا از بازپخشِ میانگینِ موزونِ متحرکِ هستهٔ گزارش (`report-core.stockCost`، D-23 = الف) —
       ارزش خودِ جمعِ دفتر است، نه مقدار × بهای گردشدهٔ واحد (همان `ValueLine.costValueMinor` ِ برنامه)؛
     • بهای پایهٔ نامعلوم (بازپخش بهایی ندارد) ⇒ `purchasePrice` ِ کالا و «تقریبی» (همان `ReportEngine.stockInsight`)؛
     • کیت موجودیِ خودش را ندارد (اجزایش شمرده می‌شوند) ⇒ نمی‌آید؛ کالای غیرفعالِ بی موجودی هم نه؛
     • فقط کالاهای `products` ِ دفتر، به همان ترتیب. فقط خواندن؛ هیچ چیز نوشته نمی‌شود.

   یکاها: پول به واحدِ کوچک، مقدار به هزارم، درصد به بیسیس‌پوینت؛ هر عدد در ±(2^53−1)، ردیفِ بیرون از آن
   «حساب نشد» (`outOfRange`)، نه عددِ غلط. ES5 (همان قیدِ report-core برای گوشی‌های قدیمی).
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidStockValue = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidStockValue = api;
})(function () {
  'use strict';

  var MAX_EXACT = 9007199254740991;
  var BP = 10000;
  var SMALL = 1125899906842624; // 2^50: تا این‌جا ضرب و تقسیم با Number دقیق است
  var LONG_MAX = '9223372036854775807';

  function isInt(x) { return typeof x === 'number' && isFinite(x) && Math.floor(x) === x; }
  function inRange(x) { return isInt(x) && x >= 0 && x <= MAX_EXACT; }
  function nz(x) { return x === 0 ? 0 : x; }

  /** مقدار × قیمت ÷ ۱۰۰۰ گردِ نیمه به بالا؛ بیرون از بازهٔ دقیق ⇒ −۱ (همان `lineValue`) */
  function lineValue(q, u) {
    if (!inRange(q) || !inRange(u)) return -1;
    if (q === 0 || u === 0) return 0;
    var p = q * u;
    if (p > MAX_EXACT) return -1;
    var r = p % 1000;
    return (p - r) / 1000 + (r >= 500 ? 1 : 0);
  }

  /** جمعِ بی‌سرریز، سقفِ ±MAX_EXACT (همان `satAdd`) */
  function satAdd(a, b) {
    var s = a + b;
    if (s > MAX_EXACT) return MAX_EXACT;
    if (s < -MAX_EXACT) return -MAX_EXACT;
    return nz(s);
  }

  /** floor(n / d) برای d > 0 و |n|، d ≤ 2^50 (دقیق) */
  function floorDivSmall(n, d) {
    var q = Math.floor(n / d), r = n - q * d;
    if (r < 0) q -= 1; else if (r >= d) q += 1;
    return nz(q);
  }

  /**
   *  `part / whole` به بیسیس‌پوینت، گردِ نیمه رو به +∞؛ `whole ≤ ۰` یا `part = ۰` ⇒ ۰ (همان `bpOf`): دقیق تا جایی که
   *  Long ِ Kotlin جا دارد، بیرون از آن همان فرمولِ Double.
   */
  function bpOf(part, whole) {
    if (whole <= 0 || part === 0) return 0;
    var mag = part < 0 ? -part : part;
    var half = Math.floor(whole / 2);
    if (mag * BP <= SMALL && whole <= SMALL) return floorDivSmall(part * BP + half, whole);
    if (typeof BigInt === 'function') {
      var W = BigInt(whole), B = BigInt(BP);
      if (BigInt(mag) <= (BigInt(LONG_MAX) - W) / B) {
        var n = BigInt(part) * B + BigInt(half), q = n / W;
        if (n % W !== BigInt(0) && n < BigInt(0)) q = q - BigInt(1);
        return nz(Number(q));
      }
    }
    return nz(Math.floor(part * BP / whole + 0.5));
  }

  /**
   *  ارزشِ موجودی به بها و به قیمتِ فروش، و سودِ بالقوه — دوقلوی `StockAnalysis.valuation`.
   *  `lines`: `{ id, onHandMilli, unitCostMinor, unitPriceMinor, costValueMinor? }` (بها/قیمتِ ≤ ۰ = «ثبت نشده»؛
   *  `costValueMinor` = ارزشِ دقیقِ همین موجودی به بها، نبود/null ⇒ مقدار × بهای واحد).
   */
  function valuation(lines) {
    var cost = 0, retail = 0, profit = 0, bothRetail = 0, inStock = 0;
    var missingCost = [], missingPrice = [], belowCost = [], negative = [], outOfRange = [], values = [];
    (lines || []).forEach(function (l) {
      var id = String(l.id), q = l.onHandMilli;
      if (q < 0) { negative.push(id); return; }
      if (q === 0) return;
      inStock++;
      var exact = l.costValueMinor === undefined || l.costValueMinor === null ? null : l.costValueMinor;
      var hasCost = l.unitCostMinor > 0 || (exact !== null && exact > 0);
      var hasPrice = l.unitPriceMinor > 0;
      var c = !hasCost ? 0 : (exact !== null ? (inRange(exact) ? exact : -1) : lineValue(q, l.unitCostMinor));
      var r = hasPrice ? lineValue(q, l.unitPriceMinor) : 0;
      if (c < 0 || r < 0) { outOfRange.push(id); return; }
      if (!hasCost) missingCost.push(id);
      if (!hasPrice) missingPrice.push(id);
      values.push({ id: id, costMinor: c, retailMinor: r });
      cost = satAdd(cost, c);
      retail = satAdd(retail, r);
      if (hasCost && hasPrice) {
        profit = satAdd(profit, r - c);
        bothRetail = satAdd(bothRetail, r);
        if (l.unitPriceMinor < l.unitCostMinor) belowCost.push(id);
      }
    });
    return {
      costMinor: cost, retailMinor: retail, potentialProfitMinor: profit,
      marginBp: bothRetail > 0 ? bpOf(profit, bothRetail) : null,
      inStock: inStock, missingCost: missingCost, missingPrice: missingPrice, belowCost: belowCost,
      negative: negative, outOfRange: outOfRange, lines: values,
      complete: missingCost.length === 0 && missingPrice.length === 0 && outOfRange.length === 0,
    };
  }

  /* ---------------- از دفترِ برنامه ---------------- */

  function num(x) {
    var n = typeof x === 'number' ? x : (typeof x === 'string' && x !== '' ? Number(x) : 0);
    return isFinite(n) ? n : 0;
  }
  /** افغانی ⇒ واحدِ کوچک، همان `money` ِ report-core */
  function minorOf(x) { return nz(Math.round(num(x) * 100)); }

  /**
   *  ارزشِ موجودیِ دفترِ خامِ برنامه (`products`، `warehouseEntries`، `sales`، `saleItems`، `returns`) با هستهٔ گزارش
   *  `R` (`TohidReportCore`). ⇒ `{ valuation, rows }`؛ هر ردیف کالای موجودِ حساب‌شده به ترتیبِ `valuation.lines`:
   *  `{ productId, name, qtyMilli, unitCostMinor|null, costMinor, retailMinor, profitMinor|null, approximate }`.
   */
  function fromLedger(raw, R) {
    raw = raw || {};
    var sc = R.stockCost(raw), by = {}, lines = [], meta = {};
    sc.rows.forEach(function (r) { by[r.productId] = r; });
    (Array.isArray(raw.products) ? raw.products : []).forEach(function (p) {
      if (!p) return;
      var id = String(p.id === undefined || p.id === null ? '' : p.id);
      if (id === '' || meta[id] || (Array.isArray(p.kit) && p.kit.length > 0)) return;
      var r = by[id];
      if (!r || (p.active === false && r.qtyMilli <= 0)) return;
      var replayed = r.unitCostMinor !== null && r.unitCostMinor > 0;
      var unit = replayed ? r.unitCostMinor : minorOf(p.purchasePrice);
      meta[id] = { name: r.name || String(p.name || '') || id, approximate: r.approximate === true || !replayed, unit: unit > 0 ? unit : null };
      lines.push({
        id: id, onHandMilli: r.qtyMilli, unitCostMinor: unit, unitPriceMinor: minorOf(p.salePrice),
        costValueMinor: replayed && r.qtyMilli > 0 ? r.valueMinor : null,
      });
    });
    var v = valuation(lines), price = {};
    lines.forEach(function (l) { price[l.id] = l; });
    var rows = v.lines.map(function (x) {
      var l = price[x.id], m = meta[x.id];
      var both = (l.unitCostMinor > 0 || x.costMinor > 0) && l.unitPriceMinor > 0;
      return {
        productId: x.id, name: m.name, qtyMilli: l.onHandMilli, unitCostMinor: m.unit, costMinor: x.costMinor,
        retailMinor: x.retailMinor, profitMinor: both ? x.retailMinor - x.costMinor : null, approximate: m.approximate,
      };
    });
    return { valuation: v, rows: rows };
  }

  return {
    VECTORS_FORMAT: 'stock-value-v1',
    MAX_EXACT: MAX_EXACT,
    valuation: valuation,
    fromLedger: fromLedger,
    _lineValue: lineValue,
    _bpOf: bpOf,
  };
});
