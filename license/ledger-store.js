/* ==========================================================
   فروشگاه من | جای دفتر در مرورگر: localStorage یا IndexedDB
   ----------------------------------------------------------
   F0-23 (دنباله). مشخصات ۳: «دادهٔ وب: IndexedDB با durability
   سخت‌گیرانه، با wrapperِ کوچکِ داخلِ ریپو (بی CDN)». ۱.۳ بند ۷
   (قاعدهٔ آیفون): «انتقال به IndexedDB فقط پس از آزمون روی آیفونِ
   واقعیِ صاحب محصول».

   ⛔ پیش‌فرض خاموش. تا کلیدِ `tohid-ledger-store-v1` برابرِ `idb`
   نباشد این فایل هیچ چیزی را عوض نمی‌کند: نه IndexedDB باز می‌کند،
   نه به localStorage دست می‌زند. فقط `TohidLedgerStore` را می‌سازد تا
   «تنظیمات» بتواند روشنش کند.

   روشن که شد:
   • دفتر و هر کلیدی که باید با آن یک‌جا بماند (صندوقچهٔ حساب‌ها، صاحبِ
     دفتر، سایه و صف و وضعِ همگام‌سازی) در IndexedDB می‌نشیند — همه در
     **یک تراکنش**. اگر دفتر در IndexedDB و سایه در localStorage بود،
     بستنِ برنامه پیش از پایانِ نوشتن دفتر را از سایه عقب می‌انداخت و
     تفاضلِ بعدی ردیفِ تازه را «حذف‌شده» به سرور می‌فرستاد.
   • کدهای موجود عوض نمی‌شوند: `localStorage.getItem/setItem/removeItem`
     برای همین کلیدها از حافظهٔ صفحه خوانده و به IndexedDB نوشته می‌شوند.
   • بالا آمدن: خواندنِ IndexedDB ناهمگام است. تا خوانده نشده، این کلیدها
     «نیستند» (getItem ⇒ null) و نوشتنشان خطا می‌دهد؛ پرده‌ای هم روی صفحه
     است تا کسی روی دفترِ خالی کار نکند. `index.html` و `shop-sync.js`
     تا `ready` صبر می‌کنند.
   • انتقال: رونوشت ⇒ خواندنِ دوباره و مقایسهٔ نویسه‌به‌نویسه ⇒ در **یک
     نوبت** نشانه و پاک کردنِ اصل. نسخهٔ پیش از انتقال با پیشوندِ
     `backup:` در IndexedDB می‌ماند. برگشت (خاموش کردن) همان راه وارونه
     است و IndexedDB را پاک نمی‌کند.
   • نبودِ IndexedDB (حالتِ ناشناس، Safariِ کهنه، بسته‌بودن): روشن نمی‌شود
     و دفتر همان‌جا در localStorage می‌ماند.

   ⚠️ جای دفتر را فقط با یک تبِ باز عوض کنید. تبِ دیگر با رویدادِ
   `storage` دوباره بار می‌شود، ولی نوشتنی که درست در همان لحظه در آن
   تب بوده ممکن است جا بماند.
   ========================================================== */
