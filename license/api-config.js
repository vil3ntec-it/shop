/* ==========================================================
   توحید — پیکربندیِ اتصال به Backend مرکزی
   ----------------------------------------------------------
   تنها جایی که نشانیِ سرور از آن خوانده می‌شود.

   تا امروز چهار جای مختلف (index.html، license-client.js،
   shop-sync.js، vip.js) هر کدام خودشان نشانی را از localStorage
   می‌خواندند و هر کدام قاعدهٔ خودشان را داشتند برای برداشتنِ `/` آخر و
   نشانی‌های قدیمی. یعنی یک قاعده، چهار بار — و هر بار کمی فرق داشت.

   قاعدهٔ اصلی که این فایل نگه می‌دارد:

       برنامه یک «دامنه» می‌شناسد، نه یک «سرور».

   نشانیِ IP هیچ‌جا نوشته نمی‌شود. اگر Backend فردا از رایانهٔ خانه به یک
   VPS برود، تا وقتی همان دامنه به جای تازه اشاره کند، هیچ‌کدام از
   برنامه‌ها (وب، اندروید، دسکتاپ) نمی‌فهمند چیزی عوض شده.

   ── چطور نشانی را تنظیم کنید ─────────────────────────────────────
   یک خط در `index.html`، داخلِ <head>:

       <meta name="tohid-api-base" content="https://api.YOURDOMAIN.com">

   همین. اگر نگذارید، برنامه کاملاً آفلاین کار می‌کند و هیچ قابلیتی
   قفل نمی‌شود — تا وقتی کاربر خودش نشانی را در تنظیمات بزند.
   ========================================================== */
