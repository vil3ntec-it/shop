/* ==========================================================
   فروشگاه من | کلیدِ جست‌وجو، ارقام، افغانی و تلفن (بخش‌های ۳.۱۳ و ۴)
   ----------------------------------------------------------
   همتای دقیقِ `core/text/TextNormalizer.kt` و `core/text/LocaleFormat.kt`
   در برنامهٔ نیتیو. هر دو با یک فایلِ مرجع سنجیده می‌شوند:
   `core/test-vectors/text-normalize.json` و `core/test-vectors/locale-format.json`.
   ⛔ رفتارِ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید.

   ── قانونِ طلایی ─────────────────────────────────────────────────
   متنِ ذخیره‌شده هرگز عوض نمی‌شود. نرمال‌سازی فقط روی «کلیدِ جست‌وجو»
   و «کلیدِ مقایسه» است — در پشتو «ي» و «ی» دو حرفِ جدا‌اند و متنِ
   اصلی باید همان بماند.

   این فایل هیچ چیزی از مرورگر نمی‌خواهد (نه DOM، نه Intl)، پس همان
   کدی که در گوشی می‌دود در آزمونِ Node هم عیناً دویده می‌شود.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidText = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidText = api;
})(function () {
  'use strict';

  /* ---------- ۱) کلیدِ جست‌وجو ---------- */

  /** نویسه‌های نامرئی که از کپی/چسباندن می‌آیند: نیم‌فاصله، ZWJ، نشانه‌های جهت، BOM، خط‌تیرهٔ نرم */
  function isInvisible(c) {
    return c === 0x200C || c === 0x200D || c === 0x200E || c === 0x200F || c === 0x061C ||
      (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069) || c === 0xFEFF || c === 0x00AD;
  }

  function isSpace(c) {
    return c === 0x20 || (c >= 0x09 && c <= 0x0D) || c === 0x00A0 || c === 0x1680 ||
      (c >= 0x2000 && c <= 0x200A) || c === 0x2028 || c === 0x2029 || c === 0x202F ||
      c === 0x205F || c === 0x3000;
  }

  /**
   * کلیدِ جست‌وجو: حروفِ هم‌ارز یکی، اعراب و کشیده حذف، نیم‌فاصله حذف،
   * ارقام لاتین، «٫» و «,» نقطه، حروفِ لاتین کوچک، فاصله‌ها یکی.
   */
  function searchKey(text) {
    var s = text == null ? '' : String(text);
    var out = '';
    var pendingSpace = false;
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      var ch;
      if (c === 0x064A || c === 0x0649) ch = 'ی';            // ي ى → ی
      else if (c === 0x0643) ch = 'ک';                       // ك → ک
      else if (c === 0x06C0) ch = 'ه';                       // ۀ → ه
      else if (c === 0x0623 || c === 0x0625 || c === 0x0622) ch = 'ا'; // أ إ آ → ا
      else if ((c >= 0x064B && c <= 0x065F) || c === 0x0670 || c === 0x0640) continue; // اعراب، کشیده
      else if (c >= 0x06F0 && c <= 0x06F9) ch = String.fromCharCode(48 + c - 0x06F0);
      else if (c >= 0x0660 && c <= 0x0669) ch = String.fromCharCode(48 + c - 0x0660);
      else if (c === 0x066B || c === 0x2C) ch = '.';              // ٫ و , → .
      else if (isInvisible(c)) continue;
      else if (isSpace(c)) { pendingSpace = out.length > 0; continue; }
      else if (c >= 0x41 && c <= 0x5A) ch = String.fromCharCode(c + 32); // فقط ASCII؛ بی‌وابستگی به زبانِ دستگاه
      else ch = s.charAt(i);
      if (pendingSpace) { out += ' '; pendingSpace = false; }
      out += ch;
    }
    return out;
  }

  /** کلیدِ مقایسه: همان کلیدِ جست‌وجو بی هیچ فاصله — «می‌خواهم» = «میخواهم» = «می خواهم» */
  function compareKey(text) {
    return searchKey(text).split(' ').join('');
  }

  /** گروهِ مترادف‌های یک کلید (خودش هم هست). groups: [['کچالو','سیب‌زمینی'], …] */
  function alternatives(key, groups) {
    var out = [key];
    if (!groups) return out;
    for (var g = 0; g < groups.length; g++) {
      var keys = [];
      var hit = false;
      for (var k = 0; k < groups[g].length; k++) {
        var ck = compareKey(groups[g][k]);
        if (!ck) continue;
        keys.push(ck);
        if (ck === key) hit = true;
      }
      if (!hit) continue;
      for (var j = 0; j < keys.length; j++) if (out.indexOf(keys[j]) < 0) out.push(keys[j]);
    }
    return out;
  }

  function containsAny(hay, keys) {
    for (var i = 0; i < keys.length; i++) if (hay.indexOf(keys[i]) >= 0) return true;
    return false;
  }

  /**
   * آیا متن با پرس‌وجو جور است: هر کلمهٔ پرس‌وجو (یا مترادفش) جایی در
   * کلیدِ مقایسهٔ متن باشد، به هر ترتیب. اگر کلِ پرس‌وجو خودش یک مترادف
   * باشد («بادنجان رومی»)، مترادفِ کامل هم پذیرفته است. پرس‌وجوی خالی همه را می‌گیرد.
   */
  function matches(text, query, synonyms) {
    var q = searchKey(query);
    if (!q) return true;
    var hay = compareKey(text);
    var whole = q.split(' ').join('');
    if (containsAny(hay, alternatives(whole, synonyms))) return true;
    var tokens = q.split(' ');
    for (var i = 0; i < tokens.length; i++) {
      if (!containsAny(hay, alternatives(tokens[i], synonyms))) return false;
    }
    return true;
  }

  /**
   * فاصلهٔ ویرایشیِ محدود (Levenshtein) روی کلیدِ مقایسه. اگر از max
   * بیشتر شود همان max+1 برمی‌گردد — زود دست می‌کشد.
   */
  function editDistance(a, b, max) {
    var x = compareKey(a), y = compareKey(b);
    if (Math.abs(x.length - y.length) > max) return max + 1;
    var prev = [], cur = [], i, j;
    for (j = 0; j <= y.length; j++) prev[j] = j;
    for (i = 1; i <= x.length; i++) {
      cur = [i];
      var best = i;
      for (j = 1; j <= y.length; j++) {
        var cost = x.charCodeAt(i - 1) === y.charCodeAt(j - 1) ? 0 : 1;
        var v = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost);
        cur[j] = v;
        if (v < best) best = v;
      }
      if (best > max) return max + 1;
      prev = cur;
    }
    return prev[y.length] > max ? max + 1 : prev[y.length];
  }

  /** چند غلطِ تایپی برای کلمه‌ای به این طول بخشیده می‌شود: ≤۳ هیچ، ۴ تا ۷ یکی، ۸ به بالا دو */
  function typoBudget(len) {
    return len <= 3 ? 0 : (len <= 7 ? 1 : 2);
  }

  /**
   * جست‌وجوی بخشنده: هر کلمهٔ پرس‌وجو یا در متن هست، یا با کلمه‌ای از
   * متن در بودجهٔ غلطِ تایپی فاصله دارد.
   */
  function fuzzyMatches(text, query) {
    var q = searchKey(query);
    if (!q) return true;
    var hay = compareKey(text);
    var words = searchKey(text).split(' ');
    var tokens = q.split(' ');
    for (var i = 0; i < tokens.length; i++) {
      var t = tokens[i];
      if (hay.indexOf(t) >= 0) continue;
      var budget = typoBudget(t.length);
      var ok = false;
      for (var w = 0; budget > 0 && w < words.length && !ok; w++) {
        ok = editDistance(words[w], t, budget) <= budget;
      }
      if (!ok) return false;
    }
    return true;
  }

  /* ---------- ۲) ارقام (LOC-05) ---------- */

  /** ورودی به ASCII: ۰-۹ و ٠-٩ لاتین، «٫» نقطه، «٬» کاما. چیزِ دیگری دست نمی‌خورد. */
  function asciiDigits(text) {
    var s = text == null ? '' : String(text);
    var out = '';
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (c >= 0x06F0 && c <= 0x06F9) out += String.fromCharCode(48 + c - 0x06F0);
      else if (c >= 0x0660 && c <= 0x0669) out += String.fromCharCode(48 + c - 0x0660);
      else if (c === 0x066B) out += '.';
      else if (c === 0x066C) out += ',';
      else out += s.charAt(i);
    }
    return out;
  }

  /** برای نمایش: style = 'fa' ⇒ ارقامِ فارسی (۰-۹)؛ هر چیزِ دیگر ⇒ لاتین */
  function displayDigits(text, style) {
    var s = asciiDigits(text);
    if (style !== 'fa') return s;
    var out = '';
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      out += c >= 48 && c <= 57 ? String.fromCharCode(0x06F0 + c - 48) : s.charAt(i);
    }
    return out;
  }

  /* ---------- ۳) افغانی (LOC-07) ---------- */

  var AFN_SIGN = '؋';   // ؋
  var AFN_WORD = 'افغانی';

  /**
   * مبلغ بی اعشار، با جداکنندهٔ هزارگان. گرد کردن همان `Math.round` است
   * (نیمه رو به بالا) تا با `money()` ِ کاتلین و `formatMoney` ِ وب یکی بماند؛
   * «-0» هرگز چاپ نمی‌شود.
   * symbol: 'sign' ⇒ «؋»، 'word' ⇒ «افغانی»، غیرِ آن ⇒ بی نماد.
   * digits: 'fa' ⇒ ۱۲۳٬۴۵۶؛ غیرِ آن ⇒ 123,456.
   */
  function formatAfghani(amount, symbol, digits) {
    var v = Number(amount);
    var n = isFinite(v) ? Math.round(v) : 0;
    if (n === 0) n = 0;   // -0 ⇒ 0
    var neg = n < 0;
    var raw = String(Math.abs(n));
    var out = neg ? '-' : '';
    for (var i = 0; i < raw.length; i++) {
      if (i > 0 && (raw.length - i) % 3 === 0) out += ',';
      out += raw.charAt(i);
    }
    //  پشتو/دری با ارقامِ فارسی: جداکنندهٔ «٬»؛ با ارقامِ لاتین همیشه «,» (نه جداکنندهٔ عجیبِ CLDR)
    if (digits === 'fa') out = displayDigits(out, 'fa').split(',').join('٬');
    if (symbol === 'sign') return out + ' ' + AFN_SIGN;
    if (symbol === 'word') return out + ' ' + AFN_WORD;
    return out;
  }

  /* ---------- ۴) تلفن (LOC-09) ---------- */

  /**
   * شماره به قالبِ ذخیره: ‎+93 و ۹ رقمِ ملی، یا E.164ِ کشورِ دیگر.
   * 07xxxxxxxx، 7xxxxxxxx، 937xxxxxxxx، 00937…، ‎+93 07… همه پذیرفته‌اند.
   * پیش‌شماره به اپراتور نگاشت نمی‌شود (نامِ برندها عوض شده). نامعتبر ⇒ null.
   */
  function normalizePhone(text) {
    var s = asciiDigits(text == null ? '' : String(text));
    var clean = '';
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (isSpace(c) || isInvisible(c) || c === 0x2D || c === 0x28 || c === 0x29 || c === 0x2E) continue;
      clean += s.charAt(i);
    }
    if (clean.indexOf('00') === 0) clean = '+' + clean.slice(2);
    var intl = clean.charAt(0) === '+';
    var d = intl ? clean.slice(1) : clean;
    if (!/^[0-9]+$/.test(d)) return null;
    var nsn = null;
    if (intl) {
      if (d.indexOf('93') !== 0) return /^[1-9][0-9]{7,14}$/.test(d) ? '+' + d : null;
      nsn = d.slice(2);
      if (nsn.charAt(0) === '0') nsn = nsn.slice(1);
    } else if (d.charAt(0) === '0') {
      nsn = d.slice(1);
    } else if (d.length === 9) {
      nsn = d;
    } else if (d.length === 11 && d.indexOf('93') === 0) {
      nsn = d.slice(2);
    }
    return nsn !== null && /^[1-9][0-9]{8}$/.test(nsn) ? '+93' + nsn : null;
  }

  /** نمایش: ‎+93 7X XXX XXXX. شماره‌ای که این شکل را ندارد همان‌طور برمی‌گردد. */
  function formatPhone(e164) {
    var s = e164 == null ? '' : String(e164);
    if (!/^\+93[1-9][0-9]{8}$/.test(s)) return s;
    var n = s.slice(3);
    return '+93 ' + n.slice(0, 2) + ' ' + n.slice(2, 5) + ' ' + n.slice(5);
  }

  return {
    searchKey: searchKey,
    compareKey: compareKey,
    matches: matches,
    editDistance: editDistance,
    typoBudget: typoBudget,
    fuzzyMatches: fuzzyMatches,
    asciiDigits: asciiDigits,
    displayDigits: displayDigits,
    formatAfghani: formatAfghani,
    normalizePhone: normalizePhone,
    formatPhone: formatPhone,
    AFN_SIGN: AFN_SIGN,
    AFN_WORD: AFN_WORD,
  };
});