(function () {
  'use strict';

  var MODE_KEY = 'tohid-ledger-store-v1';     // 'idb' یعنی دفتر در IndexedDB است؛ نبودن یعنی localStorage
  var DB_NAME = 'tohid-ledger';
  var STORE = 'kv';
  var META = 'meta';
  var BACKUP = 'backup:';                       // نسخهٔ پیش از انتقال
  var ORPHAN = 'orphan:';                       // کلیدِ جامانده در localStorage بعد از انتقال
  var FALLBACK_QUOTA = 50 * 1024 * 1024;        // مرورگری که `storage.estimate` ندارد
  var OPEN_TIMEOUT_MS = 6000;

  /* کلیدهایی که با دفتر یک‌جا می‌روند — کمتر از این یعنی ناسازگاری روی دیسک */
  var ROUTED_EXACT = [
    'tohid-shop-data-v1',          // خودِ دفتر
    'tohid-ledger-owner-v1',       // دفتر مالِ کدام حساب است (shop-sync.js)
    'tohid-sync-state-v1',         // همگام‌سازیِ قدیمی: بازهٔ شمارهٔ فاکتور
    'tohid-sync-shadow-v1',
  ];
  var ROUTED_PREFIX = [
    'tohid-ledger-vault-',         // دفترِ حسابِ دیگر روی همین مرورگر
    'tohid-sync-v1-',              // Sync v1: سایه، صف، cursor
  ];
  function routed(k) {
    k = String(k);
    if (ROUTED_EXACT.indexOf(k) !== -1) return true;
    for (var i = 0; i < ROUTED_PREFIX.length; i++) if (k.indexOf(ROUTED_PREFIX[i]) === 0) return true;
    return false;
  }

  function named(name, message) {
    var e = new Error(message);
    e.name = name;
    return e;
  }

  var proto = window.Storage && window.Storage.prototype;
  var nat = proto ? { get: proto.getItem, set: proto.setItem, del: proto.removeItem, key: proto.key } : null;
  var local = null;
  try { local = window.localStorage; } catch (e) { local = null; }

  function nget(k) { try { return local ? nat.get.call(local, k) : null; } catch (e) { return null; } }
  function nset(k, v) { nat.set.call(local, k, v); }            // خطای پر بودن را بالا می‌دهد
  function ndel(k) { try { if (local) nat.del.call(local, k); } catch (e) { /* بسته */ } }
  /** کلیدهای دفتر که واقعاً در localStorage نشسته‌اند */
  function nativeRouted() {
    var out = {};
    if (!local) return out;
    try {
      var keys = [];
      for (var i = 0; i < local.length; i++) keys.push(nat.key.call(local, i));
      for (var j = 0; j < keys.length; j++) {
        if (keys[j] == null || !routed(keys[j])) continue;
        var v = nget(keys[j]);
        if (v != null) out[keys[j]] = v;
      }
    } catch (e) { /* بسته */ }
    return out;
  }
  function sameMap(a, b) {
    var ka = Object.keys(a), kb = Object.keys(b);
    if (ka.length !== kb.length) return false;
    for (var i = 0; i < ka.length; i++) if (a[ka[i]] !== b[ka[i]]) return false;
    return true;
  }

  var mode = nget(MODE_KEY) === 'idb' ? 'idb' : 'ls';
  var hydrated = false;
  var failure = null;
  var cache = Object.create(null);
  var dirty = Object.create(null);
  var scheduled = false;
  var inflight = null;
  var lastError = null;
  var estimate = null;
  var errorFns = [];
  var resolveReady;
  var ready = new Promise(function (r) { resolveReady = r; });
  if (mode !== 'idb') resolveReady(true);

  /* ---------- IndexedDB، کوچک و بی وابستگی ---------- */
  function idbFactory() { try { return window.indexedDB || null; } catch (e) { return null; } }

  /* WebKit 14.x: نخستین `indexedDB.open` گاهی هرگز جواب نمی‌دهد تا
     `databases()` صدا زده شود (همان راهِ safari-14-idb-fix). */
  function safariWake() {
    var idb = idbFactory();
    if (!idb || typeof idb.databases !== 'function') return Promise.resolve();
    return new Promise(function (resolve) {
      var done = false, iv = null;
      function finish() { if (done) return; done = true; clearInterval(iv); resolve(); }
      function poke() { try { idb.databases().then(finish, finish); } catch (e) { finish(); } }
      poke();
      iv = setInterval(poke, 100);
      setTimeout(finish, 1500);
    });
  }

  var dbp = null;
  function openDb() {
    if (dbp) return dbp;
    dbp = safariWake().then(function () {
      return new Promise(function (resolve, reject) {
        var idb = idbFactory();
        if (!idb) { reject(named('NotSupportedError', 'IndexedDB در این مرورگر نیست')); return; }
        var req;
        try { req = idb.open(DB_NAME, 1); } catch (e) { reject(e); return; }
        var timer = setTimeout(function () { reject(named('TimeoutError', 'IndexedDB جواب نداد')); }, OPEN_TIMEOUT_MS);
        req.onupgradeneeded = function () {
          var db = req.result;
          if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
        };
        req.onsuccess = function () {
          clearTimeout(timer);
          var db = req.result;
          db.onversionchange = function () { try { db.close(); } catch (e) { /* بسته */ } dbp = null; };
          db.onclose = function () { dbp = null; };       // Safari: «اتصال قطع شد» — بارِ بعد دوباره باز شود
          resolve(db);
        };
        req.onerror = function () { clearTimeout(timer); reject(req.error || named('UnknownError', 'IndexedDB باز نشد')); };
      });
    });
    dbp.catch(function () { dbp = null; });
    return dbp;
  }

  function txn(db, rw) {
    if (!rw) return db.transaction(STORE, 'readonly');
    try { return db.transaction(STORE, 'readwrite', { durability: 'strict' }); }
    catch (e) { return db.transaction(STORE, 'readwrite'); }
  }

  /** چند نوشتن در یک تراکنش، همه یا هیچ. مقدارِ undefined یعنی پاک کن. */
  function write(entries) {
    return openDb().then(function (db) {
      return new Promise(function (resolve, reject) {
        var t = null;
        try {
          t = txn(db, true);
          var st = t.objectStore(STORE);
          for (var i = 0; i < entries.length; i++) {
            if (entries[i][1] === undefined) st.delete(entries[i][0]);
            else st.put(entries[i][1], entries[i][0]);
          }
        } catch (e) {
          try { if (t) t.abort(); } catch (x) { /* لغو شده */ }
          reject(e);
          return;
        }
        t.oncomplete = function () { resolve(); };
        t.onabort = function () { reject(t.error || named('AbortError', 'نوشتن در IndexedDB لغو شد')); };
      });
    });
  }

  function readAll() {
    return openDb().then(function (db) {
      return new Promise(function (resolve, reject) {
        var out = Object.create(null);
        var t = txn(db, false);
        var req = t.objectStore(STORE).openCursor();
        req.onsuccess = function () {
          var c = req.result;
          if (c) { out[String(c.key)] = c.value; c['continue'](); }
        };
        t.oncomplete = function () { resolve(out); };
        t.onabort = function () { reject(t.error || req.error || named('UnknownError', 'خواندن از IndexedDB نشد')); };
      });
    });
  }

  /* ---------- نوشتن: یک تراکنش در هر نوبت، پشتِ سرِ هم ---------- */
  function hasDirty() { for (var k in dirty) return true; return false; }

  function emitError(e) {
    for (var i = 0; i < errorFns.length; i++) {
      try { errorFns[i](e); } catch (x) { /* شنوندهٔ شکسته بقیه را نمی‌برد */ }
    }
  }

  /* پس از پایانِ نوبتِ جاری: `saveState` دفتر و سایه و صف را در یک نوبت
     می‌نویسد و هر سه باید با هم در یک تراکنش بروند. */
  function schedule() {
    if (scheduled) return;
    scheduled = true;
    Promise.resolve().then(function () { scheduled = false; pump(); });
  }

  function pump() {
    if (inflight || !hasDirty()) return inflight;
    var keys = Object.keys(dirty);
    dirty = Object.create(null);
    var entries = keys.map(function (k) { return [k, k in cache ? cache[k] : undefined]; });
    inflight = write(entries).then(function () {
      lastError = null;
    }, function (e) {
      //  ⛔ گم نمی‌شود: همین کلیدها دوباره «نانوشته»اند و نوشتنِ بعدی
      //  همه را با هم می‌برد. تا آن موقع خودِ صفحه نسخهٔ درست را دارد.
      for (var i = 0; i < keys.length; i++) dirty[keys[i]] = true;
      lastError = e;
      emitError(e);
    }).then(function () {
      inflight = null;
      if (!lastError && hasDirty()) pump();
      refreshUsage();
    });
    return inflight;
  }

  /** تا هر چه تا این لحظه نوشته شده روی دیسک بنشیند */
  function flush() {
    if (mode !== 'idb' || !hydrated) return Promise.resolve();
    return new Promise(function (resolve, reject) {
      var tries = 0;
      (function step() {
        if (inflight) { inflight.then(step); return; }
        if (!hasDirty()) { resolve(); return; }
        if (++tries > 3) { reject(lastError || named('UnknownError', 'در IndexedDB نوشته نشد')); return; }
        pump();
        if (inflight) inflight.then(step); else resolve();
      })();
    });
  }

  /* ---------- localStorage برای کلیدهای دفتر ---------- */
  function notReady() { return named('InvalidStateError', 'دفتر هنوز از IndexedDB خوانده نشده است'); }

  function install() {
    if (!proto || !local || proto.getItem.tohidLedger) return;
    var get = function (k) {
      if (this === local && arguments.length && routed(k)) {
        k = String(k);
        return hydrated && (k in cache) ? cache[k] : null;
      }
      return nat.get.apply(this, arguments);
    };
    var set = function (k, v) {
      if (this === local && arguments.length >= 2 && routed(k)) {
        if (!hydrated) throw notReady();
        k = String(k);
        cache[k] = String(v);
        dirty[k] = true;
        schedule();
        return undefined;
      }
      return nat.set.apply(this, arguments);
    };
    var del = function (k) {
      if (this === local && arguments.length && routed(k)) {
        if (!hydrated) throw notReady();
        k = String(k);
        if (k in cache) { delete cache[k]; dirty[k] = true; schedule(); }
        return undefined;
      }
      return nat.del.apply(this, arguments);
    };
    get.tohidLedger = set.tohidLedger = del.tohidLedger = true;
    proto.getItem = get;
    proto.setItem = set;
    proto.removeItem = del;
  }
  function uninstall() {
    if (!proto) return;
    proto.getItem = nat.get;
    proto.setItem = nat.set;
    proto.removeItem = nat.del;
  }

  /* ---------- اندازه — برای هشدارِ F0-23 ---------- */
  function usage() {
    if (mode !== 'idb') return null;
    var chars = 0;
    for (var k in cache) chars += k.length + cache[k].length;
    var quota = estimate && estimate.quota > 0 ? estimate.quota : FALLBACK_QUOTA;
    var used = Math.max(chars * 2, estimate ? estimate.usage : 0);
    return { used: used, quota: quota, ratio: used / quota, estimated: !!estimate };
  }
  function refreshUsage() {
    try {
      if (mode === 'idb' && navigator.storage && navigator.storage.estimate) {
        return navigator.storage.estimate().then(function (e) {
          estimate = e && e.quota > 0 ? { usage: Number(e.usage) || 0, quota: Number(e.quota) } : null;
          return usage();
        }, function () { return usage(); });
      }
    } catch (e) { /* قدیمی */ }
    return Promise.resolve(usage());
  }

  /* ---------- پرده: تا دفتر خوانده نشده، کسی روی دفترِ خالی کار نکند ---------- */
  var gate = null;
  function showGate(failed) {
    if (!document.body) {
      document.addEventListener('DOMContentLoaded', function () { showGate(failed); }, { once: true });
      return;
    }
    if (hydrated) return;
    if (!gate) {
      gate = document.createElement('div');
      gate.id = 'ledger-gate';
      gate.style.cssText = 'position:fixed;inset:0;z-index:2147483000;display:flex;flex-direction:column;'
        + 'align-items:center;justify-content:center;gap:14px;padding:24px;text-align:center;direction:rtl;'
        + 'background:var(--bg,#f2f0fa);color:var(--text,#191627);font:15px/1.9 Vazirmatn,Tahoma,sans-serif;'
        + 'opacity:0;transition:opacity .2s .25s;';
      document.body.appendChild(gate);
      setTimeout(function () { if (gate) gate.style.opacity = '1'; }, 0);
    }
    gate.textContent = '';
    gate.setAttribute('role', failed ? 'alertdialog' : 'status');
    gate.setAttribute('aria-live', 'assertive');
    var p = document.createElement('p');
    p.style.cssText = 'margin:0;max-width:420px;';
    p.textContent = failed
      ? 'دفترِ این دستگاه از حافظهٔ مرورگر (IndexedDB) باز نشد. چیزی پاک نشده و چیزی هم نوشته نمی‌شود تا دوباره باز شود. صفحه را دوباره باز کنید؛ اگر باز هم نشد، با پشتیبانی تماس بگیرید.'
      : 'در حالِ باز کردنِ دفتر…';
    gate.appendChild(p);
    if (failed) {
      gate.style.opacity = '1';
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'btn btn-primary';
      b.textContent = 'دوباره امتحان کنید';
      b.addEventListener('click', function () { location.reload(); });
      gate.appendChild(b);
    }
  }
  function hideGate() {
    if (gate && gate.parentNode) gate.parentNode.removeChild(gate);
    gate = null;
  }

  /* ---------- بالا آمدن وقتی دفتر در IndexedDB است ---------- */
  function hydrate() {
    install();      // پیش از خوانده شدن هم کسی به جای خالیِ localStorage ننویسد
    readAll().then(function (all) {
      //  نشانه می‌گوید IndexedDB، ولی پایگاه خالی است (پاک شده؟): دفترِ خالی
      //  نشان نمی‌دهیم، وگرنه همگام‌سازی همه‌چیز را «حذف‌شده» می‌فرستاد
      if (!all[META]) throw named('NotFoundError', 'دفترِ IndexedDB پیدا نشد');
      for (var k in all) if (routed(k) && typeof all[k] === 'string') cache[k] = all[k];
      //  کپیِ جامانده در localStorage (برق رفت وسطِ انتقال، یا تبِ دیگر):
      //  اول در IndexedDB کنار گذاشته می‌شود، بعد پاک — هرگز بی‌نشان
      var stray = nativeRouted();
      var keys = Object.keys(stray);
      if (keys.length) {
        var at = Date.now();
        write(keys.map(function (s) { return [ORPHAN + at + ':' + s, stray[s]]; }))
          .then(function () { keys.forEach(ndel); }, function () { /* همان‌جا بماند */ });
      }
      hydrated = true;
      hideGate();
      resolveReady(true);
      refreshUsage();
    }).catch(function (e) {
      failure = e;
      try { console.error('[ledger-store] دفتر از IndexedDB خوانده نشد:', e); } catch (x) { /* بی کنسول */ }
      showGate(true);
    });
  }

  /* ---------- روشن کردن: localStorage ⇒ IndexedDB ---------- */
  function probe() {
    return write([['probe', Date.now()]]).then(function () { return write([['probe', undefined]]); });
  }

  function switchToIdb(snap, keys) {
    //  نشانه اول، پاک کردن بعد: اگر وسطش برق رفت، بارِ بعد از IndexedDB
    //  خوانده می‌شود و کپیِ localStorage «جامانده» است (بالا را ببین).
    try { nset(MODE_KEY, 'idb'); }
    catch (e) {
      //  localStorage لبالب است: جای نشانه را با برداشتنِ بزرگ‌ترین کلید باز
      //  کن — رونوشتِ تأییدشده‌اش همین حالا در IndexedDB است
      var big = keys.slice().sort(function (a, b) { return snap[b].length - snap[a].length; })[0];
      if (!big) throw e;
      ndel(big);
      try { nset(MODE_KEY, 'idb'); }
      catch (e2) { try { nset(big, snap[big]); } catch (e3) { /* همان جایی است که تازه خالی شد */ } throw e2; }
    }
    cache = Object.create(null);
    var chars = 0;
    for (var i = 0; i < keys.length; i++) { cache[keys[i]] = snap[keys[i]]; chars += snap[keys[i]].length; }
    dirty = Object.create(null);
    mode = 'idb';
    hydrated = true;
    install();
    keys.forEach(ndel);
    resolveReady(true);
    refreshUsage();
    return { ok: true, keys: keys.length, chars: chars };
  }

  function enable() {
    if (mode === 'idb') return Promise.resolve({ ok: true, already: true });
    if (!proto || !local) return Promise.reject(named('NotSupportedError', 'حافظهٔ مرورگر بسته است'));
    var attempt = 0;
    function once() {
      attempt++;
      return readAll().then(function (old) {
        var snap = nativeRouted();
        var keys = Object.keys(snap);
        var at = Date.now();
        var entries = [];
        //  کلیدِ زنده‌ای از دورهٔ قبلیِ IndexedDB که امروز در localStorage
        //  نیست: کنار گذاشته می‌شود، پاک نمی‌شود
        for (var k in old) {
          if (routed(k) && !(k in snap)) {
            entries.push([BACKUP + k, { at: at, from: 'indexeddb', value: old[k] }]);
            entries.push([k, undefined]);
          }
        }
        for (var i = 0; i < keys.length; i++) {
          entries.push([keys[i], snap[keys[i]]]);
          entries.push([BACKUP + keys[i], { at: at, from: 'localStorage', value: snap[keys[i]] }]);
        }
        entries.push([META, { v: 1, migratedAt: at, keys: keys }]);
        return write(entries).then(readAll).then(function (back) {
          for (var j = 0; j < keys.length; j++) {
            if (back[keys[j]] !== snap[keys[j]]) throw named('DataError', 'رونوشت با اصل یکی نبود: ' + keys[j]);
          }
          //  ⛔ از این‌جا تا پایان همه در همین یک نوبت است و هیچ نوشتنی وسطش
          //  نمی‌آید. اگر در فاصلهٔ رونوشت چیزی عوض شده بود، از نو.
          if (!sameMap(nativeRouted(), snap)) {
            if (attempt >= 3) throw named('AbortError', 'دفتر وسطِ انتقال عوض شد');
            return once();
          }
          return switchToIdb(snap, keys);
        });
      });
    }
    return probe().then(once);
  }

  /* ---------- خاموش کردن: IndexedDB ⇒ localStorage ---------- */
  function disable() {
    if (mode !== 'idb') return Promise.resolve({ ok: true, already: true });
    if (!hydrated) return Promise.reject(notReady());
    return flush().then(function () {
      //  ⛔ یک نوبت: اول همه در localStorage، بعد سنجش، بعد نشانه
      var keys = Object.keys(cache), done = [];
      try {
        for (var i = 0; i < keys.length; i++) { nset(keys[i], cache[keys[i]]); done.push(keys[i]); }
        for (var j = 0; j < keys.length; j++) {
          if (nget(keys[j]) !== cache[keys[j]]) throw named('DataError', 'رونوشت با اصل یکی نبود: ' + keys[j]);
        }
      } catch (e) {
        done.forEach(ndel);           // جا نشد ⇒ دفتر همان‌جا در IndexedDB می‌ماند
        throw e;
      }
      ndel(MODE_KEY);
      uninstall();
      mode = 'ls';
      //  IndexedDB پاک نمی‌شود: همان نسخه پشتیبان می‌ماند
      write([[META, { v: 1, disabledAt: Date.now(), keys: keys }]]).catch(function () { /* مهم نیست */ });
      return { ok: true, keys: keys.length };
    });
  }

  /* ---------- راه‌اندازی ---------- */
  if (mode === 'idb') {
    hydrate();
    document.addEventListener('DOMContentLoaded', function () { if (!hydrated && !failure) showGate(false); }, { once: true });
  }
  //  گوشی برنامه را پیش از پایانِ نوشتن می‌بندد: تا می‌شود زودتر بنویس
  window.addEventListener('pagehide', function () { if (mode === 'idb' && hydrated) pump(); });
  document.addEventListener('visibilitychange', function () { if (document.hidden && mode === 'idb' && hydrated) pump(); });
  //  تبِ دیگر جای دفتر را عوض کرد: این تب با جای کهنه ادامه ندهد
  window.addEventListener('storage', function (e) {
    if (!e || e.key !== MODE_KEY) return;
    if ((e.newValue === 'idb') === (mode === 'idb')) return;
    var go = function () { location.reload(); };
    flush().then(go, go);
  });

  window.TohidLedgerStore = {
    MODE_KEY: MODE_KEY,
    DB_NAME: DB_NAME,
    mode: function () { return mode; },
    isReady: function () { return mode !== 'idb' || hydrated; },
    ready: ready,
    failure: function () { return failure; },
    routed: routed,
    enable: enable,
    disable: disable,
    flush: flush,
    usage: usage,
    refreshUsage: refreshUsage,
    lastError: function () { return lastError; },
    onError: function (fn) { if (typeof fn === 'function') errorFns.push(fn); },
  };
})();
