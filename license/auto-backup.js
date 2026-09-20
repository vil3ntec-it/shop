/* ---------------------------------------------------------------------------
 *  پشتیبانِ ابریِ خودکار — نسخهٔ وب
 *
 *  ── چه اشکالی را می‌بندد ────────────────────────────────────────────────
 *  خواستهٔ صریحِ صاحب سامانه: «هر دو برنامه بک‌اپ خودکار داشته باشن بدون
 *  نیاز به تایید و غیره… هر ۱۲ ساعت یک بک‌اپ اتومات بیان تو سرور که
 *  اطلاعات‌شون به‌روز بمونه از هر حساب کاربری جداگانه.»
 *
 *  برنامهٔ اندروید از قبل شبانه یکی می‌فرستاد. **نسخهٔ وب هیچ‌وقت هیچ
 *  پشتیبانِ خودکاری نمی‌فرستاد** — تنها راهش دکمهٔ دستی بود
 *  (`manual: true`). یعنی کاربرِ آیفون، که فقط همین را دارد، اگر خودش
 *  یادش نمی‌رفت هیچ نسخه‌ای روی سرور نداشت.
 *  ──────────────────────────────────────────────────────────────────────
 *
 *  ⛔ **بی هیچ تاییدی و بی هیچ پنجره‌ای.** نه می‌پرسد، نه پیام می‌دهد، نه
 *     کاری را قطع می‌کند. پشتیبانی که برای گرفتنش اجازه بخواهد، همان
 *     پشتیبانِ دستی است با یک نامِ دیگر.
 *
 *  ⛔ **مهرِ زمان برای هر حساب جداست.** روی یک مرورگرِ مشترک، دو دکان‌دار
 *     نباید مهرِ هم را ببینند — وگرنه دومی «تازه گرفته شده» می‌بیند و
 *     دکانش دوازده ساعت بی پشتیبان می‌ماند. همان قاعدهٔ «حالِ Sync هر
 *     حساب جداست».
 *
 *  ⛔ **هیچ‌وقت چیزی را نمی‌شکند.** نت نبودن، اشتراک تمام شدن، سرور خواب
 *     بودن — هیچ‌کدام نباید دکانِ باز را لمس کنند. هر خطا یعنی «دفعهٔ بعد».
 * ------------------------------------------------------------------------- */
(function () {
  'use strict';

  /** هر دوازده ساعت — خواستهٔ صریحِ صاحب سامانه */
  var EVERY_MS = 12 * 60 * 60 * 1000;

  /** هر چند دقیقه ساعت را نگاه کنیم (خودِ نگاه کردن هیچ خرجی ندارد) */
  var TICK_MS = 5 * 60 * 1000;

  /*
   *  ⚠️ سرِ باز شدنِ صفحه کمی صبر می‌کنیم.
   *
   *  دکان‌دار صفحه را باز می‌کند تا کار کند؛ فرستادنِ چند مگابایت در همان
   *  ثانیهٔ اول یعنی صفحه‌ای که کند بالا می‌آید. پشتیبان عجله ندارد.
   */
  var FIRST_MS = 90 * 1000;

  var KEY = 'tohid.autobackup.at';
  var timer = null;
  var busy = false;
  var payloadOf = null;

  function lic() { return window.TohidLicense || null; }

  /** کلیدِ مهر برای همین حساب — بی حساب، هیچ */
  function stampKey() {
    var l = lic();
    if (!l || !l.isLoggedIn || !l.isLoggedIn()) return '';
    var who = '';
    try { who = (l.userLabel && l.userLabel()) || ''; } catch (e) { who = ''; }
    /*
     *  ⚠️ برچسبِ حساب کافی است و عمداً شناسهٔ خام نیست: این فقط یک کلیدِ
     *  محلی برای «کِی آخرین بار فرستادم» است، نه چیزی که به سرور برود.
     *  چیزی که به سرور می‌رود حسابش را از **توکن** می‌گیرد، نه از این.
     */
    return KEY + '.' + (who || 'account');
  }

  function lastAt() {
    var k = stampKey();
    if (!k) return 0;
    try { return Number(localStorage.getItem(k) || 0) || 0; } catch (e) { return 0; }
  }

  function mark(at) {
    var k = stampKey();
    if (!k) return;
    try { localStorage.setItem(k, String(at)); } catch (e) { /* حالتِ ناشناس */ }
  }

  function due() {
    if (!stampKey()) return false;               // وارد نشده
    if (!navigator.onLine) return false;          // نت نیست
    return Date.now() - lastAt() >= EVERY_MS;
  }

  /**
   * یک بار فرستادن.
   *
   * @param {boolean} force نادیده گرفتنِ ساعت (برای آزمون و دکمهٔ «همین حالا»)
   * @returns {Promise<boolean>} رفت یا نه
   */
  async function runOnce(force) {
    if (busy) return false;
    if (!force && !due()) return false;
    var l = lic();
    if (!l || !l.backups || !l.isLoggedIn || !l.isLoggedIn()) return false;
    if (typeof payloadOf !== 'function') return false;

    busy = true;
    try {
      var data = payloadOf();
      if (!data) return false;
      var text = typeof data === 'string' ? data : JSON.stringify(data);
      if (!text || text.length < 2) return false;

      //  ⛔ `manual: false` — این نسخه خودکار است و در فهرست هم باید همان را بگوید
      await l.backups.upload(text, { manual: false, ext: 'json' });
      mark(Date.now());
      return true;
    } catch (e) {
      /*
       *  ⛔ مهر **زده نمی‌شود** وقتی نرفته.
       *
       *  وگرنه یک خطای گذرا یعنی دوازده ساعتِ دیگر بی پشتیبان — و کاربر
       *  هیچ‌وقت نمی‌فهمد. نرفت ⇒ تیکِ بعدی دوباره تلاش می‌کند.
       */
      return false;
    } finally {
      busy = false;
    }
  }

  /**
   * روشن کردن.
   *
   * @param {() => any} provider چیزی که باید پشتیبان شود — همان
   *   `backupPayload()`ِ خودِ صفحه. این فایل از ساختارِ دفتر هیچ نمی‌داند
   *   و نباید بداند.
   */
  function start(provider) {
    if (typeof provider === 'function') payloadOf = provider;
    if (timer) return;

    setTimeout(function () { runOnce(false); }, FIRST_MS);
    timer = setInterval(function () { runOnce(false); }, TICK_MS);

    /*
     *  ⚠️ بازگشتِ نت هم یک فرصت است: گوشی‌ای که تمامِ شب آفلاین بوده،
     *  همان لحظه که وصل می‌شود باید بفرستد، نه پنج دقیقهٔ بعد.
     */
    window.addEventListener('online', function () { runOnce(false); });
  }

  function stop() {
    if (timer) clearInterval(timer);
    timer = null;
  }

  function status() {
    return {
      every: EVERY_MS,
      last: lastAt(),
      due: due(),
      running: Boolean(timer),
      ready: typeof payloadOf === 'function',
    };
  }

  window.TohidAutoBackup = { start: start, stop: stop, runOnce: runOnce, status: status, EVERY_MS: EVERY_MS };
})();
