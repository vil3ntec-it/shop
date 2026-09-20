/* ==========================================================
   توحید | ورود با کدِ شش‌رقمیِ ایمیلی — سمتِ وب
   ----------------------------------------------------------
   قراردادِ `docs/LOGIN-fa.md`، مو‌به‌مو:

     POST /api/auth/shop/request-code      ⇒ {request_id, resend_after, …}
     GET  /api/auth/shop/request-status    ⇒ {state, reason, can_resend_in}
     POST /api/auth/shop/verify            ⇒ توکن‌ها + user + subscription
     POST /api/auth/refresh                ⇒ جفتِ تازه (چرخشی)

   ── سه قاعده که نباید بشکنند ────────────────────────────────────
   ⛔ **کادرِ نشانیِ سرور این‌جا نیست و نباید بیاید.** نشانی در
      `<meta name="tohid-api-base">` قفل است (`api-config.js`). پرامپت
      «آدرس سرور از Settings با تأیید رمز» می‌خواست؛ قاعدهٔ صاحبِ مخزن
      بالاتر است و شرحش در `docs/SYNC-CLIENT-fa.md`.
   ⛔ **توکن‌ها در همان بستهٔ امروز می‌نشینند** (`tohid-license-v1`).
      کلیدِ تازه یعنی هر مشتریِ امروزی یک‌شبه بیرون می‌افتد و
      `license-client.js` و `shop-sync.js` نشستِ او را پیدا نمی‌کنند.
   ⚠️ **تازه‌سازی یک بار، نه در حلقه.** ۴۰۱ پس از تازه‌سازی یعنی نشست
      واقعاً مرده است؛ حلقه زدن فقط مشکل را پنهان می‌کند.
   ========================================================== */
