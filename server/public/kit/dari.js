/*
 * دری (`prs`) ِ وبِ خریدار = واژه‌نامهٔ فارسی + لایهٔ واژه (LOC-08)؛ فقط وقتی دری برگزیده شد بار می‌شود
 * (boot.js، بیرون از بودجهٔ بارِ نخست). همان قاعدهٔ `core/.../text/DariText.kt` ِ برنامه‌ها، گام‌به‌گام:
 * واژهٔ کامل با پسوندِ شناخته، نیم‌فاصله و مرزِ واژه؛ پسوندِ ناشناخته یا چسبیدن ⇒ همان فارسی (در شک، دست نزن).
 * فهرست (DATA) از خودِ DariText.kt ساخته می‌شود: `node scripts/dari-words.js --write` (آزمونِ dari-text).
 * فقط متنِ خودِ صفحه: کیت (`K.t`) قالبِ واژه‌نامه را پیش از پر کردنِ {جای‌خالی} به این‌جا می‌دهد، پس نامِ کالا،
 * دکان، مشتری و هر دادهٔ دیگر هرگز به این لایه نمی‌رسد؛ واژه‌ای که بی فاصله به {جای‌خالی} چسبیده دست نمی‌خورد.
 * KEYS: جانشینیِ کلید‌به‌کلید (null = همان فارسی) جایی که واژهٔ فهرست در وب معنای دیگری دارد.
 */
