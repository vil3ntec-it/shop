/* ==========================================================
   فروشگاه من | پول و مقدار — بخشِ ۳.۴ِ مشخصات
   ----------------------------------------------------------
   هم‌زادِ `android-native/.../core/money/{Money,Rounding}.kt`. هر دو با یک
   فایلِ بردارِ مرجع (`license/test-vectors/money-v1.json`) سنجیده می‌شوند؛
   یکی را عوض کنی، دیگری هم.

   پول عددِ صحیح است به «واحدِ کوچک» (پول = یک‌صدمِ افغانی) و مقدار عددِ صحیح
   به «هزارم». همهٔ عددها و حاصل‌های میانی زیرِ ۲^۵۳−۱ می‌مانند؛ بیرون از آن
   RangeError (کاتلین: ArithmeticException) — هیچ‌جا گردکردنِ بی‌صدا نیست.

   هیچ چیزی از مرورگر نمی‌خواهد و هنوز هیچ صفحه‌ای آن را بار نمی‌کند؛
   `lineTotal`ِ `index.html` همان حسابِ امروز را می‌کند (گامِ بعدی، با پرچم).
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidMoney = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidMoney = api;
})(function () {
  'use strict';

  var MAX_EXACT = 9007199254740991; // ۲^۵۳−۱ = Number.MAX_SAFE_INTEGER
  var ROUNDING_LINE_TYPE = 'rounding';

  // -0 را صفر می‌کند تا با Long ِ کاتلین یکی بماند
  function norm(v) { return v + 0; }

  function checkExact(v, what) {
    if (typeof v !== 'number' || !Number.isSafeInteger(v)) {
      throw new RangeError((what || 'عدد') + ' بیرون از بازهٔ دقیق: ' + v);
    }
    return norm(v);
  }

  /* حاصلِ واقعیِ بیرون از ۲^۵۳−۱ هرگز به عددِ «امن» گرد نمی‌شود، پس همین سنجش دقیق است */
  function addExact(a, b) { return checkExact(checkExact(a) + checkExact(b), 'جمع'); }
  function mulExact(a, b) { return checkExact(checkExact(a) * checkExact(b), 'ضرب'); }

  /* تقسیمِ کف‌گیر بی تقسیمِ اعشاری (Math.floor(a/b) نزدیکِ ۲^۵۳ اشتباه می‌کند) */
  function floorDiv(a, b) {
    checkExact(a); checkExact(b);
    if (b === 0) throw new RangeError('تقسیم بر صفر');
    var r = a % b;                 // باقی‌ماندهٔ دقیق
    var q = (a - r) / b;           // تقسیمِ دقیقِ مضرب
    if (r !== 0 && ((r < 0) !== (b < 0))) q -= 1;
    return norm(q);
  }

  /* n/d نیمه به سمتِ +∞ (معنای Math.round)، برای مثبت و منفی: floorDiv(n + ⌊d/2⌋, d) */
  function roundHalfUp(n, d) {
    checkExact(d);
    if (d <= 0) throw new RangeError('مقسوم‌علیه باید مثبت باشد: ' + d);
    return floorDiv(addExact(n, Math.floor(d / 2)), d);
  }

  // ── ارز ─────────────────────────────────────────────────
  function makeCurrency(code, decimals, stepMinor) {
    if (typeof code !== 'string' || !code.trim()) throw new TypeError('کدِ ارز خالی است');
    if (!Number.isInteger(decimals) || decimals < 0 || decimals > 4) throw new RangeError('رقمِ اعشار باید ۰ تا ۴ باشد: ' + decimals);
    if (!Number.isSafeInteger(stepMinor) || stepMinor < 1) throw new RangeError('پلهٔ گردکردن باید مثبت باشد: ' + stepMinor);
    return Object.freeze({ code: code, decimals: decimals, stepMinor: stepMinor, minorPerMajor: Math.pow(10, decimals) });
  }

  /* جدولِ ارزها — باید با `Currency.TABLE`ِ کاتلین و بخشِ `currencies`ِ بردارها یکی باشد */
  var CURRENCIES = Object.freeze({
    AFN: makeCurrency('AFN', 2, 100), // پلهٔ یک افغانی (۱۰۰ پول) — همان رفتارِ امروز
    USD: makeCurrency('USD', 2, 1),   // فقط برای تبادل (PAY-10)
  });

  /* ارز از جدول؛ پله برای هر دکان قابلِ تنظیم است */
  function currency(code, stepMinor) {
    var c = CURRENCIES[code];
    if (!c) throw new RangeError('ارزِ ناشناخته: ' + code);
    return stepMinor === undefined ? c : makeCurrency(c.code, c.decimals, stepMinor);
  }

  function curOf(c) { return typeof c === 'string' ? currency(c) : c; }

  // ── پول و مقدار ─────────────────────────────────────────
  function money(minor, cur) {
    return Object.freeze({ minor: checkExact(minor, 'مبلغ'), currency: curOf(cur) });
  }

  function qty(milli) { return Object.freeze({ milli: checkExact(milli, 'مقدار') }); }

  /* هویتِ ارز فقط کد است؛ دو ارزِ متفاوت خطای زمانِ اجراست، نه تبدیلِ بی‌صدا */
  function same(a, b) {
    if (a.currency.code !== b.currency.code) {
      var e = new TypeError('دو ارزِ متفاوت: ' + a.currency.code + ' و ' + b.currency.code);
      e.code = 'CURRENCY_MISMATCH';
      throw e;
    }
    return b;
  }

  function add(a, b) { return money(addExact(a.minor, same(a, b).minor), a.currency); }
  function sub(a, b) { return money(addExact(a.minor, -same(a, b).minor), a.currency); }
  function neg(a) { return money(-a.minor, a.currency); }
  function compare(a, b) { same(a, b); return a.minor < b.minor ? -1 : a.minor > b.minor ? 1 : 0; }

  /* از عددِ اعشاریِ امروز (مثلاً salePrice به افغانی)، با Math.round */
  function moneyFromLegacy(major, cur) {
    var c = curOf(cur);
    if (typeof major !== 'number' || !isFinite(major)) throw new RangeError('مبلغِ نامعتبر: ' + major);
    return money(Math.round(major * c.minorPerMajor), c);
  }

  /* همان پاک‌سازیِ setCartQty: Math.round(x × 1000) */
  function qtyFromLegacy(quantity) {
    if (typeof quantity !== 'number' || !isFinite(quantity)) throw new RangeError('مقدارِ نامعتبر: ' + quantity);
    return qty(Math.round(quantity * 1000));
  }

  // ── قاعدهٔ گردکردن ───────────────────────────────────────
  /* floorDiv(qtyMilli × priceMinor + 500·step, 1000·step) × step — برای AFN همان فرمولِ ۵۰٬۰۰۰ / ۱۰۰٬۰۰۰ */
  function lineTotalMinor(qtyMilli, priceMinor, stepMinor) {
    checkExact(stepMinor);
    if (stepMinor <= 0) throw new RangeError('پلهٔ گردکردن باید مثبت باشد: ' + stepMinor);
    var raw = mulExact(qtyMilli, priceMinor);
    return mulExact(roundHalfUp(raw, mulExact(1000, stepMinor)), stepMinor);
  }

  function lineTotal(q, unitPrice) {
    return money(lineTotalMinor(q.milli, unitPrice.minor, unitPrice.currency.stepMinor), unitPrice.currency);
  }

  /* تخفیفِ فاکتور به روشِ بزرگ‌ترین باقی‌مانده، با پلهٔ ارز؛ برابری ⇒ ردیفِ جلوتر */
  function allocateDiscount(discount, lines) {
    var cur = discount.currency;
    var step = cur.stepMinor;
    lines.forEach(function (m) {
      same(discount, m);
      if (m.minor < 0) throw new RangeError('مبلغِ ردیف منفی است: ' + m.minor);
    });
    if (discount.minor < 0) throw new RangeError('تخفیف منفی است: ' + discount.minor);
    if (discount.minor % step !== 0) throw new RangeError('تخفیف باید مضربِ پله (' + step + ') باشد: ' + discount.minor);
    var sum = lines.reduce(function (acc, m) { return addExact(acc, m.minor); }, 0);
    if (discount.minor > sum) throw new RangeError('تخفیف (' + discount.minor + ') از جمعِ ردیف‌ها (' + sum + ') بیشتر است');
    if (discount.minor === 0) return lines.map(function () { return money(0, cur); });

    var units = discount.minor / step;
    var base = [], rem = [], given = 0;
    lines.forEach(function (m, i) {
      var num = mulExact(units, m.minor);
      rem[i] = num % sum;
      base[i] = (num - rem[i]) / sum;
      given += base[i];
    });
    var order = lines.map(function (_, i) { return i; })
      .sort(function (a, b) { return rem[b] - rem[a] || a - b; });
    for (var k = 0; k < units - given; k++) base[order[k]] += 1;
    return base.map(function (u) { return money(mulExact(u, step), cur); });
  }

  /* گردکردنِ نقدی (مثلاً ۵ یا ۱۰ افغانی) — ردیفی جدا با نوعِ rounding، نه تغییرِ قیمت؛ صفر ⇒ null */
  function cashRounding(total, stepMinor) {
    checkExact(stepMinor);
    if (stepMinor <= 0) throw new RangeError('پلهٔ گردکردنِ نقدی باید مثبت باشد: ' + stepMinor);
    var rounded = mulExact(roundHalfUp(total.minor, stepMinor), stepMinor);
    var adjust = addExact(rounded, -total.minor);
    return adjust === 0 ? null : Object.freeze({ type: ROUNDING_LINE_TYPE, amount: money(adjust, total.currency) });
  }

  return {
    MAX_EXACT: MAX_EXACT,
    ROUNDING_LINE_TYPE: ROUNDING_LINE_TYPE,
    CURRENCIES: CURRENCIES,
    currency: currency,
    money: money,
    qty: qty,
    add: add,
    sub: sub,
    neg: neg,
    compare: compare,
    moneyFromLegacy: moneyFromLegacy,
    qtyFromLegacy: qtyFromLegacy,
    floorDiv: floorDiv,
    roundHalfUp: roundHalfUp,
    lineTotalMinor: lineTotalMinor,
    lineTotal: lineTotal,
    allocateDiscount: allocateDiscount,
    cashRounding: cashRounding,
  };
});