(function () {
  'use strict';

  /** نسخهٔ API — یک بار اینجا، نه پخش در مسیرها */
  const VERSION = 'v1';
  const PREFIX = '/api/' + VERSION;

  /** جایی که نشانیِ دستیِ کاربر می‌نشیند (ساختِ خودی / سرور شخصی) */
  const STORE_KEY = 'tohid-license-server-url';
  /** نامِ خیلی قدیمی؛ آن روزها نشانیِ WebSocket بود */
  const LEGACY_KEY = 'tohid-shop-server-url';

  const lsGet = (k) => { try { return localStorage.getItem(k); } catch { return null; } };
  const lsSet = (k, v) => { try { localStorage.setItem(k, v); } catch {} };

  /**
   * نشانی را به یک شکلِ واحد می‌رساند.
   *
   * `/` آخر، فاصله، و پیشوندِ `/api/v1` که کاربر از روی مرورگر کپی کرده
   * — هر سه برداشته می‌شود. بدونِ این، مسیرها `/api/v1/api/v1/…`
   * می‌شدند و هر درخواست ۴۰۴ می‌گرفت.
   *
   * نشانیِ بی‌طرح `https://` می‌گیرد، نه `http://`. پیش‌فرضِ ناامن،
   * پیش‌فرضِ غلط است.
   */
  function normalize(raw) {
    let s = String(raw || '').trim();
    if (!s) return '';
    // نشانیِ خیلی قدیمیِ WebSocket
    s = s.replace(/^ws:/i, 'http:').replace(/^wss:/i, 'https:');
    if (!/:\/\//.test(s)) s = 'https://' + s;
    s = s.replace(/\/+$/, '');
    // پیشوندِ API را خودِ برنامه می‌گذارد
    let cut = true;
    while (cut) {
      const lower = s.toLowerCase();
      if (lower.endsWith(PREFIX)) s = s.slice(0, -PREFIX.length).replace(/\/+$/, '');
      else if (lower.endsWith('/api')) s = s.slice(0, -4).replace(/\/+$/, '');
      else cut = false;
    }
    return s;
  }

  /** نشانیِ زمانِ انتشار — از `<meta>` در `index.html` */
  function deployed() {
    try {
      const tag = document.querySelector('meta[name="tohid-api-base"]');
      return normalize(tag && tag.getAttribute('content'));
    } catch { return ''; }
  }

  /** آیا نشانی در خودِ نسخهٔ منتشرشده نشسته است */
  function isLocked() { return deployed() !== ''; }

  /* ==========================================================
     شناسهٔ این برنامه نزدِ سرورِ مرکزی
     ----------------------------------------------------------
     سرورِ مرکزی یکی است و چند برنامه از آن احراز هویت می‌گیرند. هر
     درخواستی که نشست می‌سازد می‌گوید مالِ کدام است، و سرور توکن را به
     همان بخش مهر می‌زند: توکنِ یک برنامه در برنامهٔ دیگر پیدا نمی‌شود.

     تا دیروز این نسخه هیچ‌وقت نمی‌گفت کیست و به پیش‌فرضِ سرور تکیه
     می‌کرد («نگفتی، پس دکانی») — و در `support.js` هم دو بار رشتهٔ
     `'shop'` مستقیم داخلِ کد نوشته شده بود.

     مثلِ نشانیِ سرور، از `<meta>` در `index.html` می‌آید:

         <meta name="tohid-app-id" content="shop">

     نبودنش یعنی `shop`، همان چیزی که این برنامه از روزِ اول بوده — پس
     نسخه‌ای که آن خط را ندارد هم درست کار می‌کند.

     ⚠️ مقدارهایی که سرور امروز می‌شناسد در `server/src/lib/tenancy.js`
     تعریف شده‌اند (`shop` و `pump`). نامِ ناشناخته را سرور رد می‌کند.
     ========================================================== */
  const DEFAULT_APP_ID = 'shop';

  function appId() {
    try {
      const tag = document.querySelector('meta[name="tohid-app-id"]');
      const v = (tag && tag.getAttribute('content') || '').trim();
      return v || DEFAULT_APP_ID;
    } catch { return DEFAULT_APP_ID; }
  }

  /**
   * نشانیِ ریشه — بدونِ `/api/v1`، بدونِ `/` آخر.
   *
   * نشانیِ انتشار مقدم است: در نسخه‌ای که به Backend مرکزی بسته شده،
   * کاربر نه نشانی را می‌بیند و نه می‌تواند برنامه را جای دیگری ببرد.
   */
  function baseUrl() {
    const fixed = deployed();
    if (fixed) return fixed;
    return normalize(lsGet(STORE_KEY) || lsGet(LEGACY_KEY) || '');
  }

  /** گذاشتنِ نشانی به دست — فقط وقتی نسخه به جایی بسته نشده */
  function setBaseUrl(value) {
    if (isLocked()) return false;
    lsSet(STORE_KEY, normalize(value));
    return true;
  }

  function isConfigured() { return baseUrl() !== ''; }

  /** نشانیِ کاملِ یک مسیر — پیشوند یک بار، همین‌جا */
  function url(path) {
    const clean = String(path || '');
    return baseUrl() + PREFIX + (clean.startsWith('/') ? clean : '/' + clean);
  }

  /**
   * نسخهٔ این نسخهٔ وب — قرینهٔ `versionName` در برنامهٔ اندروید.
   *
   * مثلِ شناسه، از `<meta>` می‌آید:
   *
   *     <meta name="tohid-app-version" content="3.2">
   *
   * فقط ثبت می‌شود و هیچ تصمیمی با آن گرفته نمی‌شود. نبودنش هم اشکالی
   * ندارد: سرور خالی را «نگفت» می‌فهمد.
   */
  function appVersion() {
    try {
      const tag = document.querySelector('meta[name="tohid-app-version"]');
      return (tag && tag.getAttribute('content') || '').trim();
    } catch { return ''; }
  }

  window.TohidApiConfig = {
    VERSION, PREFIX,
    normalize, baseUrl, setBaseUrl, isConfigured, isLocked, url,
    appId, appVersion,
  };
})();
