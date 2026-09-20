/* ==========================================================
   توحید | موتور Sync v1 — سمتِ وب
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
   ========================================================== */
(function () {
  'use strict';

  var core = window.TohidSyncCore;
  var acc = window.TohidAccount;
  if (!core || !acc) return;

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
  function setShadow(data) { writeJson(keyOf('shadow'), core.shadowOf(data)); }
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
    var q = queue();
    return {
      configured: acc.configured(),
      signedIn: acc.isSignedIn(),
      online: navigator.onLine !== false,
      busy: busy,
      queued: q.size(),
      dropped: q.dropped().length,
      cursor: Number(st.cursor || 0),
      lastOkAt: Number(st.lastOkAt || 0),
      lastPushAt: Number(st.lastPushAt || 0),
      lastPullAt: Number(st.lastPullAt || 0),
      lastError: st.lastError || '',
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
        queued: q.size(), busy: busy,
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
    queue().push(out.ops);
    setShadow(data);
    announce();
    schedulePush(reason);
    return out.ops.length;
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
    var data = ledger.read();
    if (!data) return 0;
    var out = core.diff({}, data);
    if (out.ops.length) queue().push(out.ops);
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
      schema_version: core.SCHEMA_VERSION,
      ops: batch,
      queued: q.size(),
    };
    var r;
    try {
      r = await acc.authFetch('/api/sync/v1/push', { method: 'POST', body: body });
    } catch (e) {
      if (e.status === 426) {
        //  سرور عقب‌تر است. هیچ opی اعمال نشده و هیچ opی هم پاک
        //  نمی‌شود؛ منتظر می‌مانیم تا سرور به‌روز شود.
        patchState({ holding: true, lastError: e.message || 'سرور باید به‌روز شود' });
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
    if (ok.length) q.ack(ok);
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
    }
    patchState({ lastPushAt: Date.now() });
    return { sent: batch.length, applied: ok.length, rejected: bad.length, head: Number(r.cursor || r.head || 0) };
  }

  /* ==========================================================
     ۳) Pull — تغییرهای دستگاه‌های دیگر
     ========================================================== */
  async function pullOnce() {
    var st = state();
    var since = Number(st.cursor || 0);
    var r = await acc.authFetch('/api/sync/v1/pull?device_id='
      + encodeURIComponent(acc.deviceId()) + '&since=' + since);
    var ops = Array.isArray(r.ops) ? r.ops : [];
    if (ops.length) {
      var data = ledger.read();
      if (data) {
        var out = core.applyRemote(data, ops);
        ledger.write(out.data);
        //  سایه همین‌جا جلو می‌رود، وگرنه تفاضلِ بعدی همین‌ها را
        //  دوباره به سرور پس می‌فرستد
        setShadow(out.data);
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
    if (!acc.configured() || !acc.isSignedIn()) { announce(); return; }
    if (navigator.onLine === false) { announce(); return; }
    busy = true;
    announce();
    try {
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
    var r = await acc.authFetch('/api/sync/v1/snapshot');
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

  async function connectLive() {
    if (socket || !acc.configured() || !acc.isSignedIn()) return;
    var token = await acc.accessToken();
    if (!token) return;
    var url = '';
    try {
      var u = new URL('/api/sync/v1/live', window.TohidApiConfig.baseUrl());
      u.protocol = u.protocol === 'http:' ? 'ws:' : 'wss:';
      u.searchParams.set('token', token);
      u.searchParams.set('device_id', acc.deviceId());
      u.searchParams.set('app', acc.appId());
      url = u.toString();
    } catch (e) { return; }

    try { socket = new WebSocket(url); } catch (e) { socket = null; return; }

    socket.onopen = function () { socketOpen = true; socketAttempt = 0; announce(); };
    socket.onmessage = function (ev) {
      var msg = null;
      try { msg = JSON.parse(ev.data); } catch (e) { return; }
      //  فقط «چیزی عوض شد» می‌آید؛ خودِ داده هرگز از این در نمی‌رود
      if (msg && msg.event === 'changed') pump();
    };
    socket.onclose = function () {
      socketOpen = false;
      socket = null;
      announce();
      //  ⚠️ بی‌پایان تلاش می‌کنیم ولی با فاصلهٔ فزاینده؛ سوکتی که هر
      //  ثانیه دوباره وصل شود، هم باتری می‌خورد هم سرور را می‌کوبد
      if (socketTimer) clearTimeout(socketTimer);
      socketAttempt += 1;
      socketTimer = setTimeout(connectLive, core.backoffMs(socketAttempt));
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
    tickTimer = setInterval(function () { if (queue().size()) pump(); }, PUSH_TICK_MS);
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

    acc.on('signed-out', function () { disconnectLive(); announce(); });

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
    queueSize: function () { return queue().size(); },
    dropped: function () { return queue().dropped(); },
    clearError: function () { patchState({ lastError: '' }); announce(); },
    connectLive: connectLive,
    disconnectLive: disconnectLive,
    //  فقط برای سنجه‌های مرورگری — هیچ منطقی به آن تکیه ندارد
    _state: state,
  };
})();
