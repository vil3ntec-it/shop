/* ==========================================================
   فروشگاه من | موتور Sync v1 — سمتِ وب
   ----------------------------------------------------------
   هستهٔ بی‌مرورگر در `sync-core.js` است؛ این فایل آن را به شبکه و به
   دفترِ روی صفحه وصل می‌کند.

       saveState()  ⇒  recordChange()  ⇒  تفاضل ⇒ صف ⇒ Push
       wss «changed» یا هر ۳۰ ثانیه  ⇒  Pull ⇒ اعمال روی دفتر

   ── قاعده‌هایی که نباید بشکنند ──────────────────────────────────
   ⛔ **opی که سرور نپذیرفته از صف بیرون نمی‌رود.** فقط `applied` و
      `duplicate` پاک می‌شوند. `rejected` هم بیرون می‌رود ولی با دلیل
      در دفترِ کنار می‌نشیند و در تنظیمات دیده می‌شود.
   ⛔ **۴۲۶ یعنی نگه‌دار، نه دور بریز.** سرور از برنامه عقب‌تر است؛
      opها سرِ جایشان می‌مانند تا سرور به‌روز شود (بندِ ۲۰.۶).
   ⛔ **حالِ همگام‌سازیِ هر حساب جداست** (`…-<userId>`). بی این، سایهٔ
      حسابِ قبلی روی مرورگرِ مشترک همهٔ ردیف‌هایش را «تغییرِ تازه»
      می‌دید و داخلِ دکانِ نفرِ بعدی می‌فرستاد.
   ⚠️ **اعمالِ opهای رسیده باید سایه را هم جلو ببرد**، وگرنه تفاضلِ
      بعدی همان‌ها را دوباره به سرور پس می‌فرستد — حلقهٔ بی‌پایان.
   ⛔ **یک برگه نویسنده است** (`TohidTabLock`، پایین‌تر). سایه، صف و cursor
      میانِ برگه‌های یک مرورگر مشترک‌اند ولی دفترِ حافظه نه.
   ========================================================== */
