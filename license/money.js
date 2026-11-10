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
      throw new RangeError((what || 'عدد') + ' بیرون از بازهٔ دقیق: ' + v);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    }
    return norm(v);
  }

  /* حاصلِ واقعیِ بیرون از ۲^۵۳−۱ هرگز به عددِ «امن» گرد نمی‌شود، پس همین سنجش دقیق است */
  function addExact(a, b) { return checkExact(checkExact(a) + checkExact(b), 'جمع'); }  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
  function mulExact(a, b) { return checkExact(checkExact(a) * checkExact(b), 'ضرب'); }  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)

  /* تقسیمِ کف‌گیر بی تقسیمِ اعشاری (Math.floor(a/b) نزدیکِ ۲^۵۳ اشتباه می‌کند) */
  function floorDiv(a, b) {
    checkExact(a); checkExact(b);
    if (b === 0) throw new RangeError('تقسیم بر صفر');  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    var r = a % b;                 // باقی‌ماندهٔ دقیق
    var q = (a - r) / b;           // تقسیمِ دقیقِ مضرب
    if (r !== 0 && ((r < 0) !== (b < 0))) q -= 1;
    return norm(q);
  }

  /* n/d نیمه به سمتِ +∞ (معنای Math.round)، برای مثبت و منفی: floorDiv(n + ⌊d/2⌋, d) */
  function roundHalfUp(n, d) {
    checkExact(d);
    if (d <= 0) throw new RangeError('مقسوم‌علیه باید مثبت باشد: ' + d);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    return floorDiv(addExact(n, Math.floor(d / 2)), d);
  }

  // ── ارز ─────────────────────────────────────────────────
  function makeCurrency(code, decimals, stepMinor) {
    if (typeof code !== 'string' || !code.trim()) throw new TypeError('کدِ ارز خالی است');  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    if (!Number.isInteger(decimals) || decimals < 0 || decimals > 4) throw new RangeError('رقمِ اعشار باید ۰ تا ۴ باشد: ' + decimals);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    if (!Number.isSafeInteger(stepMinor) || stepMinor < 1) throw new RangeError('پلهٔ گردکردن باید مثبت باشد: ' + stepMinor);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    return Object.freeze({ code: code, decimals: decimals, stepMinor: stepMinor, minorPerMajor: Math.pow(10, decimals) });
  }

  /* جدولِ ارزها — باید با `Currency.TABLE`ِ کاتلین و بخشِ `currencies`ِ بردارها یکی باشد */
  var CURRENCIES = Object.freeze({
    AFN: makeCurrency('AFN', 2, 100), // پلهٔ یک افغانی (۱۰۰ پول) — همان رفتارِ امروز
    USD: makeCurrency('USD', 2, 1),   // فقط برای تبادل (PAY-10)
    PKR: makeCurrency('PKR', 2, 100), // کلدار: فقط تبادل (PAY-10)؛ پله یک روپیه
    IRR: makeCurrency('IRR', 0, 1),   // ریال: فقط تبادل (PAY-10)؛ بی اعشار
  });

  /* ارز از جدول؛ پله برای هر دکان قابلِ تنظیم است. فقط کلیدِ خودِ جدول: «constructor» و «__proto__» ارزِ ناشناخته‌اند،
     نه تابع/شیءِ پروتوتایپ (فازِ تفاضلی: پرداخت با ارزِ «constructor» پذیرفته می‌شد و Kotlin رد می‌کرد) */
  function currency(code, stepMinor) {
    var c = typeof code === 'string' && Object.prototype.hasOwnProperty.call(CURRENCIES, code) ? CURRENCIES[code] : null;
    if (!c) throw new RangeError('ارزِ ناشناخته: ' + code);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    return stepMinor === undefined ? c : makeCurrency(c.code, c.decimals, stepMinor);
  }

  function curOf(c) { return typeof c === 'string' ? currency(c) : c; }

  // ── پول و مقدار ─────────────────────────────────────────
  function money(minor, cur) {
    return Object.freeze({ minor: checkExact(minor, 'مبلغ'), currency: curOf(cur) });  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
  }

  function qty(milli) { return Object.freeze({ milli: checkExact(milli, 'مقدار') }); }  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)

  /* هویتِ ارز فقط کد است؛ دو ارزِ متفاوت خطای زمانِ اجراست، نه تبدیلِ بی‌صدا */
  function same(a, b) {
    if (a.currency.code !== b.currency.code) {
      var e = new TypeError('دو ارزِ متفاوت: ' + a.currency.code + ' و ' + b.currency.code);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
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
    if (typeof major !== 'number' || !isFinite(major)) throw new RangeError('مبلغِ نامعتبر: ' + major);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    return money(Math.round(major * c.minorPerMajor), c);
  }

  /* همان پاک‌سازیِ setCartQty: Math.round(x × 1000) */
  function qtyFromLegacy(quantity) {
    if (typeof quantity !== 'number' || !isFinite(quantity)) throw new RangeError('مقدارِ نامعتبر: ' + quantity);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    return qty(Math.round(quantity * 1000));
  }

  // ── قاعدهٔ گردکردن ───────────────────────────────────────
  /* floorDiv(qtyMilli × priceMinor + 500·step, 1000·step) × step — برای AFN همان فرمولِ ۵۰٬۰۰۰ / ۱۰۰٬۰۰۰ */
  function lineTotalMinor(qtyMilli, priceMinor, stepMinor) {
    checkExact(stepMinor);
    if (stepMinor <= 0) throw new RangeError('پلهٔ گردکردن باید مثبت باشد: ' + stepMinor);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
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
      if (m.minor < 0) throw new RangeError('مبلغِ ردیف منفی است: ' + m.minor);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    });
    if (discount.minor < 0) throw new RangeError('تخفیف منفی است: ' + discount.minor);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    if (discount.minor % step !== 0) throw new RangeError('تخفیف باید مضربِ پله (' + step + ') باشد: ' + discount.minor);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    var sum = lines.reduce(function (acc, m) { return addExact(acc, m.minor); }, 0);
    if (discount.minor > sum) throw new RangeError('تخفیف (' + discount.minor + ') از جمعِ ردیف‌ها (' + sum + ') بیشتر است');  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
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
    if (stepMinor <= 0) throw new RangeError('پلهٔ گردکردنِ نقدی باید مثبت باشد: ' + stepMinor);  // داده: خطای برنامه‌نویس (همان require ِ Money.kt)
    var rounded = mulExact(roundHalfUp(total.minor, stepMinor), stepMinor);
    var adjust = addExact(rounded, -total.minor);
    return adjust === 0 ? null : Object.freeze({ type: ROUNDING_LINE_TYPE, amount: money(adjust, total.currency) });
  }

  /* مبلغِ برگشتِ بخشی از یک قلم — «گردِ تجمعی» (SEC-DATA-01). هم‌زادِ `ReturnShare.amount` ِ core؛
     بردار: `license/test-vectors/return-share-v1.json`. مبلغِ هر برگشت = پس‌دادنیِ تا پایانِ آن − پس‌دادنیِ
     تا پیش از آن، پس جمعِ برگشت‌های تکه‌تکهٔ یک قلم هرگز از برگشتِ یک‌جای همان مقدار بیشتر نمی‌شود
     (پیش‌تر دو عدد به ۲٫۵ ⇒ ۳ + ۳ = ۶ پس داده می‌شد). عددهای دفترِ امروز (افغانی، Double)؛ گرد به افغانیِ
     کامل با Math.round — همان mathRound ِ کاتلین، و ترتیبِ عمل یکی، پس خروجی بیت‌به‌بیت یکی است.
     refundedBefore/refundedQty (اختیاری، #586، تاریخچهٔ قاطی): جمعِ amount و quantity ِ برگشت‌های پیشینِ همین
     saleItemId. برگشت‌های پیش از به‌روزرسانی با فرمولِ جداجدا ثبت شده‌اند، پس مبنا پولِ واقعاً پس‌داده است:
     max(0, upTo(پایان) − refundedBefore) — قلمِ ۲ افغانی برای ۳ عدد با دو برگشتِ کهنهٔ ۱ + ۱ دیگر یکِ سوم
     نمی‌دهد. ردیف‌ها کمتر از returnedBefore (ردیفِ کهنه بی saleItemId) ⇒ بخشِ بی‌ردیف upTo(پیش) فرض می‌شود. */
  function returnShare(lineTotal, quantity, returnedBefore, qty, paidRatio, refundedBefore, refundedQty) {
    if (!(quantity > 0) || !isFinite(quantity) || !isFinite(lineTotal) || !isFinite(paidRatio)) return 0;
    if (!isFinite(qty) || !(qty > 0)) return 0;
    var before = isFinite(returnedBefore) && returnedBefore > 0 ? returnedBefore : 0;
    var net = lineTotal * paidRatio;
    function upTo(k) {
      if (k <= 0) return 0;
      if (k >= quantity) return Math.round(net);
      return Math.round(net * k / quantity);
    }
    if (refundedBefore === undefined && refundedQty === undefined) return norm(upTo(before + qty) - upTo(before));
    var paid = isFinite(refundedBefore) && refundedBefore > 0 ? refundedBefore : 0;
    var rowsQty = isFinite(refundedQty) && refundedQty > 0 ? refundedQty : 0;
    var base = rowsQty + 1e-9 < before ? Math.max(paid, upTo(before)) : paid;
    return norm(Math.max(0, upTo(before + qty) - base));
  }

  // ── PRD-08: مقدارِ اعشاری (هم‌زادِ `core/money/QtyExact.kt`؛ بردارِ `core/test-vectors/qty-exact-v1.json`) ──
  /* هزارم از Double (Math.round(q × 1000))؛ NaN، بی‌نهایت یا بیرون از بازهٔ دقیق ⇒ null */
  function qMilli(q) {
    if (typeof q !== 'number' || !isFinite(q)) return null;
    var m = norm(Math.round(q * 1000));
    return Math.abs(m) > MAX_EXACT ? null : m;
  }
  function qFractional(milli) { return milli % 1000 !== 0; }
  function qExactMinor(priceMinor, qtyMilli) {
    try { return lineTotalMinor(qtyMilli, priceMinor, 100); } catch (e) { if (e instanceof RangeError) return null; throw e; }
  }
  /* فقط مقدارِ اعشاری قاعدهٔ دقیق دارد؛ مقدارِ درست یا عددِ نامعتبر ⇒ null (همان حسابِ پیشین) */
  function qLineMinorOrNull(price, quantity) {
    var q = qMilli(quantity);
    if (q === null || !qFractional(q) || typeof price !== 'number' || !isFinite(price)) return null;
    var p = norm(Math.round(price * 100));
    return Math.abs(p) > MAX_EXACT ? null : qExactMinor(p, q);
  }
  function qLegacyMinor(price, quantity) { return mulExact(norm(Math.round(price * quantity)), 100); }
  function qLineMinor(price, quantity, on) {
    var e = on ? qLineMinorOrNull(price, quantity) : null;
    return e === null ? qLegacyMinor(price, quantity) : e;
  }
  function qClean(q) { var m = qMilli(q); return m === null ? q : m / 1000; }
  function qPlus(a, b) {
    var x = qMilli(a), y = qMilli(b);
    if (x === null || y === null || Math.abs(x + y) > MAX_EXACT) return a + b;
    return (x + y) / 1000;
  }
  function qRoom(available, inCart) {
    var a = qMilli(available), c = qMilli(inCart);
    if (a === null || c === null) return Math.max(0, available - inCart);
    return a > c ? (a - c) / 1000 : 0;
  }
  function qTooPrecise(text) {
    var s = String(text);
    var at = s.search(/[.٫٬]/);
    if (at < 0) return false;
    var digits = s.slice(at + 1).replace(/[۰-۹]/g, function (c) { return String(c.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (c) { return String(c.charCodeAt(0) - 0x0660); }).replace(/[^0-9]/g, '');
    return digits.replace(/0+$/, '').length > 3;
  }

  return {
    qtyExact: Object.freeze({
      DECIMALS: 3, milli: qMilli, fractional: qFractional, exactMinor: qExactMinor, lineMinorOrNull: qLineMinorOrNull,
      legacyMinor: qLegacyMinor, lineMinor: qLineMinor, clean: qClean, plus: qPlus, room: qRoom, tooPrecise: qTooPrecise,
    }),
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
    returnShare: returnShare,
  };
});
