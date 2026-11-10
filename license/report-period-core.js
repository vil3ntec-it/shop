/* ==========================================================
   فروشگاه من | «دورهٔ قبل / بعد» و «تغییر نسبت به دورهٔ قبل» (RPT-13) — هستهٔ خالصِ وب
   ----------------------------------------------------------
   دوقلوی `core/report/ReportPeriod.kt` (Kotlin، commonMain): همان قاعده و همان عدد، با بردارِ مشترکِ
   `core/test-vectors/report-period-v1.json` (JS: `server/test/report-period-vectors.test.js`، Kotlin:
   `core/src/jvmTest/.../report/ReportPeriodVectorsTest.kt`). یکی را بی دیگری و بردار عوض نکن.

   ‹`shift`›: بازهٔ `steps` دوره پس (منفی) یا پیش (مثبت) به واحدِ خودِ بازه — `days` (همان طول)، `gmonth` (ماهِ میلادیِ
   کامل؛ ماهی که امروز در آن است تا امروز)، `jmonth`/`jyear` (ماه/سالِ خورشیدیِ کامل)، `none` (جابه‌جا نمی‌شود)؛ دورهٔ
   سراسر آینده و ورودیِ نادرست ⇒ `null`؛ صفر ⇒ همان. ‹`change`›: جاری − پیشین و درصد به ده‌هزارم (نیمه دور از صفر)؛
   پیشینِ صفر یا تفاضلی که ×۱۰٬۰۰۰ اش از 2^53 می‌گذرد ⇒ بی درصد (`MAX_DIFF`، همان سقفِ Kotlin). تقویم فقط
   `calendar-core.js` (`CivilDays` و `SolarHijri` ِ Kotlin). ES5 (همان قیدِ report-core برای گوشی‌های قدیمی).
   ========================================================== */
(function (factory) {
  'use strict';
  var node = typeof module !== 'undefined' && module.exports;
  var g = typeof globalThis !== 'undefined' ? globalThis : (typeof window !== 'undefined' ? window : {});
  var cal = node ? require('./calendar-core.js') : g.TohidCalendar;
  if (!cal || typeof cal.toSolar !== 'function') throw new Error('report-period-core: calendar-core.js must load first');
  var api = factory(cal);
  if (node) module.exports = api;
  if (typeof window !== 'undefined') window.TohidReportPeriod = api;
  else g.TohidReportPeriod = api;
})(function (cal) {
  'use strict';

  var UNITS = ['days', 'gmonth', 'jmonth', 'jyear', 'none'];
  /** ⌊(2^53 − 1) / 10٬000⌋: تا این‌جا تفاضل × ۱۰٬۰۰۰ با Number دقیق است (همان `ReportPeriod.MAX_DIFF`) */
  var MAX_DIFF = 900719925474;

  function floorDiv(a, b) { var r = a % b; return (a - r) / b - (r !== 0 && (r < 0) !== (b < 0) ? 1 : 0); }
  function mod(a, b) { var r = a % b; return r !== 0 && (r < 0) !== (b < 0) ? r + b : r; }
  function pad(n, w) { var s = String(n); while (s.length < w) s = '0' + s; return s; }
  function first(y, m) { return pad(y, 4) + '-' + pad(m, 2) + '-01'; }
  function isInt(n) { return typeof n === 'number' && Math.floor(n) === n && Math.abs(n) <= 9007199254740991; }

  /** بازهٔ `steps` دوره پس یا پیش از `from..to`؛ صفر ⇒ همان؛ نشد ⇒ null */
  function shift(unit, from, to, steps, today) {
    if (!isInt(steps)) return null;
    if (steps === 0) return { from: from, to: to };
    var out = null, s, idx, a, b;
    if (unit === 'days') {
      a = cal.jdnOfIso(from); b = cal.jdnOfIso(to);
      if (a === null || b === null || b < a) return null;
      var len = b - a + 1;
      out = { from: cal.isoOfJdn(a + steps * len), to: cal.isoOfJdn(b + steps * len) };
    } else if (unit === 'gmonth') {
      if (cal.jdnOfIso(from) === null) return null;
      idx = Number(from.slice(0, 4)) * 12 + (Number(from.slice(5, 7)) - 1) + steps;
      var head = first(floorDiv(idx, 12), mod(idx, 12) + 1);
      var last = cal.addDays(first(floorDiv(idx + 1, 12), mod(idx + 1, 12) + 1), -1);
      if (last === null) return null;
      out = { from: head, to: head <= today && today <= last ? today : last };
    } else if (unit === 'jmonth') {
      s = cal.toSolar(from);
      if (!s) return null;
      idx = s.y * 12 + (s.m - 1) + steps;
      var jy = floorDiv(idx, 12), jm = mod(idx, 12) + 1;
      a = cal.fromSolar(jy, jm, 1); b = cal.fromSolar(jy, jm, cal.solarMonthLength(jy, jm));
      if (a === null || b === null) return null;
      out = { from: a, to: b };
    } else if (unit === 'jyear') {
      s = cal.toSolar(from);
      if (!s) return null;
      a = cal.fromSolar(s.y + steps, 1, 1); b = cal.fromSolar(s.y + steps, 12, cal.solarMonthLength(s.y + steps, 12));
      if (a === null || b === null) return null;
      out = { from: a, to: b };
    } else return null;
    //  دورهٔ سراسر آینده نه
    return out.from > today ? null : out;
  }

  /** `diff` = جاری − پیشین؛ `bp` = تغییر به ده‌هزارم نسبت به |پیشین| (نیمه دور از صفر)؛ پیشینِ صفر ⇒ null */
  function change(current, previous) {
    var diff = current - previous;
    if (previous === 0 || !isInt(diff) || diff > MAX_DIFF || diff < -MAX_DIFF) return { diff: diff, bp: null };
    var base = previous < 0 ? -previous : previous;
    var scaled = diff * 10000, r = scaled % base, q = (scaled - r) / base;
    var bp = 2 * (r < 0 ? -r : r) >= base ? q + (scaled < 0 ? -1 : 1) : q;
    return { diff: diff, bp: bp === 0 ? 0 : bp };
  }

  return {
    VECTORS_FORMAT: 'report-period-v1',
    UNITS: UNITS,
    MAX_DIFF: MAX_DIFF,
    shift: shift,
    change: change,
  };
});
