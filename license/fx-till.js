/* ==========================================================
   فروشگاه من | ارزِ نقدِ دیگر در صندوق — PAY-10
   ----------------------------------------------------------
   هم‌زادِ `core/src/commonMain/.../core/pay/FxTill.kt`. هر دو با
   `core/test-vectors/fx-till-v1.json` سنجیده می‌شوند؛ یکی را عوض کنی، دیگری هم.

   - `rates`: جدولِ نرخِ دکان (ارز، نرخ برای `per` واحد، منبع، زمان، گذارنده).
   - `tender`: پرداختِ ارز با نرخِ جدول ⇒ رکوردِ تبادلِ ثابت (`pay-core.exchange`)؛
     نرخِ کهنه‌تر از یک روز ⇒ `FX_STALE`.
   - `parseAmount`، `format`، `afnText`، `rateLine`: متن ⇐⇒ واحدِ کوچک، دقیق و بی اعشارِ شناور.
   تسویه با ارز خودِ `pay-core.settle` است (`fx`).

   خالص است: نه DOM، نه ساعت، نه ذخیره.
   ========================================================== */
(function (factory) {
  'use strict';
  var node = typeof module !== 'undefined' && module.exports;
  var M = node ? require('./money.js') : (typeof window !== 'undefined' ? window.TohidMoney : globalThis.TohidMoney);
  var P = node ? require('./pay-core.js') : (typeof window !== 'undefined' ? window.TohidPay : globalThis.TohidPay);
  var api = factory(M, P);
  if (node) module.exports = api;
  if (typeof window !== 'undefined') window.TohidFx = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidFx = api;
})(function (M, P) {
  'use strict';

  if (!M || !P) throw new Error('license/money.js و license/pay-core.js باید پیش از fx-till.js بار شوند');  // داده: خطای برنامه‌نویس

  var BASE = 'AFN';
  var MAX_RATES = 8;
  var MAX_TEXT = 60;
  var MAX_AGE_MS = 86400000;   // یک روز
  var SKEW_MS = 600000;        // ده دقیقه جلوتر از ساعتِ صندوق
  var DEFAULT_PER = Object.freeze({ USD: 1, PKR: 1000, IRR: 1000000 });

  function fail(code, extra) {
    var r = { ok: false, error: code };
    if (extra) for (var k in extra) if (Object.prototype.hasOwnProperty.call(extra, k)) r[k] = extra[k];
    return r;
  }
  function posInt(v) { return typeof v === 'number' && Number.isSafeInteger(v) && v > 0; }
  function exactMul(a, b) {
    var r = a * b;
    if (!Number.isSafeInteger(r)) throw new RangeError('ضرب بیرون از بازهٔ دقیق: ' + a + ' × ' + b);  // داده: خطای برنامه‌نویس
    return r + 0;
  }
  function add(a, b) {
    var r = a + b;
    if (!Number.isSafeInteger(r)) throw new RangeError('جمع بیرون از بازهٔ دقیق: ' + a + ' + ' + b);  // داده: خطای برنامه‌نویس
    return r + 0;
  }
  function line(v) {
    var s = typeof v === 'string' ? v.trim() : '';
    return s && s.length <= MAX_TEXT && !/[\r\n]/.test(s) ? s : null;
  }

  /* جدولِ نرخِ دکان: هر ارز یک بار، نه افغانی؛ نرخ و زمان عددِ صحیحِ مثبت؛ منبع و گذارنده یک خطِ ناخالی */
  function rates(list) {
    if (!Array.isArray(list)) return fail('BAD_RATE', { index: -1 });
    if (list.length > MAX_RATES) return fail('TOO_MANY_RATES');
    var out = [];
    for (var i = 0; i < list.length; i++) {
      var x = list[i] || {}, cur;
      try { cur = M.currency(typeof x.currency === 'string' ? x.currency.trim() : ''); } catch (e) {
        return fail('BAD_RATE', { index: i, field: 'currency' });
      }
      if (cur.code === BASE) return fail('BAD_RATE', { index: i, field: 'currency' });
      if (out.some(function (r) { return r.currency === cur.code; })) return fail('DUPLICATE_RATE', { index: i });
      if (!posInt(x.rate)) return fail('BAD_RATE', { index: i, field: 'rate' });
      var per = x.per === undefined || x.per === null ? 1 : x.per;
      if (P.FX_PER.indexOf(per) < 0) return fail('BAD_RATE', { index: i, field: 'per' });
      var source = line(x.source);
      if (!source) return fail('BAD_RATE', { index: i, field: 'source' });
      if (!posInt(x.at)) return fail('BAD_RATE', { index: i, field: 'at' });
      var by = line(x.by);
      if (!by) return fail('BAD_RATE', { index: i, field: 'by' });
      out.push({ currency: cur.code, rate: x.rate, per: per, source: source, at: x.at, by: by });
    }
    return { ok: true, rates: out };
  }

  /* پرداختِ `amount` (واحدِ کوچکِ ارز) با نرخِ همان ارزِ جدولِ سنجیده در لحظهٔ `now` ⇒ رکوردِ تبادل */
  function tender(table, currency, amount, now, maxAgeMs) {
    var max = maxAgeMs === undefined ? MAX_AGE_MS : maxAgeMs;
    var r = null;
    for (var i = 0; i < (table || []).length; i++) if (table[i].currency === currency) { r = table[i]; break; }
    if (!r) return fail('FX_NO_RATE');
    if (!posInt(amount)) return fail('INVALID_AMOUNT');
    if (!posInt(now)) return fail('INVALID_TIME');
    var age = now - r.at;
    if (age > max || age < -SKEW_MS) return fail('FX_STALE');
    var rec;
    try {
      rec = P.exchange({ from: { currency: r.currency, amount: amount }, to: BASE, rate: r.rate, per: r.per, source: r.source, at: r.at, approvedBy: r.by });
    } catch (e) {
      return fail('INVALID_AMOUNT');
    }
    if (rec.ok && rec.record.to.amount <= 0) return fail('INVALID_AMOUNT');
    return rec;
  }

  /* رقمِ فارسی و عربی ⇒ لاتین، «٫» ⇒ نقطه */
  function latin(s) {
    return s.replace(/[۰-۹]/g, function (c) { return String(c.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (c) { return String(c.charCodeAt(0) - 0x0660); })
      .replace(/٫/g, '.');
  }

  /* متنِ کادرِ مبلغِ ارز ⇒ واحدِ کوچکِ همان ارز؛ نادرست ⇒ null */
  function parseAmount(text, currency) {
    var cur;
    try { cur = M.currency(currency); } catch (e) { return null; }
    if (typeof text !== 'string') return null;
    var t = latin(text).trim().replace(/[,٬]/g, '');
    var m = /^([0-9]+)(?:\.([0-9]+))?$/.exec(t);
    if (!m) return null;
    var whole = m[1].replace(/^0+/, '') || '0', frac = m[2] || '';
    if (frac.length > cur.decimals || whole.length > 16) return null;
    var w = Number(whole);
    if (!Number.isSafeInteger(w)) return null;
    var f = frac ? Number((frac + '0000').slice(0, cur.decimals)) : 0;
    try { return add(exactMul(w, cur.minorPerMajor), f); } catch (e) { return null; }
  }

  function pad(s, n) { while (s.length < n) s = '0' + s; return s; }  // همان padStart، بی ES2017 (هم‌شکلِ هسته‌های ES5)

  function group(d) {
    var out = '';
    for (var i = 0; i < d.length; i++) {
      if (i > 0 && (d.length - i) % 3 === 0) out += ',';
      out += d.charAt(i);
    }
    return out;
  }

  /* مبلغِ ارز با همهٔ اعشارِ همان ارز: «10.00»، «7,500,000» */
  function format(minor, currency) {
    var cur;
    try { cur = M.currency(currency); } catch (e) { return String(minor); }
    var a = Math.abs(minor), unit = cur.minorPerMajor;
    var frac = cur.decimals > 0 ? '.' + pad(String(a % unit), cur.decimals) : '';
    return (minor < 0 ? '-' : '') + group(String(Math.floor(a / unit))) + frac;
  }

  /* پولِ افغانی بی صفرِ زائدِ اعشار: 7025 ⇒ «70.25»، 24600 ⇒ «246» */
  function afnText(minor) {
    var a = Math.abs(minor);
    var frac = pad(String(a % 100), 2).replace(/0+$/, '');
    return (minor < 0 ? '-' : '') + group(String(Math.floor(a / 100))) + (frac ? '.' + frac : '');
  }

  function rateLine(rate, per, currency) { return group(String(per)) + ' ' + currency + ' = ' + afnText(rate) + ' ' + BASE; }

  return {
    BASE: BASE,
    MAX_RATES: MAX_RATES,
    MAX_TEXT: MAX_TEXT,
    MAX_AGE_MS: MAX_AGE_MS,
    SKEW_MS: SKEW_MS,
    DEFAULT_PER: DEFAULT_PER,
    rates: rates,
    tender: tender,
    parseAmount: parseAmount,
    format: format,
    afnText: afnText,
    rateLine: rateLine,
  };
});