(function () {
  'use strict';

  var ACCT_KEY = 'tohid-license-v1';      // همان بستهٔ لایهٔ اشتراک
  var DEVICE_KEY = 'tohid-device-uid-v1'; // همان شناسهٔ دستگاهِ امروز
  var HEARTBEAT_KEY = 'tohid-heartbeat-v1';

  var RESEND_S = 60;
  var TIMEOUT_MS = 15000;

  /* ---------- حافظه ---------- */
  function readJson(key, dflt) {
    try { var raw = localStorage.getItem(key); return raw ? (JSON.parse(raw) ?? dflt) : dflt; }
    catch (e) { return dflt; }
  }
  function writeJson(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); return true; } catch (e) { return false; }
  }

  function acct() { return readJson(ACCT_KEY, {}) || {}; }
  function patchAcct(patch) {
    var next = Object.assign(acct(), patch);
    writeJson(ACCT_KEY, next);
    return next;
  }

  /** شناسهٔ پایدارِ این مرورگر — همان که لایهٔ امروز هم می‌سازد. */
  function deviceId() {
    var u = '';
    try { u = localStorage.getItem(DEVICE_KEY) || ''; } catch (e) { u = ''; }
    if (!/^[A-Za-z0-9_-]{8,128}$/.test(u)) {
      var b = new Uint8Array(16);
      (self.crypto || window.crypto).getRandomValues(b);
      u = Array.prototype.map.call(b, function (x) { return ('0' + x.toString(16)).slice(-2); }).join('');
      try { localStorage.setItem(DEVICE_KEY, u); } catch (e) { /* حافظه بسته است */ }
    }
    return u;
  }

  function cfg() { return window.TohidApiConfig || null; }
  function base() { var c = cfg(); return c ? c.baseUrl() : ''; }
  function appId() { var c = cfg(); return c && c.appId ? c.appId() : 'shop'; }
  function appVersion() { var c = cfg(); return c && c.appVersion ? c.appVersion() : ''; }
  function configured() { return !!base(); }

  /** نامِ این دستگاه، برای میزِ «دستگاه‌های من» — بی هیچ چیزِ شناسایی‌کننده. */
  function deviceName() {
    var ua = String(navigator.userAgent || '');
    if (/iPhone|iPad|iPod/i.test(ua)) return 'آیفون — مرورگر';
    if (/Android/i.test(ua)) return 'اندروید — مرورگر';
    if (/Windows/i.test(ua)) return 'ویندوز — مرورگر';
    if (/Mac OS/i.test(ua)) return 'مک — مرورگر';
    return 'نسخهٔ وب';
  }

  /* ---------- خطای آدمیزاد ---------- */
  function ApiError(message, code, status, extra) {
    var e = new Error(message || 'خطای نامشخص');
    e.code = code || '';
    e.status = status || 0;
    if (extra) Object.assign(e, extra);
    return e;
  }

  /**
   *  یک درخواست به سرورِ حساب.
   *
   *  ⚠️ هر چهار سرآیندِ قرارداد می‌روند (`X-App`، `X-Device`،
   *  `X-App-Version`، `X-Request-Id`). بی `X-App`، سرور نشست را به
   *  بخشِ پیش‌فرض مهر می‌زند — همان باگی که یک بار اپِ کارمندانِ پمپ
   *  را کاملاً مرده کرد.
   */
  async function call(path, opts) {
    var o = opts || {};
    if (!configured()) throw ApiError('نشانی سرور تنظیم نشده است', 'no_server');
    var headers = {
      'X-App': appId(),
      'X-App-Id': appId(),
      'X-Device': deviceId(),
      'X-Request-Id': requestStamp(),
    };
    if (appVersion()) headers['X-App-Version'] = appVersion();
    if (o.body !== undefined) headers['Content-Type'] = 'application/json';
    if (o.token) headers.Authorization = 'Bearer ' + o.token;
    if (o.headers) Object.assign(headers, o.headers);

    var ctrl = new AbortController();
    var timer = setTimeout(function () { ctrl.abort(); }, o.timeout || TIMEOUT_MS);
    var res;
    try {
      res = await fetch(base() + path, {
        method: o.method || 'GET',
        headers: headers,
        signal: ctrl.signal,
        body: o.body === undefined ? undefined : JSON.stringify(o.body),
      });
    } catch (e) {
      throw ApiError(e && e.name === 'AbortError' ? 'سرور پاسخ نداد' : 'به سرور نرسیدیم', 'network');
    } finally { clearTimeout(timer); }

    var data = null;
    var text = await res.text().catch(function () { return ''; });
    if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }

    if (!res.ok) {
      //  دو شکلِ خطا روی این سرور هست: شکلِ قراردادِ ورود
      //  (`{error, message}`) و شکلِ قدیمیِ `{error:{code,message}}`.
      var code = '', msg = '';
      if (data && typeof data.error === 'string') { code = data.error; msg = data.message || ''; }
      else if (data && data.error) { code = data.error.code || ''; msg = data.error.message || ''; }
      var err = ApiError(msg || ('خطای سرور (' + res.status + ')'), code || ('http_' + res.status), res.status, {
        retryAfter: Number((data && (data.retry_after || data.retryAfter)) || 0) || 0,
        attemptsLeft: data && data.attempts_left !== undefined ? Number(data.attempts_left) : null,
        body: data,
      });
      throw err;
    }
    return data || {};
  }

  var stampSeq = 0;
  function requestStamp() {
    stampSeq += 1;
    return (Date.now().toString(36) + '-' + stampSeq).slice(0, 64);
  }

  /* ==========================================================
     رقم‌ها — فارسی و عربی هر دو به انگلیسی
     ----------------------------------------------------------
     صفحه‌کلیدِ گوشیِ کاربر فارسی است و کدِ ایمیل انگلیسی. بی این، کدِ
     درست هم «کد اشتباه است» می‌گرفت.
     ========================================================== */
  var FA = '۰۱۲۳۴۵۶۷۸۹';
  var AR = '٠١٢٣٤٥٦٧٨٩';
  function toEnglishDigits(s) {
    var out = '';
    var str = String(s == null ? '' : s);
    for (var i = 0; i < str.length; i++) {
      var ch = str[i];
      var f = FA.indexOf(ch);
      var a = AR.indexOf(ch);
      out += f >= 0 ? String(f) : (a >= 0 ? String(a) : ch);
    }
    return out;
  }
  function onlyDigits(s) { return toEnglishDigits(s).replace(/[^0-9]/g, ''); }

  /* ==========================================================
     سه پلهٔ ورود
     ========================================================== */

  async function requestCode(email) {
    var r = await call('/api/auth/' + encodeURIComponent(appId()) + '/request-code', {
      method: 'POST',
      body: { email: String(email || '').trim(), device_name: deviceName() },
    });
    return {
      requestId: r.request_id || '',
      expiresIn: Number(r.expires_in || 300),
      resendAfter: Number(r.resend_after || RESEND_S),
      maskedEmail: r.masked_email || '',
    };
  }

  async function requestStatus(requestId) {
    var r = await call('/api/auth/' + encodeURIComponent(appId())
      + '/request-status?request_id=' + encodeURIComponent(requestId));
    return {
      state: r.state || 'queued',
      reason: r.reason || '',
      attempts: Number(r.attempts || 0),
      canResendIn: Number(r.can_resend_in || 0),
      expiresIn: Number(r.expires_in || 0),
    };
  }

  async function verify(requestId, code) {
    var r = await call('/api/auth/' + encodeURIComponent(appId()) + '/verify', {
      method: 'POST',
      body: {
        request_id: String(requestId || ''),
        code: onlyDigits(code),
        device_id: deviceId(),
        device_name: deviceName(),
      },
    });
    var token = r.access_token || r.accessToken || '';
    if (!token) throw ApiError('سرور توکن نداد', 'no_token', 0);
    patchAcct({
      accessToken: token,
      accessExpiresAt: r.accessExpiresAt || (Date.now() + (Number(r.access_expires_in || 3600) * 1000)),
      refreshToken: r.refresh_token || r.refreshToken || '',
      userId: (r.user && r.user.id) || '',
      userLabel: (r.user && (r.user.name || r.user.email)) || '',
      userEmail: (r.user && r.user.email) || '',
      signedInAt: Date.now(),
    });
    if (r.subscription) writeJson(HEARTBEAT_KEY, { subscription: r.subscription, at: Date.now() });
    return r;
  }

  /* ==========================================================
     توکن — تازه‌سازیِ خودکار
     ----------------------------------------------------------
     ⚠️ یک تازه‌سازیِ هم‌زمان، نه چند تا: چند درخواست که با هم ۴۰۱
     بگیرند، هر کدام یک بار زنجیره را می‌چرخانند و آخری‌ها به توکنِ
     باطل می‌رسند. سرور پنجرهٔ ارفاقِ سی‌ثانیه‌ای دارد ولی تکیه به آن
     پوشاندنِ باگ است.
     ========================================================== */
  var refreshing = null;

  async function refresh() {
    if (refreshing) return refreshing;
    var rt = acct().refreshToken;
    if (!rt) throw ApiError('نشست شما منقضی شده است، دوباره وارد شوید', 'no_refresh', 401);
    refreshing = (async function () {
      try {
        var r = await call('/api/auth/refresh', { method: 'POST', body: { refreshToken: rt } });
        patchAcct({
          accessToken: r.accessToken || r.access_token || '',
          accessExpiresAt: r.accessExpiresAt || (Date.now() + (Number(r.access_expires_in || 3600) * 1000)),
          refreshToken: r.refreshToken || r.refresh_token || rt,
        });
        return acct().accessToken;
      } finally { refreshing = null; }
    })();
    return refreshing;
  }

  /** توکنِ دسترسیِ سالم — پیش از انقضا خودش تازه می‌کند. */
  async function accessToken() {
    var a = acct();
    if (!a.accessToken) return '';
    var exp = Number(a.accessExpiresAt || 0);
    //  شصت ثانیه پیش از انقضا، نه بعدش: درخواستی که وسطِ راه منقضی
    //  شود یک رفت‌وبرگشتِ اضافه می‌خواهد
    if (exp && exp - Date.now() < 60000 && a.refreshToken) {
      try { return await refresh(); } catch (e) { return a.accessToken; }
    }
    return a.accessToken;
  }

  /**
   *  درخواست با نشستِ کاربر. روی ۴۰۱ **یک بار** تازه می‌کند و یک بار
   *  دوباره می‌زند؛ باز ۴۰۱ ⇒ نشست مرده است و پاک می‌شود.
   */
  async function authFetch(path, opts) {
    var token = await accessToken();
    if (!token) throw ApiError('برای این کار باید وارد حساب شوید', 'no_session', 401);
    try {
      return await call(path, Object.assign({}, opts, { token: token }));
    } catch (e) {
      if (e.status !== 401) throw e;
      var fresh = '';
      try { fresh = await refresh(); } catch (e2) { signOutLocal(); throw e; }
      if (!fresh) { signOutLocal(); throw e; }
      try {
        return await call(path, Object.assign({}, opts, { token: fresh }));
      } catch (e3) {
        if (e3.status === 401) signOutLocal();
        throw e3;
      }
    }
  }

  function isSignedIn() { return !!acct().accessToken; }

  function signOutLocal() {
    patchAcct({ accessToken: '', refreshToken: '', accessExpiresAt: 0 });
    emit('signed-out', {});
  }

  async function signOut() {
    var a = acct();
    //  ⚠️ نشدنِ باطل‌سازیِ سمتِ سرور نباید جلوی خروجِ محلی را بگیرد؛
    //  کاربری که «خارج شو» زده باید خارج شود، حتی بی اینترنت.
    if (a.refreshToken) {
      try {
        await call('/api/auth/logout', {
          method: 'POST', token: a.accessToken, body: { refreshToken: a.refreshToken },
        });
      } catch (e) { /* بی‌اینترنت هم خروج خروج است */ }
    }
    signOutLocal();
  }

  /* ==========================================================
     تپش — اشتراک، اعلان‌ها و نسخهٔ تازه، در یک درخواست
     ----------------------------------------------------------
     نتیجه کش می‌شود و «اشتراکِ من» از همان می‌خواند، پس آن صفحه
     آفلاین هم چیزی برای نشان دادن دارد (بندِ ۲۰.۷).
     ========================================================== */
  async function heartbeat() {
    var q = appVersion() ? '?version=' + encodeURIComponent(appVersion()) : '';
    var r = await authFetch('/api/me/heartbeat' + q);
    writeJson(HEARTBEAT_KEY, Object.assign({}, r, { at: Date.now() }));
    emit('heartbeat', r);
    return r;
  }

  function cachedHeartbeat() { return readJson(HEARTBEAT_KEY, null); }

  /* ---------- رویدادها ---------- */
  var listeners = {};
  function on(name, fn) {
    (listeners[name] = listeners[name] || []).push(fn);
    return function () { off(name, fn); };
  }
  function off(name, fn) {
    var l = listeners[name] || [];
    var i = l.indexOf(fn);
    if (i >= 0) l.splice(i, 1);
  }
  function emit(name, payload) {
    var l = (listeners[name] || []).slice();
    for (var i = 0; i < l.length; i++) {
      try { l[i](payload); } catch (e) { /* شنونده‌ای که می‌شکند بقیه را نمی‌برد */ }
    }
  }

  window.TohidAccount = {
    RESEND_S: RESEND_S,
    deviceId: deviceId,
    deviceName: deviceName,
    configured: configured,
    appId: appId,
    appVersion: appVersion,
    toEnglishDigits: toEnglishDigits,
    onlyDigits: onlyDigits,
    requestCode: requestCode,
    requestStatus: requestStatus,
    verify: verify,
    refresh: refresh,
    accessToken: accessToken,
    authFetch: authFetch,
    call: call,
    isSignedIn: isSignedIn,
    signOut: signOut,
    signOutLocal: signOutLocal,
    heartbeat: heartbeat,
    cachedHeartbeat: cachedHeartbeat,
    account: acct,
    on: on, off: off, emit: emit,
  };
})();
