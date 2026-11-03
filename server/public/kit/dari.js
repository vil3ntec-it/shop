/*
 * دری (`prs`) ِ وبِ خریدار = واژه‌نامهٔ فارسی + لایهٔ واژه (LOC-08)؛ فقط وقتی دری برگزیده شد بار می‌شود
 * (boot.js، بیرون از بودجهٔ بارِ نخست). همان قاعدهٔ `core/.../text/DariText.kt` ِ برنامه‌ها، گام‌به‌گام:
 * واژهٔ کامل با پسوندِ شناخته، نیم‌فاصله و مرزِ واژه؛ پسوندِ ناشناخته یا چسبیدن ⇒ همان فارسی (در شک، دست نزن).
 * فهرست (DATA) از خودِ DariText.kt ساخته می‌شود: `node scripts/dari-words.js --write` (آزمونِ dari-text).
 * فقط متنِ خودِ صفحه: کیت (`K.t`) قالبِ واژه‌نامه را پیش از پر کردنِ {جای‌خالی} به این‌جا می‌دهد، پس نامِ کالا،
 * دکان، مشتری و هر دادهٔ دیگر هرگز به این لایه نمی‌رسد؛ واژه‌ای که بی فاصله به {جای‌خالی} چسبیده دست نمی‌خورد.
 * KEYS: جانشینیِ کلید‌به‌کلید (null = همان فارسی) جایی که واژهٔ فهرست در وب معنای دیگری دارد.
 * PHRASES با `{}`: جملهٔ کاملِ دری برای قالبی که {جای‌خالی} دارد؛ هر {} همان {نام} ِ قالب به همان ترتیب.
 */
