/* ==========================================================
   فروشگاه من | تقویم: هجری خورشیدی، قمری و روزِ کاری (بخش ۳.۱۴)
   ----------------------------------------------------------
   همتای دقیقِ `core/calendar/*.kt` در برنامهٔ نیتیو؛ هر دو با
   `core/test-vectors/calendar.json` سنجیده می‌شوند.
   ⛔ رفتارِ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید.

   ── چرا حساب و نه Intl ───────────────────────────────────────────
   تبدیلِ خورشیدی همان الگوریتمِ شناخته‌شدهٔ jalaali است (جدولِ «شکست»‌ها)،
   همان که اندروید از روزِ اول داشت. Intl در WebViewِ اندرویدهای قدیمی
   تقویمِ `persian` ندارد و `jalaliOf` ِ امروزِ وب آن‌جا بی‌صدا به میلادی
   می‌افتد. آزمون‌ها نشان می‌دهند این حساب برای همهٔ روزهای ۱۳۰۰ تا ۱۵۰۰
   با `Intl` (ICU، تقویم‌های persian و islamic-civil) یکی است
   (`server/test/locale-vectors.test.js`).
   نامِ ماه‌ها جدولِ خودمان است: `fa-AF` ماهِ ششم را «سنبلهٔ» می‌نویسد و
   `ps-AF` پسوندِ «AP» دارد — از Intl فقط عدد گرفته می‌شد، اینجا هیچ.

   هر تاریخ رشتهٔ `YYYY-MM-DD` ِ میلادی است، همان که در فایلِ داده ذخیره
   می‌شود. تقسیمِ صحیح رو به صفر بریده می‌شود (`~~`)، درست مثلِ `/` ِ کاتلین.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidCalendar = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidCalendar = api;
})(function () {
  'use strict';

  function div(a, b) { return ~~(a / b); }
  function floorDiv(a, b) { return Math.floor(a / b); }
  function pad2(n) { return n < 10 ? '0' + n : String(n); }
  function pad4(n) { var s = String(n); while (s.length < 4) s = '0' + s; return s; }

  var DIGITS_FA = '۰۱۲۳۴۵۶۷۸۹';
  function digits(text, style) {
    if (style !== 'fa') return text;
    return String(text).replace(/[0-9]/g, function (d) { return DIGITS_FA.charAt(+d); });
  }

  /* ---------- میلادی ⇄ شمارهٔ روزِ ژولینی (JDN) ---------- */

  function jdnOf(gy, gm, gd) {
    var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
      div(153 * ((gm + 9) % 12) + 2, 5) + gd - 34840408;
    return d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752;
  }

  function gregorianOf(jdn) {
    var j = 4 * jdn + 139361631;
    j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908;
    var i = div(j % 1461, 4) * 5 + 308;
    var gd = div(i % 153, 5) + 1;
    var gm = div(i, 153) % 12 + 1;
    var gy = div(j, 1461) - 100100 + div(8 - gm, 6);
    return { y: gy, m: gm, d: gd };
  }

  function gregorianDays(y, m) {
    if (m === 2) return (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0 ? 29 : 28;
    return m === 4 || m === 6 || m === 9 || m === 11 ? 30 : 31;
  }

  /** `YYYY-MM-DD` ِ درست ⇒ JDN؛ هر چیزِ دیگر (۲۰۲۶-۰۲-۳۰ هم) ⇒ null */
  function jdnOfIso(iso) {
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso == null ? '' : String(iso));
    if (!m) return null;
    var y = +m[1], mo = +m[2], d = +m[3];
    if (y < 1 || mo < 1 || mo > 12 || d < 1 || d > gregorianDays(y, mo)) return null;
    return jdnOf(y, mo, d);
  }

  function isoOfJdn(jdn) {
    var g = gregorianOf(jdn);
    return pad4(g.y) + '-' + pad2(g.m) + '-' + pad2(g.d);
  }

  /** چند روز جلو یا عقب */
  function addDays(iso, n) {
    var j = jdnOfIso(iso);
    return j === null ? null : isoOfJdn(j + n);
  }

  /** روزِ هفته به شمارِ ISO: ۱ دوشنبه … ۵ جمعه، ۶ شنبه، ۷ یکشنبه */
  function isoWeekday(iso) {
    var j = jdnOfIso(iso);
    return j === null ? null : (j % 7) + 1;
  }

  /* ---------- هجری خورشیدی ---------- */

  var BREAKS = [-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
    1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178];

  var SOLAR_MIN = 1, SOLAR_MAX = 3177;

  function cal(jy) {
    var gy = jy + 621, leapJ = -14, jp = BREAKS[0], jump = 0, jm, n, i;
    for (i = 1; i < BREAKS.length; i++) {
      jm = BREAKS[i];
      jump = jm - jp;
      if (jy < jm) break;
      leapJ = leapJ + div(jump, 33) * 8 + div(jump % 33, 4);
      jp = jm;
    }
    n = jy - jp;
    leapJ = leapJ + div(n, 33) * 8 + div(n % 33 + 3, 4);
    if (jump % 33 === 4 && jump - n === 4) leapJ += 1;
    var leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150;
    var march = 20 + leapJ - leapG;
    if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33;
    var leap = ((n + 1) % 33 - 1) % 4;
    if (leap === -1) leap = 4;
    return { leap: leap, gy: gy, march: march };
  }

  function isSolarLeap(jy) {
    return jy >= SOLAR_MIN && jy <= SOLAR_MAX && cal(jy).leap === 0;
  }

  function solarMonthLength(jy, jm) {
    if (jm < 1 || jm > 12) return 0;
    if (jm <= 6) return 31;
    if (jm <= 11) return 30;
    return isSolarLeap(jy) ? 30 : 29;
  }

  /** میلادی ⇒ خورشیدی: {y, m, d}؛ تاریخِ نادرست ⇒ null */
  function toSolar(iso) {
    var jdn = jdnOfIso(iso);
    if (jdn === null) return null;
    var gy = gregorianOf(jdn).y;
    var jy = gy - 621;
    if (jy < SOLAR_MIN || jy > SOLAR_MAX) return null;
    var c = cal(jy);
    var k = jdn - jdnOf(gy, 3, c.march);
    if (k >= 0) {
      if (k <= 185) return { y: jy, m: 1 + div(k, 31), d: k % 31 + 1 };
      k -= 186;
    } else {
      jy -= 1;
      if (jy < SOLAR_MIN) return null;
      k += 179;
      if (c.leap === 1) k += 1;
    }
    return { y: jy, m: 7 + div(k, 30), d: k % 30 + 1 };
  }

  /** خورشیدی ⇒ `YYYY-MM-DD` ِ میلادی؛ روزِ نادرست (۳۰ حوتِ سالِ غیرکبیسه هم) ⇒ null */
  function fromSolar(jy, jm, jd) {
    if (jy < SOLAR_MIN || jy > SOLAR_MAX || jd < 1 || jd > solarMonthLength(jy, jm)) return null;
    var c = cal(jy);
    var jdn = jdnOf(c.gy, 3, c.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1;
    return isoOfJdn(jdn);
  }

  var SOLAR_MONTHS = {
    fa: ['حمل', 'ثور', 'جوزا', 'سرطان', 'اسد', 'سنبله', 'میزان', 'عقرب', 'قوس', 'جدی', 'دلو', 'حوت'],
    ps: ['وری', 'غویی', 'غبرګولی', 'چنګاښ', 'زمری', 'وږی', 'تله', 'لړم', 'لیندۍ', 'مرغومی', 'سلواغه', 'کب'],
  };

  /** lang: 'ps' ⇒ پشتو؛ هر چیزِ دیگر ⇒ دری */
  function solarMonthName(m, lang) {
    return (SOLAR_MONTHS[lang === 'ps' ? 'ps' : 'fa'])[m - 1] || '';
  }

  /** `1405/07/22` — همان شکلِ `formatDate` ِ اندروید؛ digits = 'fa' ⇒ ۱۴۰۵/۰۷/۲۲ */
  function formatSolarNumeric(iso, digitStyle) {
    var j = toSolar(iso);
    if (!j) return iso == null ? '' : String(iso);
    return digits(j.y + '/' + pad2(j.m) + '/' + pad2(j.d), digitStyle);
  }

  /** `22 میزان 1405` — از اجزا ساخته می‌شود، نه از قالبِ `full` ِ Intl */
  function formatSolarLong(iso, lang, digitStyle) {
    var j = toSolar(iso);
    if (!j) return iso == null ? '' : String(iso);
    return digits(String(j.d), digitStyle) + ' ' + solarMonthName(j.m, lang) + ' ' + digits(String(j.y), digitStyle);
  }

  /** سالِ مالی: حمل تا حوت ⇒ {from, to} میلادی */
  function fiscalYearRange(jy) {
    var from = fromSolar(jy, 1, 1);
    var to = fromSolar(jy, 12, solarMonthLength(jy, 12));
    return from && to ? { from: from, to: to } : null;
  }

  /**
   * سه‌ماههٔ مالی (LOC-02) ⇒ {from, to} میلادی: ۱ = حمل–جوزا، ۲ = سرطان–سنبله،
   * ۳ = میزان–قوس، ۴ = جدی–حوت (حوتِ کبیسه ۳۰ روز). سه‌ماههٔ نادرست یا سالِ بیرونِ بازه ⇒ null
   */
  function fiscalQuarterRange(jy, q) {
    if (q !== 1 && q !== 2 && q !== 3 && q !== 4) return null;
    var m1 = (q - 1) * 3 + 1;
    var from = fromSolar(jy, m1, 1);
    var to = fromSolar(jy, m1 + 2, solarMonthLength(jy, m1 + 2));
    return from && to ? { from: from, to: to } : null;
  }

  /** سه‌ماههٔ مالیِ یک روزِ میلادی ⇒ {y, q}؛ تاریخِ نادرست ⇒ null */
  function fiscalQuarterOf(iso) {
    var j = toSolar(iso);
    return j ? { y: j.y, q: div(j.m - 1, 3) + 1 } : null;
  }

  /* ---------- هجری قمری (جدولیِ مدنی، اختیاری روی اسناد) ----------
     ماهِ واقعی با دیدنِ هلال است؛ جدولی تا یکی‌دو روز می‌لغزد، پس
     `adjust` (روز، مثبت یا منفی) را تنظیمِ فروشگاه می‌دهد. */

  var LUNAR_EPOCH = 1948440;   // ۱ محرم ۱ = ۱۶ ژوئیهٔ ۶۲۲ (ژولینی)، همان islamic-civil

  function lunarStart(y, m) {
    return (y - 1) * 354 + floorDiv(3 + 11 * y, 30) + div(59 * (m - 1) + 1, 2) + LUNAR_EPOCH;
  }

  function toLunar(iso, adjust) {
    var jdn = jdnOfIso(iso);
    if (jdn === null) return null;
    jdn += adjust || 0;
    if (jdn < LUNAR_EPOCH) return null;
    var y = floorDiv(30 * (jdn - LUNAR_EPOCH) + 10646, 10631);
    var m = Math.min(12, -floorDiv(-2 * (jdn - 29 - lunarStart(y, 1)), 59) + 1);
    if (m < 1) m = 1;
    return { y: y, m: m, d: jdn - lunarStart(y, m) + 1 };
  }

  var LUNAR_MONTHS = ['محرم', 'صفر', 'ربیع‌الاول', 'ربیع‌الثانی', 'جمادی‌الاول', 'جمادی‌الثانی',
    'رجب', 'شعبان', 'رمضان', 'شوال', 'ذوالقعده', 'ذوالحجه'];

  function lunarMonthName(m) { return LUNAR_MONTHS[m - 1] || ''; }

  /** `2 ربیع‌الثانی 1448` */
  function formatLunarLong(iso, digitStyle, adjust) {
    var h = toLunar(iso, adjust);
    if (!h) return iso == null ? '' : String(iso);
    return digits(String(h.d), digitStyle) + ' ' + lunarMonthName(h.m) + ' ' + digits(String(h.y), digitStyle);
  }

  /* ---------- هفته و روزِ کاری (LOC-03) ----------
     rules = { weekStart: 6 (شنبه), weekend: [5] (جمعه), holidays: ['YYYY-MM-DD', …] }
     جدولِ تعطیلات از سرور می‌آید و این‌جا ثابت نوشته نمی‌شود. */

  var DEFAULT_RULES = { weekStart: 6, weekend: [5], holidays: [] };

  function rulesOf(r) {
    r = r || {};
    return {
      weekStart: r.weekStart || DEFAULT_RULES.weekStart,
      weekend: r.weekend || DEFAULT_RULES.weekend,
      holidays: r.holidays || DEFAULT_RULES.holidays,
    };
  }

  /** نخستین روزِ هفته‌ای که این روز در آن است */
  function weekStartOf(iso, rules) {
    var wd = isoWeekday(iso);
    if (wd === null) return null;
    var back = (wd - rulesOf(rules).weekStart + 7) % 7;
    return addDays(iso, -back);
  }

  /** 'holiday' (جدولِ سرور) | 'weekend' (تعطیلِ هفتگیِ دکان) | 'work' */
  function dayKind(iso, rules) {
    var wd = isoWeekday(iso);
    if (wd === null) return null;
    var r = rulesOf(rules);
    if (r.holidays.indexOf(iso) >= 0) return 'holiday';
    if (r.weekend.indexOf(wd) >= 0) return 'weekend';
    return 'work';
  }

  function isWorkingDay(iso, rules) { return dayKind(iso, rules) === 'work'; }

  /**
   * روزِ کاری که از نیمه‌شب می‌گذرد: دکانی که تا ۲ بامداد باز است
   * (dayStartMinutes = 120)، فروشِ ۱:۳۰ را به نامِ دیروز می‌نویسد.
   * local = `YYYY-MM-DDTHH:MM` (ثانیه اختیاری) به وقتِ دکان.
   */
  function businessDate(local, dayStartMinutes) {
    var m = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(:\d{2})?$/.exec(local == null ? '' : String(local));
    if (!m || +m[2] > 23 || +m[3] > 59 || jdnOfIso(m[1]) === null) return null;
    var minutes = +m[2] * 60 + +m[3];
    return minutes < (dayStartMinutes || 0) ? addDays(m[1], -1) : m[1];
  }

  /** همان، از ساعتِ میلی‌ثانیه‌ای و اختلافِ ساعتِ دکان با UTC (کابل: ۲۷۰ دقیقه) */
  function businessDateAt(epochMs, utcOffsetMinutes, dayStartMinutes) {
    var local = epochMs + (utcOffsetMinutes || 0) * 60000;
    var days = floorDiv(local, 86400000);
    var minutes = floorDiv(local - days * 86400000, 60000);
    var jdn = 2440588 + days - (minutes < (dayStartMinutes || 0) ? 1 : 0);
    return isoOfJdn(jdn);
  }

  return {
    jdnOfIso: jdnOfIso,
    isoOfJdn: isoOfJdn,
    addDays: addDays,
    isoWeekday: isoWeekday,
    isSolarLeap: isSolarLeap,
    solarMonthLength: solarMonthLength,
    toSolar: toSolar,
    fromSolar: fromSolar,
    solarMonthName: solarMonthName,
    formatSolarNumeric: formatSolarNumeric,
    formatSolarLong: formatSolarLong,
    fiscalYearRange: fiscalYearRange,
    fiscalQuarterRange: fiscalQuarterRange,
    fiscalQuarterOf: fiscalQuarterOf,
    toLunar: toLunar,
    lunarMonthName: lunarMonthName,
    formatLunarLong: formatLunarLong,
    weekStartOf: weekStartOf,
    dayKind: dayKind,
    isWorkingDay: isWorkingDay,
    businessDate: businessDate,
    businessDateAt: businessDateAt,
    SOLAR_MONTHS: SOLAR_MONTHS,
    LUNAR_MONTHS: LUNAR_MONTHS,
  };
});