(function () {
  'use strict';

  var core = window.TohidSyncCore;
  var acc = window.TohidAccount;
  if (!core || !acc) return;

  /* ==========================================================
     یک برگهٔ نویسنده — دو برگه، یک دفتر
     ----------------------------------------------------------
     سایه، صف و cursor در حافظهٔ مشترکِ مرورگرند ولی هر برگه دفترِ حافظهٔ
     خودش را دارد. برگهٔ کهنه (B) که پس از ثبتِ کالای تازه در برگهٔ A ذخیره
     کند، آن کالا را «حذف‌شده» می‌بیند و DELETE ِ آن به سرور و همهٔ
     دستگاه‌ها می‌رود؛ `saveState` ِ همان برگه هم دفترِ ذخیره‌شده را با
     نسخهٔ کهنه‌اش رونویسی می‌کند.
     ⛔ پس فقط یک برگه نویسنده است (Web Locks، `tohid-ledger-writer`، تا
     بسته شدنِ برگه). بقیه فقط‌خواندنی‌اند: موتور نه ثبت می‌کند، نه
     push/pull، و `saveState` ِ صفحه (`readOnly()`) چیزی نمی‌نویسد؛ نوار و
     پیامِ روشن. نویسنده که بسته شد، برگهٔ منتظر خودش تازه می‌شود (دفترش
     کهنه است) و نویسنده می‌شود.
     تا پاسخِ قفل (چند میلی‌ثانیه پس از بار شدن) همان رفتارِ امروز؛ v1 هر
     اثر را همان دم می‌سنجد (پس از هر درخواستِ شبکه هم)، v3 پشتِ `guardTabs`.
     بی Web Locks (مرورگرِ خیلی کهنه) یا خطای آن ⇒ همان رفتارِ پیشین.
     ========================================================== */
  var TAB_LOCK = 'tohid-ledger-writer';
  var TAB_MSG = 'این برنامه در برگهٔ دیگری از همین مرورگر باز است. این برگه فقط برای دیدن است و '
    + 'چیزی در آن ذخیره یا همگام نمی‌شود؛ همان برگه را به کار ببرید یا آن را ببندید.';
  var tabLock = (function () {
    var role = 'writer';
    var ready = Promise.resolve(role);
    var locks = (typeof navigator !== 'undefined' && navigator && navigator.locks) || null;
    function showBanner() {
      try {
        if (typeof document === 'undefined' || !document.body || document.getElementById('tohid-tab-reader')) return;
        var el = document.createElement('div');
        el.id = 'tohid-tab-reader';
        el.setAttribute('role', 'alert');
        el.dir = 'rtl';
        el.textContent = TAB_MSG;
        el.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:2147483000;padding:10px 16px;'
          + 'background:#b42318;color:#fff;font-weight:600;line-height:1.6;text-align:center';
        document.body.appendChild(el);
      } catch (e) { /* بی صفحه */ }
    }
    function waitTurn() {
      //  نویسنده بسته شد ⇒ دفترِ این برگه کهنه است: تازه‌سازی (پس از آن همین برگه نویسنده است)
      try {
        locks.request(TAB_LOCK, function () {
          try { location.reload(); } catch (e) { /* بیرون از مرورگر */ }
          return new Promise(function () { });
        }).catch(function () { });
      } catch (e) { /* هیچ */ }
    }
    if (locks && typeof locks.request === 'function') {
      role = 'pending';
      ready = new Promise(function (resolve) {
        function settle(r) {
          if (role !== 'pending') return;
          role = r;
          resolve(r);
          if (r === 'reader') { showBanner(); waitTurn(); }
          try { window.dispatchEvent(new CustomEvent('tohid-tab-role', { detail: { role: r } })); } catch (e) { /* قدیمی */ }
        }
        try {
          locks.request(TAB_LOCK, { ifAvailable: true }, function (lock) {
            if (!lock) { settle('reader'); return undefined; }
            settle('writer');
            return new Promise(function () { });   // تا بسته شدنِ برگه نگه داشته می‌شود
          }).catch(function () { settle('writer'); });
        } catch (e) { settle('writer'); }
      });
    }
    return {
      LOCK: TAB_LOCK,
      message: TAB_MSG,
      ready: ready,
      role: function () { return role; },
      /** صفحه: فقط برگهٔ نویسنده (و تا روشن شدن، برگهٔ تازه) دفتر را ذخیره می‌کند */
      readOnly: function () { return role === 'reader'; },
    };
  })();
  window.TohidTabLock = tabLock;

  /** خطای درِ «گرفتنِ نسخهٔ سرور» در برگهٔ فقط‌خواندنی */
  function otherTabError() { var e = new Error(TAB_MSG); e.code = 'other_tab'; return e; }

  /**
   *  درهای v3 (`window.TohidSync` ِ sync-v3-engine) پشتِ قفلِ برگه: زمان‌سنج‌های v3 درونِ خودش‌اند،
   *  پس برگهٔ فقط‌خواندنی آن را راه نمی‌اندازد و تا روشن شدنِ نقش (چند میلی‌ثانیه) صبر می‌کند.
   *  تفاضل‌محور است، پس ثبتِ عقب‌افتاده چیزی را گم نمی‌کند.
   */
  function guardTabs(api) {
    if (!api) return;
    ['start', 'onSignedIn', 'sync', 'pullOnce', 'restoreFromServer', 'firstSync', 'pushBaseline', 'connectLive'].forEach(function (n) {
      var orig = api[n];
      if (typeof orig !== 'function') return;
      api[n] = function () {
        var args = arguments;
        if (tabLock.role() === 'writer') return orig.apply(api, args);
        return tabLock.ready.then(function (r) {
          if (r === 'writer') return orig.apply(api, args);
          if (n === 'restoreFromServer') throw otherTabError();
          if (n === 'firstSync') return { did: 'other_tab' };
          return n === 'pullOnce' ? 0 : undefined;
        });
      };
    });
    var rec = api.recordChange;
    if (typeof rec === 'function') {
      api.recordChange = function () {
        var args = arguments;
        if (tabLock.role() === 'writer') return rec.apply(api, args);
        if (tabLock.role() === 'pending') tabLock.ready.then(function (r) { if (r === 'writer') rec.apply(api, args); });
        return 0;
      };
    }
    var st = api.status;
    if (typeof st === 'function') {
      api.status = function () {
        var s = st.apply(api, arguments);
        if (s && tabLock.role() === 'reader') { s.otherTab = true; s.lastError = TAB_MSG; }
        return s;
      };
    }
  }

  //  Sync v3 پشتِ تنظیمِ هر دستگاه (`tohid-sync-engine`، پیش‌فرض v1) — `license/sync-v3-engine.js`.
  //  خاموش (پیش‌فرض) ⇒ همین موتورِ v1، بی هیچ تغییری؛ روشن ⇒ همان رابطِ `window.TohidSync` را v3 می‌سازد.
  if (window.TohidSyncV3 && window.TohidSyncV3.choice() === 'v3' && window.TohidSyncV3.install(acc)) {
    guardTabs(window.TohidSync);
    return;
  }

  var PUSH_DEBOUNCE_MS = 500;      // بندِ ۲۰.۲
  var PUSH_TICK_MS = 2000;         // تا وقتی صف خالی نشده
  var PULL_EVERY_MS = 30000;       // وقتی سوکت قطع است
  var HEARTBEAT_MS = 15 * 60 * 1000;

  /* ---------- حافظهٔ محلی ---------- */
  function readJson(key, dflt) {
    try { var raw = localStorage.getItem(key); return raw ? (JSON.parse(raw) ?? dflt) : dflt; }
    catch (e) { return dflt; }
  }
  function writeJson(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); return true; }
    catch (e) { return false; }
  }

  var io = { read: readJson, write: writeJson };

  /* ==========================================================
     شمارشِ صف — کش‌شده، عمداً
     ----------------------------------------------------------
     ⚠️ **قاعدهٔ سرعتِ مخزن.** `queue().size()` کلِ دفترِ صف را از
     `localStorage` می‌خواند و تجزیه می‌کند. این عدد هم در تیکِ
     دوثانیه‌ای لازم است و هم در هر `announce()` (یعنی با هر تغییرِ
     کاربر). با صفِ چندهزارتاییِ یک دکانِ سه‌روز‌آفلاین، همین یک خط
     می‌شد یک تجزیهٔ چندمگابایتی هر دو ثانیه — و صفحه همان‌جا تکان
     می‌خورد.
     پس عدد کش می‌شود و فقط وقتی دوباره خوانده می‌شود که صف واقعاً دست
     خورده باشد.
     ⛔ هر جایی که به صف می‌نویسد باید `forgetCounts()` را صدا بزند.
     ========================================================== */
  var counts = null;

  function forgetCounts() { counts = null; }

  function queueCounts() {
    if (counts) return counts;
    var q = queue();
    counts = { queued: q.size(), dropped: q.dropped().length };
    return counts;
  }

  /** کلیدهای این حساب. حسابِ نبوده هم کلیدِ خودش را دارد («anon»). */
  function keyOf(suffix) {
    var uid = String(acc.account().userId || 'anon').replace(/[^A-Za-z0-9_-]/g, '_');
    return 'tohid-sync-v1-' + uid + '-' + suffix;
  }

  function state() {
    return readJson(keyOf('state'), null) || {
      cursor: 0, lastPushAt: 0, lastPullAt: 0, lastOkAt: 0,
      lastError: '', lastErrorAt: 0, holding: false,
      serverSchema: 0, upgradeAvailable: false, snapshotAt: 0,
    };
  }
  function patchState(patch) {
    var next = Object.assign(state(), patch);
    writeJson(keyOf('state'), next);
    return next;
  }
  function shadow() { return readJson(keyOf('shadow'), null); }
  function setShadow(data) { return writeJson(keyOf('shadow'), core.shadowOf(data)); }
  function queue() { return new core.OpQueue(io, keyOf('oplog')); }

  /* ==========================================================
     پلِ دفتر — صفحه می‌گوید دفتر کجاست
     ----------------------------------------------------------
     `index.html` صاحبِ دفتر است. این‌جا فقط دو تابع می‌گیریم: بخوان،
     بنویس. بی این، موتور باید از شکلِ داخلیِ صفحه خبر می‌داشت.
     ========================================================== */
  var ledger = {
    read: function () { return null; },
    write: function () { },
  };
  function bind(hooks) {
    if (hooks && typeof hooks.read === 'function') ledger.read = hooks.read;
    if (hooks && typeof hooks.write === 'function') ledger.write = hooks.write;
  }

  /* ---------- رویداد و چراغ ---------- */
  var listeners = [];
  function onChange(fn) { listeners.push(fn); return function () { var i = listeners.indexOf(fn); if (i >= 0) listeners.splice(i, 1); }; }
  function announce() {
    var s = status();
    for (var i = 0; i < listeners.length; i++) {
      try { listeners[i](s); } catch (e) { /* شنوندهٔ شکسته بقیه را نمی‌برد */ }
    }
    try {
      window.dispatchEvent(new CustomEvent('tohid-sync', { detail: s }));
    } catch (e) { /* مرورگرِ خیلی قدیمی */ }
  }

  var busy = false;

  function status() {
    var st = state();
    var n = queueCounts();
    var other = tabLock.readOnly();
    return {
      otherTab: other,
      configured: acc.configured(),
      signedIn: acc.isSignedIn(),
      online: navigator.onLine !== false,
      busy: busy,
      queued: n.queued,
      dropped: n.dropped,
      cursor: Number(st.cursor || 0),
      lastOkAt: Number(st.lastOkAt || 0),
      lastPushAt: Number(st.lastPushAt || 0),
      lastPullAt: Number(st.lastPullAt || 0),
      lastError: other ? TAB_MSG : (st.lastError || ''),
      lastErrorAt: Number(st.lastErrorAt || 0),
      holding: !!st.holding,
      needsShop: !!st.needsShop,
      upgradeAvailable: !!st.upgradeAvailable,
      serverSchema: Number(st.serverSchema || 0),
      schemaVersion: core.SCHEMA_VERSION,
      snapshotAt: Number(st.snapshotAt || 0),
      live: socketOpen,
      dot: core.dotOf({
        error: !!st.lastError, online: navigator.onLine !== false,
        signedIn: acc.isSignedIn(), configured: acc.configured(),
        queued: n.queued, busy: busy,
      }),
    };
  }

  /* ==========================================================
     ۱) هر تغییرِ دفتر ⇒ op
     ----------------------------------------------------------
     `saveState()` این را صدا می‌زند. هیچ راهِ دیگری برای نوشتن در
     دفتر نیست، پس هیچ تغییری بی op نمی‌ماند.
     ========================================================== */
  var pushTimer = null;

  function recordChange(reason) {
    if (tabLock.readOnly()) return 0;   // برگهٔ دیگر نویسنده است: تفاضلِ دفترِ کهنه با سایهٔ مشترک «حذف» می‌سازد
    var data = ledger.read();
    if (!data) return 0;
    var base = shadow();
    if (!base) {
      //  نخستین بار روی این مرورگر: دفترِ امروز «تغییر» نیست، حالتِ
      //  پایه است. اگر همین‌جا تفاضل می‌گرفتیم، کلِ دفتر یک‌جا به
      //  سرور می‌رفت — دقیقاً همان کاری که قانونِ طلایی منع کرده.
      //  فرستادنِ دفترِ موجود کارِ `pushBaseline()` است و فقط یک بار،
      //  با خواستِ خودِ کاربر یا سرِ نخستین ورود.
      setShadow(data);
      announce();
      return 0;
    }
    var out = core.diff(base, data);
    if (!out.ops.length) return 0;
    /*
     *  صف و سایه با هم جلو می‌روند یا هیچ‌کدام. اگر صف نوشته نشود و سایه
     *  جلو برود، تغییر برای همیشه گم است؛ اگر صف نوشته شود و سایه نه،
     *  دفعهٔ بعد همان op دوباره ساخته می‌شود و `$inc` دو بار جمع می‌شود.
     */
    var q = queue();
    var before = q.all();
    notePushSince(!before.length);
    if (q.push(out.ops) < 0) {
      patchState({ lastError: 'حافظهٔ مرورگر پر است؛ تغییرها روی همین دستگاه مانده‌اند و هنوز به سرور نرفته‌اند' });
      announce();
      return 0;
    }
    forgetCounts();
    if (!setShadow(data)) {
      q.replace(before);
      forgetCounts();
      patchState({ lastError: 'حافظهٔ مرورگر پر است؛ تغییرها روی همین دستگاه مانده‌اند و هنوز به سرور نرفته‌اند' });
      announce();
      return 0;
    }
    announce();
    schedulePush(reason);
    return out.ops.length;
  }

  /**
   *  `since` ِ push (پرچمِ سرورِ `sync.cursor_from_since`، #481): «یادداشت» پیش از رفتنِ op به صف —
   *  صفِ خالی ⇒ cursor ِ همین حالا، وگرنه فقط پایین می‌آید (شرح سرِ `pushSince` در sync-core.js).
   *  ⚠️ پوسته stale-while-revalidate است و sync-core.js ِ کهنه شاید هنوز این تابع را نداشته باشد ⇒
   *  بی یادداشت و بی `since` (سرور همان تأییدِ pull ِ پیشین را می‌گیرد: محتاط)، هرگز خطا در ثبت.
   */
  function notePushSince(queueWasEmpty) {
    if (typeof core.pushSinceNote !== 'function') return;
    var st = state();
    patchState({ pushSince: core.pushSinceNote(st.pushSince, queueWasEmpty, st.cursor) });
  }
  function sinceForPush() {
    if (typeof core.pushSince !== 'function') return undefined;
    var st = state();
    return core.pushSince(st.pushSince, st.cursor);
  }

  /**
   *  F0-04 (پلهٔ ۳): سایهٔ پیش از این نسخه فهرست‌های تنظیماتی را ندارد.
   *
   *  اگر همین‌طور بماند و opی از دستگاهِ دیگر برسد، `setShadow(دفتر)`
   *  همهٔ نام‌های **این** دستگاه را «رفته» ثبت می‌کند و هرگز نمی‌روند.
   *  پس پیش از هر دور، یک بار تفاضل گرفته می‌شود: سایه بی کلید ⇒ همهٔ
   *  نام‌های محلی یک بار «درج» می‌روند (شناسه از نام است؛ تکراری نمی‌سازد).
   */
  function ensureListShadow() {
    var base = shadow();
    var data = base && ledger.read();
    if (!data) return 0;
    var lists = core.LIST_COLLECTIONS || [];
    for (var i = 0; i < lists.length; i++) {
      if (Array.isArray(data[lists[i]]) && !Array.isArray(base[lists[i]])) return recordChange('lists');
    }
    return 0;
  }

  function schedulePush() {
    if (pushTimer) return;
    pushTimer = setTimeout(function () { pushTimer = null; pump(); }, PUSH_DEBOUNCE_MS);
  }

  /**
   *  دفترِ امروز را یک‌جا به سرور می‌دهد — فقط برای دستگاهِ نخست.
   *
   *  ⚠️ این تنها جایی است که «کلِ دفتر» می‌رود و عمدی است: حسابی که
   *  تازه ساخته شده روی سرور هیچ ردیفی ندارد و باید یک بار پر شود.
   *  از آن به بعد فقط تفاضل. دستگاهِ دوم همین را از `snapshot`
   *  می‌گیرد، نه از این راه.
   */
  function pushBaseline() {
    if (tabLock.readOnly()) return 0;
    var data = ledger.read();
    if (!data) return 0;
    var out = core.diff({}, data);
    if (out.ops.length) { notePushSince(queue().size() === 0); queue().push(out.ops); }
    forgetCounts();
    setShadow(data);
    announce();
    schedulePush();
    return out.ops.length;
  }

  /* ==========================================================
     ۲) Push — دسته‌ای، با عقب‌نشینیِ نمایی
     ========================================================== */
  var attempt = 0;
  var retryTimer = null;

  /** حسابی که هنوز دکان نساخته — خطا نیست، یک کارِ نکرده است. */
  var NOT_YET = ['no_shop', 'no_account', 'no_station'];

  function failed(message, code) {
    if (NOT_YET.indexOf(code) !== -1) {
      /*
       *  ⚠️ چراغِ قرمز یعنی «چیزی خراب است». حسابی که تازه ساخته شده و
       *  هنوز دکانی ندارد چیزی خراب نکرده — فقط هنوز جایی برای
       *  فرستادن نیست. قرمز کردنش کاربر را دنبالِ باگی می‌فرستد که
       *  وجود ندارد.
       */
      patchState({ lastError: '', lastErrorAt: 0, needsShop: true });
      attempt += 1;
      if (retryTimer) clearTimeout(retryTimer);
      retryTimer = setTimeout(function () { retryTimer = null; pump(); }, core.BACKOFF_MAX_MS);
      announce();
      return;
    }
    patchState({ lastError: message || 'خطای نامشخص', lastErrorAt: Date.now(), needsShop: false });
    attempt += 1;
    if (retryTimer) clearTimeout(retryTimer);
    retryTimer = setTimeout(function () { retryTimer = null; pump(); }, core.backoffMs(attempt));
    announce();
  }

  function succeeded() {
    attempt = 0;
    patchState({ lastError: '', lastOkAt: Date.now() });
  }

  async function pushOnce() {
    var q = queue();
    var batch = q.batch();
    if (!batch.length) return { sent: 0, head: state().cursor };

    var body = {
      device_id: acc.deviceId(),
      //  پلهٔ همان سرور، نه بیشتر (`wireSchema` ِ sync-core.js، پرچمِ گاوصندوق)
      schema_version: core.wireSchema(state().serverSchema),
      ops: batch,
      queued: q.size(),
      //  A00: وب ردیفِ JSON ِ خام را نگه می‌دارد و null اش پاک کردنِ واقعی است — سرور
      //  تورِ «ساختِ پیش از A00» را برایش کنار می‌گذارد (server/src/lib/newer-fields.js)
      client: 'web',
      //  #481: «تا این‌جا را دیده بودم» — فقط با پرچمِ دکانِ `sync.cursor_from_since` خوانده می‌شود
      since: sinceForPush(),
    };
    var r;
    try {
      r = await acc.authFetch('/api/sync/v1/push', { method: 'POST', body: body });
    } catch (e) {
      if (e.status === 426) {
        //  سرور عقب‌تر است. هیچ opی اعمال نشده و هیچ opی هم پاک
        //  نمی‌شود؛ منتظر می‌مانیم تا سرور به‌روز شود.
        //  پلهٔ بالاتر از پایه را سرور دیگر نمی‌شناسد (پرچمِ دکان خاموش شد)؟ بارِ بعد با پایه
        var sent = body.schema_version;
        patchState({ holding: true, lastError: e.message || 'سرور باید به‌روز شود',
          serverSchema: sent > core.BASE_SCHEMA ? 0 : state().serverSchema });
        announce();
        throw e;
      }
      throw e;
    }

    patchState({ holding: false, serverSchema: Number(r.schema_version || 0), upgradeAvailable: !!r.upgrade_available });

    var ok = [];
    var bad = [];
    var results = Array.isArray(r.results) ? r.results : [];
    for (var i = 0; i < results.length; i++) {
      var res = results[i];
      if (res.status === 'applied' || res.status === 'duplicate') ok.push(res.op_id);
      else bad.push(res);
    }
    if (ok.length) { q.ack(ok); forgetCounts(); }
    if (bad.length) {
      //  ردشده‌ها از صف بیرون می‌روند — وگرنه صف تا ابد گیر می‌کند —
      //  ولی بی‌صدا نه: دلیلِ هر کدام در دفترِ کنار می‌ماند و در
      //  تنظیمات دیده می‌شود.
      var byId = {};
      for (var b = 0; b < batch.length; b++) byId[batch[b].op_id] = batch[b];
      var list = [];
      var ids = [];
      for (var j = 0; j < bad.length; j++) {
        var op = byId[bad[j].op_id];
        if (op) { list.push(Object.assign({}, op, { reason: bad[j].reason || '' })); ids.push(bad[j].op_id); }
      }
      q.drop(list, 'rejected');
      q.ack(ids);
      forgetCounts();
    }
    patchState({ lastPushAt: Date.now() });
    return { sent: batch.length, applied: ok.length, rejected: bad.length, head: Number(r.cursor || r.head || 0) };
  }

  /* ==========================================================
     ۳) Pull — تغییرهای دستگاه‌های دیگر
     ========================================================== */
  async function pullOnce() {
    if (tabLock.readOnly()) return 0;
    var st = state();
    var since = Number(st.cursor || 0);
    var r = await acc.authFetch('/api/sync/v1/pull?device_id='
      + encodeURIComponent(acc.deviceId()) + '&since=' + since);
    if (tabLock.readOnly()) return 0;   // نقش در همین فاصله روشن شد
    var ops = Array.isArray(r.ops) ? r.ops : [];
    if (ops.length) {
      var data = ledger.read();
      //  دفتر هنوز به صفحه بسته نیست: cursor جلو نمی‌رود، وگرنه همین opها
      //  هرگز روی این دستگاه نمی‌نشستند
      if (!data) return 0;
      {
        var base = shadow();
        var out = core.applyRemote(data, ops);
        ledger.write(out.data);
        //  سایه همین‌جا جلو می‌رود، وگرنه تفاضلِ بعدی همین‌ها را
        //  دوباره به سرور پس می‌فرستد — ولی «سایهٔ پیشین + همین opها»، نه کلِ
        //  دفترِ امروز (همان `SyncCommit.absorbedShadow` ِ اندروید): تغییرِ محلی‌ای
        //  که ثبتش نگرفت (حافظهٔ پر) باید در تفاضلِ بعدی بماند، وگرنه جزوِ سایه
        //  می‌شد و هرگز به سرور نمی‌رسید (SYN-01). سایهٔ نبوده ⇒ همان دفتر (پایه).
        setShadow(base ? core.applyRemote(base, ops).data : out.data);
      }
    }
    patchState({
      cursor: Number(r.cursor || since), lastPullAt: Date.now(),
      serverSchema: Number(r.schema_version || st.serverSchema || 0),
    });
    //  صفحه بیش از یک بسته داشت ⇒ همان‌جا بقیه را هم بیاور
    if (r.has_more) return (await pullOnce()) + ops.length;
    return ops.length;
  }

  /* ==========================================================
     ۴) حلقهٔ اصلی
     ========================================================== */
  async function pump() {
    if (busy) return;
    if (tabLock.readOnly()) { announce(); return; }
    if (!acc.configured() || !acc.isSignedIn()) { announce(); return; }
    if (navigator.onLine === false) { announce(); return; }
    busy = true;
    announce();
    try {
      ensureListShadow();
      var head = 0;
      var q = queue();
      var guard = 0;
      //  صف را تا ته خالی می‌کنیم، دسته‌دسته (۲۰۰ op یا ۲۵۶KB)
      while (q.size() > 0 && guard < 200) {
        guard++;
        var out = await pushOnce();
        if (!out.sent) break;
        head = out.head;
      }
      var pulled = await pullOnce();
      succeeded();
      if (pulled || head) announce();
    } catch (e) {
      failed(e && e.message, e && e.code);
      return;
    } finally {
      busy = false;
    }
    announce();
  }

  /* ==========================================================
     ۵) Snapshot — گوشیِ نو، یک بار
     ========================================================== */
  async function restoreFromServer() {
    if (tabLock.readOnly()) throw otherTabError();
    var r = await acc.authFetch('/api/sync/v1/snapshot');
    if (tabLock.readOnly()) throw otherTabError();
    var data = ledger.read() || {};
    var next = core.fromSnapshot(r, data);
    ledger.write(next);
    setShadow(next);
    patchState({ cursor: Number(r.cursor || 0), snapshotAt: Date.now(), lastOkAt: Date.now(), lastError: '' });
    announce();
    return { rows: Number(r.rows || 0), cursor: Number(r.cursor || 0) };
  }

  /**
   *  نخستین همگام‌سازیِ این مرورگر با این حساب.
   *
   *  دفترِ خالی ⇒ Snapshot (گوشیِ نو). دفترِ پر ⇒ فرستادنِ همان دفتر
   *  یک بار، چون این مرورگر تا امروز آفلاین کار می‌کرده.
   */
  async function firstSync() {
    if (tabLock.readOnly()) return { did: 'other_tab' };
    var st = state();
    if (st.snapshotAt || st.cursor) return { did: 'none' };
    var data = ledger.read() || {};
    var rows = 0;
    for (var i = 0; i < core.COLLECTIONS.length; i++) {
      var list = data[core.COLLECTIONS[i]];
      if (Array.isArray(list)) rows += list.length;
    }
    if (rows === 0) {
      await restoreFromServer();
      return { did: 'snapshot' };
    }
    /*
     *  دفترِ محلی چیزی دارد: این مرورگر تا امروز آفلاین کار می‌کرده و
     *  ردیف‌هایش هنوز روی سرور نیستند.
     *
     *  ⚠️ **اول فرستادن، بعد گرفتن** — و این ترتیب عمدی است. اگر اول
     *  pull می‌کردیم، سایه روی «دفترِ ادغام‌شده» می‌نشست و ردیف‌های
     *  محلی — که سرور آن‌ها را ندارد — دیگر «تغییر» شمرده نمی‌شدند و
     *  **هیچ‌وقت نمی‌رفتند**. با این ترتیب، ردیفِ محلی به‌شکلِ `insert`
     *  می‌رود و ردیفِ سرور در همان دور برمی‌گردد؛ هیچ‌کدام دیگری را
     *  پاک نمی‌کند، چون شناسه‌ها یکتا هستند.
     */
    pushBaseline();
    await pump();
    return { did: 'baseline', rows: rows };
  }

  /* ==========================================================
     ۶) سوکتِ زنده — فقط یک خبرِ کوچک، نه خودِ داده
     ========================================================== */
  var socket = null;
  var socketOpen = false;
  var socketAttempt = 0;
  var socketTimer = null;

  var socketDialing = false;

  /**
   * F0-11: توکن در آدرسِ سوکت (و لاگِ پراکسی) نمی‌نشیند. اول با POST یک
   * بلیتِ یک‌بارمصرفِ ۶۰ ثانیه‌ای می‌گیریم و با `?ticket=` وصل می‌شویم.
   * فقط اگر سرور هنوز این راه را ندارد (۴۰۴)، همان `?token=` قدیم.
   */
  async function liveCredential() {
    try {
      var t = await acc.authFetch('/api/sync/v1/live/ticket', { method: 'POST', body: {} });
      if (t && t.ticket) return ['ticket', String(t.ticket)];
    } catch (e) {
      if (!e || e.status !== 404) throw e;
    }
    var token = await acc.accessToken();
    return token ? ['token', token] : null;
  }

  function retryLive() {
    if (socketTimer) clearTimeout(socketTimer);
    socketAttempt += 1;
    socketTimer = setTimeout(connectLive, core.backoffMs(socketAttempt));
  }

  async function connectLive() {
    if (socket || socketDialing || !acc.configured() || !acc.isSignedIn() || tabLock.readOnly()) return;
    var cred = null;
    socketDialing = true;
    try { cred = await liveCredential(); } catch (e) {
      //  بی اینترنت یا خطای سرور: مثلِ افتادنِ سوکت، با فاصلهٔ فزاینده دوباره
      socketDialing = false;
      if (acc.isSignedIn()) retryLive();
      return;
    }
    socketDialing = false;
    if (!cred || socket || !acc.isSignedIn()) return;
    var url = '';
    try {
      var u = new URL('/api/sync/v1/live', window.TohidApiConfig.baseUrl());
      u.protocol = u.protocol === 'http:' ? 'ws:' : 'wss:';
      u.searchParams.set(cred[0], cred[1]);
      u.searchParams.set('device_id', acc.deviceId());
      u.searchParams.set('app', acc.appId());
      //  S78: این نسخه پس از 4403 (انقضای توکن) با توکنِ تازه برمی‌گردد ⇒ سرور سرِ موعد می‌بندد
      u.searchParams.set('caps', 'expiry');
      url = u.toString();
    } catch (e) { return; }

    try { socket = new WebSocket(url); } catch (e) { socket = null; return; }

    socket.onopen = function () { socketOpen = true; socketAttempt = 0; announce(); };
    socket.onmessage = function (ev) {
      var msg = null;
      try { msg = JSON.parse(ev.data); } catch (e) { return; }
      //  فقط «چیزی عوض شد» می‌آید؛ خودِ داده هرگز از این در نمی‌رود.
      //  «hello» هم: هر وصل (از جمله پس از بسته‌شدنِ 4401/4403 ِ سرور، S78) یک pump —
      //  خبری که در فاصلهٔ قطع آمد گم نشود
      if (msg && (msg.event === 'changed' || msg.event === 'hello')) pump();
      //  عکسِ کالا و بارکدِ گوشی — ‎license/photo-studio.js‎ گوش می‌دهد
      if (msg && (msg.event === 'photos' || msg.event === 'scan' || msg.event === 'hello' || msg.event === 'photo-request' || msg.event === 'inbox')) {
        try { window.dispatchEvent(new CustomEvent('tohid:live', { detail: msg })); } catch (e) { /* مرورگرِ کهنه */ }
      }
    };
    socket.onclose = function () {
      socketOpen = false;
      socket = null;
      announce();
      //  ⚠️ بی‌پایان تلاش می‌کنیم ولی با فاصلهٔ فزاینده؛ سوکتی که هر
      //  ثانیه دوباره وصل شود، هم باتری می‌خورد هم سرور را می‌کوبد
      retryLive();
    };
    socket.onerror = function () { try { socket.close(); } catch (e) { /* بسته شد */ } };
  }

  function disconnectLive() {
    if (socketTimer) { clearTimeout(socketTimer); socketTimer = null; }
    if (socket) { try { socket.onclose = null; socket.close(); } catch (e) { /* رفته */ } }
    socket = null;
    socketOpen = false;
  }

  /* ==========================================================
     ۷) روشن کردن
     ========================================================== */
  var started = false;
  var tickTimer = null;
  var pullTimer = null;
  var beatTimer = null;

  function start() {
    if (started) return;
    started = true;

    //  صفِ مانده از نشستِ قبلی — همان «سه روز آفلاین» — اول می‌رود
    //  ⚠️ عددِ کش‌شده، نه خواندنِ دوبارهٔ حافظه هر دو ثانیه
    tickTimer = setInterval(function () { if (queueCounts().queued) pump(); }, PUSH_TICK_MS);
    pullTimer = setInterval(function () { if (!socketOpen) pump(); }, PULL_EVERY_MS);
    beatTimer = setInterval(function () {
      if (acc.isSignedIn() && navigator.onLine !== false) acc.heartbeat().catch(function () { });
    }, HEARTBEAT_MS);

    window.addEventListener('online', function () { attempt = 0; pump(); connectLive(); });
    window.addEventListener('offline', function () { announce(); });
    //  برگشتن به صفحه: کاربر منتظر نماند
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden && acc.isSignedIn()) pump();
    });

    acc.on('signed-out', function () { disconnectLive(); forgetCounts(); announce(); });

    if (acc.isSignedIn()) {
      firstSync().catch(function () { }).then(function () { pump(); connectLive(); });
      acc.heartbeat().catch(function () { });
    } else {
      announce();
    }
  }

  /** پس از ورودِ تازه صدا زده می‌شود. */
  function onSignedIn() {
    attempt = 0;
    //  کلیدهای این حساب فرق دارند، پس شمارشِ کش‌شده هم بی‌اعتبار است
    forgetCounts();
    patchState({ lastError: '' });
    firstSync().catch(function () { }).then(function () { pump(); connectLive(); });
    acc.heartbeat().catch(function () { });
  }

  window.TohidSync = {
    bind: bind,
    start: start,
    onSignedIn: onSignedIn,
    recordChange: recordChange,
    pushBaseline: pushBaseline,
    sync: pump,
    pullOnce: pullOnce,
    restoreFromServer: restoreFromServer,
    firstSync: firstSync,
    status: status,
    onChange: onChange,
    queueSize: function () { return queueCounts().queued; },
    dropped: function () { return queue().dropped(); },
    clearError: function () { patchState({ lastError: '' }); announce(); },
    connectLive: connectLive,
    /** پیامِ کوچک به دستگاه‌های دیگرِ همین حساب — `false` یعنی سوکت باز نیست */
    relay: function (obj) {
      if (!socket || !socketOpen) return false;
      try { socket.send(JSON.stringify(obj)); return true; } catch (e) { return false; }
    },
    get liveOpen() { return socketOpen; },
    disconnectLive: disconnectLive,
    //  فقط برای سنجه‌های مرورگری — هیچ منطقی به آن تکیه ندارد
    _state: state,
  };
})();