(function (g) {
  'use strict';
  /* <dari-words> ساختهٔ scripts/dari-words.js از DariText.kt — دستی ویرایش نکن */
  var DATA = {"words":[{"fa":"کامپیوتر","dari":"کمپیوتر"},{"fa":"رایانه","dari":"کمپیوتر","vowelEnd":true},{"fa":"گوشی","dari":"مبایل","vowelEnd":true},{"fa":"موبایل","dari":"مبایل"},{"fa":"تلفن\u0650 همراه","dari":"مبایل"},{"fa":"تلفن همراه","dari":"مبایل"},{"fa":"انبار","dari":"گدام"},{"fa":"درصد","dari":"فیصد"},{"fa":"کسری","dari":"کمبود","vowelEnd":true},{"fa":"بدهکار","dari":"قرض\u200Cدار","person":true},{"fa":"کارمند","dari":"شاگرد","person":true},{"fa":"فروشگاه","dari":"دکان"},{"fa":"هزینه\u0654 ارسال","dari":"کرایه\u0654 ارسال"},{"fa":"هزینه","dari":"مصرف","vowelEnd":true,"plural":"مصارف"},{"fa":"خیابان","dari":"سرک"},{"fa":"دانشگاه","dari":"پوهنتون"},{"fa":"استان","dari":"ولایت"},{"fa":"خودرو","dari":"موتر","vowelEnd":true},{"fa":"اطلاعات","dari":"معلومات"},{"fa":"اینترنت","dari":"انترنت"},{"fa":"بدهی","dari":"قرض","vowelEnd":true},{"fa":"کپی","dari":"کاپی"},{"fa":"نویسه","dari":"حرف","vowelEnd":true},{"fa":"دوباره امتحان","dari":"دوباره کوشش"},{"fa":"با موفقیت","dari":"موفقانه"},{"fa":"خورشیدی","dari":"شمسی"},{"fa":"الان","dari":"حالا"},{"fa":"بقیه","dari":"باقی","vowelEnd":true},{"fa":"سپاس","dari":"تشکر"},{"fa":"پوزش","dari":"معذرت"},{"fa":"سامانه","dari":"سیستم","vowelEnd":true},{"fa":"راهنما","dari":"رهنما"},{"fa":"مرجوعی","dari":"واپسی"},{"fa":"تعطیلی","dari":"رخصتی"},{"fa":"تعطیل","dari":"رخصتی"},{"fa":"خرده","dari":"پرچون","vowelEnd":true},{"fa":"تازه","dari":"جدید","vowelEnd":true},{"fa":"بی\u200Cخیال","dari":"صرف نظر"},{"fa":"پول\u0650 پس","dari":"باقی\u0650 پول"},{"fa":"بی","dari":"بدون","vowelEnd":true},{"fa":"پیش\u200Cتر","dari":"قبلاً"},{"fa":"ثبت شد","dari":"ثبت گردید"},{"fa":"ذخیره شد","dari":"ذخیره گردید"},{"fa":"حذف شد","dari":"حذف گردید"},{"fa":"لغو شد","dari":"لغو گردید"},{"fa":"ارسال شد","dari":"ارسال گردید"},{"fa":"تأیید شد","dari":"تأیید گردید"},{"fa":"تایید شد","dari":"تایید گردید"},{"fa":"پرداخت شد","dari":"پرداخت گردید"},{"fa":"بازیابی شد","dari":"بازیابی گردید"},{"fa":"اضافه شد","dari":"اضافه گردید"},{"fa":"ویرایش شد","dari":"ویرایش گردید"},{"fa":"اصلاح شد","dari":"اصلاح گردید"},{"fa":"انجام شد","dari":"انجام گردید"},{"fa":"تسویه شد","dari":"تسویه گردید"}],"phrases":{"قرض\u200Cدار\u0650 بدهکار":"قرض\u200Cدارانی که مانده دارند","تحویل و هزینه":"تحویل و کرایه","سبد همین حالا عوض شد — دوباره بزنید":"سبد همین حالا تغییر کرد — دوباره کوشش کنید","فاکتور در حال\u0650 جابه\u200Cجایی است — لحظه\u200Cای بعد دوباره بزنید":"فاکتور در حال\u0650 جابه\u200Cجایی است — چند لحظه بعد دوباره کوشش کنید","داده\u0654 دکان هنوز بار نشده — لحظه\u200Cای بعد":"معلومات\u0650 دکان هنوز بار نشده — چند لحظه صبر کنید","مشتری منتظر است؟ نام یا شماره\u200Cای بدهید تا بعد پیدایش کنید. صندوق برای نفر\u0650 بعد خالی می\u200Cشود.":"مشتری منتظر است؟ نام یا شماره\u200Cای بنویسید تا بعداً پیدایش کنید. صندوق برای مشتری\u0650 بعدی خالی می\u200Cشود.","PIN درست نیست — {} تلاش مانده":"PIN درست نیست — {} بار دیگر می\u200Cتوانید کوشش کنید","اعتبار (روز)":"میعاد (روز)","اعتبار تا {}":"میعاد تا {}","روزهای اعتبار ۱ تا ۹۰ است":"میعاد باید ۱ تا ۹۰ روز باشد","اعتبار\u0650 این پیش\u200Cفاکتور گذشته؛ قیمت\u200Cها دیگر قول داده نشده\u200Cاند":"میعاد\u0650 این پیش\u200Cفاکتور ختم شده؛ قیمت\u200Cها دیگر تضمین نیستند","پیش\u200Cفاکتورهای این دستگاه پر است؛ چند تای کهنه را حذف کنید":"جای پیش\u200Cفاکتورهای این دستگاه پر است؛ چند پیش\u200Cفاکتور\u0650 کهنه را حذف کنید","هزینه\u0654 خودکار":"فیس\u0650 خودکار","هر خط یک هزینه: «کیسه = 5» برای هر فاکتور، یا «برنج، شکر: بسته\u200Cبندی = 2» برای هر واحد\u0650 همین کالاها (نام یا بارکد). هزینه خودکار ته سبد می\u200Cآید و با سبد عوض می\u200Cشود. خالی یعنی خاموش. فقط روی همین دستگاه.":"هر خط یک فیس: «کیسه = 5» برای هر فاکتور، یا «برنج، شکر: بسته\u200Cبندی = 2» برای هر واحد\u0650 همین کالاها (نام یا بارکد). فیس\u0650 خودکار در آخر\u0650 سبد می\u200Cآید و با سبد تغییر می\u200Cکند. خالی یعنی خاموش. فقط روی همین دستگاه.","شلوغ":"مصروفیت\u0650 زیاد","شلوغ — {} دقیقه\u0654 دیگر":"مصروف — {} دقیقه\u0654 دیگر","شلوغم {} دقیقه":"مصروفم {} دقیقه","سرمان شلوغ است":"زیاد مصروف هستیم","بی ناحیه، همان هزینه و حداقل\u0650 «تنظیمات» برای همه\u200Cجا. با ناحیه، پین\u0650 مشتری خودکار سنجیده می\u200Cشود؛ بیرون از ناحیه\u200Cها ارسال پذیرفته نمی\u200Cشود و «دریافت از دکان» پیشنهاد می\u200Cشود.":"بدون ناحیه، همان کرایه و حداقل\u0650 «تنظیمات» برای همه\u200Cجا. با ناحیه، پین\u0650 مشتری خودکار سنجیده می\u200Cشود؛ بیرون از ناحیه\u200Cها ارسال پذیرفته نمی\u200Cشود و «دریافت از دکان» پیشنهاد می\u200Cشود.","سرور در دسترس نیست — دوباره بزنید":"سرور در دسترس نیست — دوباره کوشش کنید","درگاه\u0650 حساب\u200Cپی پاسخ نداد — دوباره بزنید":"درگاه\u0650 حساب\u200Cپی جواب نداد — دوباره کوشش کنید","سرور این پرداخت را نمی\u200Cشناسد — دوباره بزنید":"سرور این پرداخت را نمی\u200Cشناسد — دوباره کوشش کنید","مهلت\u0650 پرداخت تمام شد — فروش ثبت نشد":"میعاد\u0650 پرداخت ختم شد — فروش ثبت نشد","گزارش نیامد. اینترنت را ببینید و دوباره بزنید.":"گزارش نیامد. انترنت را بررسی کنید و دوباره کوشش کنید.","از سرور نیامد. اینترنت را ببینید و دوباره بزنید.":"از سرور نیامد. انترنت را بررسی کنید و دوباره کوشش کنید.","قاعده\u0654 دکان ذخیره نشد؛ اینترنت را ببینید و دوباره بزنید":"قاعده\u0654 دکان ذخیره نشد؛ انترنت را بررسی کنید و دوباره کوشش کنید","سرم شلوغ است":"مصروف هستم","خیلی دور است":"زیاد دور است","راه افتادم":"روان شدم","تلاش\u0650 دوباره":"کوشش\u0650 دوباره","بسته دست\u0650 شماست":"بسته در دست\u0650 شماست","پیش\u0650 مشتری هستید":"نزد\u0650 مشتری هستید"},"keep":["فروشگاه من","تازه کردن","تازه کنید","تازه کن","تازه می\u200Cشود","تازه نمی\u200Cشود","تازه شد","تازه نشد","تازه شود","تازه شدن","تازه می\u200Cکند","تازه\u200Cها"],"vowelTails":{"":"","\u0654":"\u0650","\u200Cها":"\u200Cها","\u200Cهای":"\u200Cهای","\u200Cای":"ی","\u200Cاش":"ش","\u200Cام":"م","\u200Cات":"ت","\u200Cمان":"\u200Cمان","\u200Cتان":"\u200Cتان","\u200Cشان":"\u200Cشان","مان":"مان","تان":"تان","شان":"شان"},"consonantTails":["","ها","\u200Cها","های","\u200Cهای","ی","م","ت","ش","مان","تان","شان","\u200Cمان","\u200Cتان","\u200Cشان","یم","ید","ند"]};
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

  /** کلیدِ PHRASES (`{}` به‌جای هر بخشِ protect‌شده) و خودِ مقدارها، به ترتیب (همان `DariText.shape`) */
  function shapeOf(fa) {
    var key = '';
    var values = [];
    var i = 0;
    while (i < fa.length) {
      var open = fa.indexOf(OPEN, i);
      if (open < 0) open = fa.length;
      key += strip(fa.substring(i, open));
      if (open >= fa.length) break;
      var depth = 0;
      var j = open;
      while (j < fa.length) {
        if (fa[j] === OPEN) depth++; else if (fa[j] === CLOSE && --depth === 0) break;
        j++;
      }
      values.push(strip(fa.substring(open + 1, Math.min(j, fa.length))));
      key += '{}';
      i = j + 1;
    }
    return { key: key, values: values };
  }

  /** جملهٔ کاملِ دری با مقدارهای همان نوشته؛ کلید نبود یا شمارِ {} نخواند ⇒ null (لایهٔ واژه) */
  function template(fa) {
    var s = shapeOf(fa);
    if (!own(DATA.phrases, s.key)) return null;
    var parts = DATA.phrases[s.key].split('{}');
    if (parts.length - 1 !== s.values.length) return null;
    var out = parts[0];
    for (var k = 0; k < s.values.length; k++) out += s.values[k] + parts[k + 1];
    return out;
  }

  /** نوشتهٔ فارسی ⇒ دری (همان `DariText.of`)؛ بخشِ protect‌شده بایت‌به‌بایت همان می‌ماند */
  function run(fa, glue) {
    if (own(DATA.phrases, fa)) return DATA.phrases[fa];
    if (fa.indexOf(OPEN) < 0 && fa.indexOf(CLOSE) < 0) return hasPersian(fa) ? words(fa, false, false) : fa;
    var whole = template(fa);
    if (whole !== null) return whole;
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

  function shape(fa) { return shapeOf(fa).key; }

  var api = { of: of, t: t, protect: protect, strip: strip, shape: shape, DATA: DATA, KEYS: KEYS, OPEN: OPEN, CLOSE: CLOSE };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else if (g && g.VILL3N) g.VILL3N.tx = t;
})(this);