(function (g) {
  'use strict';
  /* <dari-words> ساختهٔ scripts/dari-words.js از DariText.kt — دستی ویرایش نکن */
  var DATA = {"words":[{"fa":"کامپیوتر","dari":"کمپیوتر"},{"fa":"رایانه","dari":"کمپیوتر","vowelEnd":true},{"fa":"گوشی","dari":"مبایل","vowelEnd":true},{"fa":"موبایل","dari":"مبایل"},{"fa":"تلفن\u0650 همراه","dari":"مبایل"},{"fa":"تلفن همراه","dari":"مبایل"},{"fa":"انبار","dari":"گدام"},{"fa":"درصد","dari":"فیصد"},{"fa":"کسری","dari":"کمبود","vowelEnd":true},{"fa":"بدهکار","dari":"قرض\u200Cدار","person":true},{"fa":"کارمند","dari":"شاگرد","person":true},{"fa":"فروشگاه","dari":"دکان"},{"fa":"هزینه\u0654 ارسال","dari":"کرایه\u0654 ارسال"},{"fa":"هزینه","dari":"مصرف","vowelEnd":true,"plural":"مصارف"},{"fa":"خیابان","dari":"سرک"},{"fa":"دانشگاه","dari":"پوهنتون"},{"fa":"استان","dari":"ولایت"},{"fa":"خودرو","dari":"موتر","vowelEnd":true}],"phrases":{"قرض\u200Cدار\u0650 بدهکار":"قرض\u200Cدارانی که مانده دارند","تحویل و هزینه":"تحویل و کرایه"},"keep":["فروشگاه من"],"vowelTails":{"":"","\u0654":"\u0650","\u200Cها":"\u200Cها","\u200Cهای":"\u200Cهای","\u200Cای":"ی","\u200Cاش":"ش","\u200Cام":"م","\u200Cات":"ت","\u200Cمان":"\u200Cمان","\u200Cتان":"\u200Cتان","\u200Cشان":"\u200Cشان","مان":"مان","تان":"تان","شان":"شان"},"consonantTails":["","ها","\u200Cها","های","\u200Cهای","ی","م","ت","ش","مان","تان","شان","\u200Cمان","\u200Cتان","\u200Cشان","یم","ید","ند"]};
  /* </dari-words> */

  //  «هزینه» ِ این دو کلید کرایه/فیسِ مشتری است، نه «مصارف» ِ دکان ⇒ فارسی می‌ماند تا صاحب‌کار بگوید
  var KEYS = { 'zone.small': null, 'dm.send': null };

  var ZWNJ = '‌';
  var KASRA = 'ِ';
  var OPEN = '';
  var CLOSE = '';
  var own = function (o, k) { return Object.prototype.hasOwnProperty.call(o, k); };
  //  بلندتر اول، هم‌اندازه به ترتیبِ کاتلین (sortedByDescending پایدار است)
  var WORDS = DATA.words.map(function (w, i) { return [w, i]; })
    .sort(function (a, b) { return b[0].fa.length - a[0].fa.length || a[1] - b[1]; })
    .map(function (x) { return x[0]; });

  /** حرفِ همان واژه: حرف‌های خطِ عربی، نیم‌فاصله و اعراب (رقم و نشانه جداکننده‌اند) */
  function inWord(c) {
    return c === ZWNJ || (c >= 'ؠ' && c <= 'ٟ') || c === 'ٰ' ||
      (c >= 'ٮ' && c <= 'ە') || (c >= 'ۺ' && c <= 'ۿ');
  }
  /** {جای‌خالی} ِ قالب: نمی‌دانیم چه می‌آید، پس مثلِ حرفِ واژه (چسبیده ⇒ دست نزن) */
  function hole(c) { return c === '{' || c === '}' || inWord(c); }
  function hasPersian(s) { return /[؀-ۿ]/.test(s); }
  function endsKasra(s) { return s.slice(-1) === KASRA; }

  /** دریِ واژه با پسوندش؛ پسوندِ ناشناخته ⇒ null (فارسی می‌ماند) */
  function render(w, suffix) {
    var kasra = endsKasra(suffix);
    var core = kasra ? suffix.slice(0, -1) : suffix;
    var k = kasra ? KASRA : '';
    if (w.plural != null) {
      if (core === 'ها' || core === ZWNJ + 'ها') return w.plural + k;
      if (core === 'های' || core === ZWNJ + 'های') return w.plural + KASRA;
    }
    if (w.vowelEnd) {
      if (!own(DATA.vowelTails, core)) return null;
      var t = DATA.vowelTails[core];
      return kasra && endsKasra(t) ? null : w.dari + t + k;
    }
    if (DATA.consonantTails.indexOf(core) >= 0 || (w.person && (core === 'ان' || core === 'انی'))) return w.dari + core + k;
    return null;
  }

  function strip(s) {
    return s.indexOf(OPEN) < 0 && s.indexOf(CLOSE) < 0 ? s : s.split(OPEN).join('').split(CLOSE).join('');
  }
  function protect(v) { return OPEN + v + CLOSE; }

  /** واژه‌نامه روی یک تکه متنِ صفحه؛ gluedLeft/gluedRight: داده بی فاصله پیش/پس از آن */
  function words(fa, gluedLeft, gluedRight) {
    if (!hasPersian(fa)) return fa;
    var out = null;
    var i = 0;
    var copied = 0;
    scan: while (i < fa.length) {
      if (i === 0 ? !gluedLeft : !inWord(fa[i - 1])) {
        for (var a = 0; a < DATA.keep.length; a++) {
          if (fa.substr(i, DATA.keep[a].length) === DATA.keep[a]) { i += DATA.keep[a].length; continue scan; }
        }
        for (var b = 0; b < WORDS.length; b++) {
          var w = WORDS[b];
          if (fa.substr(i, w.fa.length) !== w.fa) continue;
          var j = i + w.fa.length;
          while (j < fa.length && inWord(fa[j])) j++;
          if (j === fa.length && gluedRight) continue;
          var dari = render(w, fa.substring(i + w.fa.length, j));
          if (dari === null) continue;
          out = (out === null ? '' : out) + fa.substring(copied, i) + dari;
          i = j;
          copied = j;
          continue scan;
        }
      }
      i++;
    }
    return out === null ? fa : out + fa.substring(copied);
  }

  /** نوشتهٔ فارسی ⇒ دری (همان `DariText.of`)؛ بخشِ protect‌شده بایت‌به‌بایت همان می‌ماند */
  function run(fa, glue) {
    if (own(DATA.phrases, fa)) return DATA.phrases[fa];
    if (fa.indexOf(OPEN) < 0 && fa.indexOf(CLOSE) < 0) return hasPersian(fa) ? words(fa, false, false) : fa;
    var sb = '';
    var i = 0;
    var gluedLeft = false;
    while (i < fa.length) {
      var open = fa.indexOf(OPEN, i);
      if (open < 0) open = fa.length;
      var close = fa.length;
      if (open < fa.length) {
        var depth = 0;
        var j = open;
        while (j < fa.length) {
          if (fa[j] === OPEN) depth++; else if (fa[j] === CLOSE && --depth === 0) break;
          j++;
        }
        close = j;
      }
      var seg = strip(fa.substring(i, open));
      var inside = open < fa.length ? strip(fa.substring(open + 1, Math.min(close, fa.length))) : '';
      var gluedRight = inside !== '' && glue(inside[0]);
      sb += words(seg, gluedLeft, gluedRight) + inside;
      gluedLeft = inside !== '' ? glue(inside[inside.length - 1]) : seg !== '' ? inWord(seg[seg.length - 1]) : gluedLeft;
      i = close + 1;
    }
    return sb;
  }
  function of(fa) { return run(fa, inWord); }

  /**
   * قالبِ واژه‌نامهٔ وب (کلید ⇒ متنِ فارسی با {جای‌خالی}) ⇒ دری؛ کیت پس از این جای‌خالی‌ها را پر می‌کند.
   * KEYS بر همه جلو است؛ هر {جای‌خالی} protect می‌شود تا مثلِ دادهٔ کاربر بماند.
   */
  var memo = Object.create(null);
  function t(s, key) {
    if (!s) return s;
    if (key != null && own(KEYS, key)) return KEYS[key] === null ? s : KEYS[key];
    if (!hasPersian(s)) return s;
    var m = memo[s];
    if (m === undefined) {
      m = memo[s] = own(DATA.phrases, s) ? DATA.phrases[s]
        : run(s.replace(/\{\w+\}/g, function (h) { return OPEN + h + CLOSE; }), hole);
    }
    return m;
  }

  var api = { of: of, t: t, protect: protect, strip: strip, DATA: DATA, KEYS: KEYS, OPEN: OPEN, CLOSE: CLOSE };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else if (g && g.VILL3N) g.VILL3N.tx = t;
})(this);
