/* eslint-disable */
'use strict';
/**
 * vill3n-sdk.js — بخشِ ۱۳.۳ی پرامپت.
 *
 * ── چه چیزی این را لازم کرد ────────────────────────────────────────
 * هر برنامه‌ای که به این سرور وصل می‌شود همان شش کار را از نو می‌نویسد:
 * ورود با کدِ ایمیلی، نگه داشتنِ توکن، تازه کردنش، تپش، خواندنِ اشتراک،
 * اعلان، پشتیبانی و «نسخهٔ تازه آمده؟». نسخهٔ وبِ دکان یک‌جور نوشته بود،
 * `kar/` جورِ دیگر، و هر باگی باید دو بار درست می‌شد. این فایل همان را
 * یک بار می‌نویسد.
 *
 * ── قاعده‌ها ────────────────────────────────────────────────────────
 * ⛔ **هیچ وابستگی‌ای ندارد** — نه npm، نه بسته، نه build. یک فایلِ ساده
 *    که با `<script src="…/sdk/vill3n-sdk.js">` می‌آید.
 * ⛔ **هیچ قیمتی این‌جا نیست.** قیمت و مدت و فهرستِ قابلیت‌ها همیشه از
 *    سرور می‌آیند (`/api/plans`, `/api/portal/heartbeat`).
 * ⛔ **شناسهٔ حساب هیچ‌وقت فرستاده نمی‌شود** — سرور آن را از خودِ توکن
 *    برمی‌دارد. هر چیزی که این‌جا بفرستیم، سرور نادیده می‌گیرد.
 * ⚠️ نشانیِ سرور از `init({base})` می‌آید و پیش‌فرضش همان دامنه‌ای است
 *    که صفحه از آن آمده. برنامه‌های نصبی (که نشانی برایشان قفل است)
 *    همان نشانیِ قفل‌شدهٔ خودشان را می‌دهند.
 *
 * ── نمونهٔ کوتاه ─────────────────────────────────────────────────────
 *   VILL3N.init({ app: 'shop', version: '2.1.0' });
 *   await VILL3N.login('ali@example.com');
 *   await VILL3N.verify('123456');
 *   const s = VILL3N.subscription();   // { daysLeft, color, permanent, label }
 *   VILL3N.onChange(() => render());
 */
(function (root, factory) {
  var api = factory();
  root.VILL3N = api;
  if (typeof module === 'object' && module && module.exports) module.exports = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {

  var DAY = 24 * 60 * 60 * 1000;
  var HEARTBEAT_MS = 15 * 60 * 1000;   // هر ۱۵ دقیقه — خواستهٔ بخشِ ۱۳.۳
  var PREFIX = 'vill3n.';

  /* ==========================================================
     حافظهٔ مرورگر — همیشه پشتِ try
     ----------------------------------------------------------
     در پنجرهٔ ناشناس، با دادهٔ سایت بسته، و در بعضی WebViewها خواندن و
     نوشتن **استثنا پرتاب می‌کند**. SDK نباید با آن بمیرد؛ حافظهٔ
     درون‌حافظه‌ای جانشین می‌شود و برنامه کارش را می‌کند.
     ========================================================== */
  var memory = {};
  function store() {
    try { if (typeof localStorage !== 'undefined' && localStorage) return localStorage; } catch (e) { /* بسته */ }
    return null;
  }
  function read(key) {
    var s = store();
    if (s) { try { var raw = s.getItem(PREFIX + key); return raw === null ? undefined : JSON.parse(raw); } catch (e) { /* خراب */ } }
    return memory[key];
  }
  function write(key, value) {
    memory[key] = value;
    var s = store();
    if (s) { try { s.setItem(PREFIX + key, JSON.stringify(value)); } catch (e) { /* پر یا بسته */ } }
  }
  function drop(key) {
    delete memory[key];
    var s = store();
    if (s) { try { s.removeItem(PREFIX + key); } catch (e) { /* بسته */ } }
  }

  /* ==========================================================
     حالت
     ========================================================== */
  var cfg = { app: 'shop', version: '', base: '', platform: 'web', heartbeatMs: HEARTBEAT_MS, autoHeartbeat: true };
  var state = {
    ready: false, signedIn: false, user: null, tenantId: '',
    subscription: null, entitlement: null, notices: [], unread: 0,
    latest: null, serverTime: 0, lastBeatAt: 0, error: '',
  };
  var listeners = [];
  var timer = null;
  var pendingEmail = '';
  var refreshing = null;

  function emit() {
    var snap = snapshot();
    for (var i = 0; i < listeners.length; i++) {
      try { listeners[i](snap); } catch (e) { /* شنوندهٔ خراب بقیه را نکشد */ }
    }
  }
  function snapshot() {
    return {
      ready: state.ready, signedIn: state.signedIn, user: state.user, app: cfg.app,
      tenantId: state.tenantId, subscription: state.subscription, entitlement: state.entitlement,
      unread: state.unread, latest: state.latest, serverTime: state.serverTime, error: state.error,
    };
  }

  /* ==========================================================
     شبکه
     ========================================================== */
  function url(path) {
    var b = cfg.base || '';
    if (b && b.charAt(b.length - 1) === '/') b = b.slice(0, -1);
    return b + path;
  }

  function tokens() { return read('tokens') || {}; }
  function setTokens(t) {
    if (!t) { drop('tokens'); return; }
    write('tokens', { accessToken: t.accessToken || '', refreshToken: t.refreshToken || '', accessExpiresAt: t.accessExpiresAt || 0 });
  }

  /** یک درخواستِ ساده؛ خطای سرور را با پیامِ فارسیِ خودش بالا می‌دهد. */
  function request(method, path, body, opts) {
    opts = opts || {};
    var headers = { 'Content-Type': 'application/json', 'X-App-Id': cfg.app };
    if (cfg.version) { headers['X-App-Version'] = cfg.version; }
    headers['X-App-Platform'] = cfg.platform;
    if (opts.token) headers.Authorization = 'Bearer ' + opts.token;
    var init = { method: method, headers: headers };
    if (body !== undefined && body !== null) init.body = JSON.stringify(body);
    return fetch(url(path), init).then(function (res) {
      return res.text().then(function (text) {
        var json = null;
        try { json = text ? JSON.parse(text) : null; } catch (e) { json = null; }
        if (!res.ok) {
          var err = new Error((json && json.error && json.error.message) || ('خطای ' + res.status));
          err.status = res.status;
          err.code = (json && json.error && json.error.code) || '';
          throw err;
        }
        return json;
      });
    });
  }

  /**
   * درخواست با توکنِ حساب.
   *
   * ⚠️ روی ۴۰۱ **یک بار** توکن را تازه می‌کند و **یک بار** دوباره می‌زند.
   * حلقه نزنید: ۴۰۱ِ بعد از تازه‌سازی یعنی نشست واقعاً مرده است.
   */
  function authed(method, path, body, retried) {
    var t = tokens();
    if (!t.accessToken) return Promise.reject(new Error('وارد نشده‌اید'));
    return request(method, path, body, { token: t.accessToken }).catch(function (err) {
      if (err.status !== 401 || retried) {
        if (err.status === 401) signOutLocal();
        throw err;
      }
      return refresh().then(function () { return authed(method, path, body, true); });
    });
  }

  function refresh() {
    if (refreshing) return refreshing;
    var t = tokens();
    if (!t.refreshToken) { signOutLocal(); return Promise.reject(new Error('نشست تمام شد')); }
    refreshing = request('POST', '/api/auth/refresh', { refreshToken: t.refreshToken })
      .then(function (out) {
        setTokens({ accessToken: out.accessToken, refreshToken: out.refreshToken || t.refreshToken, accessExpiresAt: out.accessExpiresAt || 0 });
        refreshing = null;
        return out;
      })
      .catch(function (err) { refreshing = null; signOutLocal(); throw err; });
    return refreshing;
  }

  function signOutLocal() {
    setTokens(null);
    state.signedIn = false; state.user = null; state.subscription = null;
    state.entitlement = null; state.tenantId = ''; state.notices = []; state.unread = 0;
    drop('beat');
    emit();
  }

  /** شناسهٔ همین دستگاه — یک بار ساخته می‌شود و می‌ماند. */
  function deviceId() {
    var id = read('device');
    if (!id) {
      id = 'sdk-' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36);
      write('device', id);
    }
    return id;
  }

  /* ==========================================================
     نمای اشتراک — همان چیزی که صفحهٔ «اشتراکِ من» نشان می‌دهد
     ----------------------------------------------------------
     ⛔ رنگ همین‌جا و فقط همین‌جا تصمیم گرفته می‌شود: سبز > ۳۰ روز،
        زرد ≤ ۳۰ (و ≤ ۷ همان زرد با `urgent`)، سرخ منقضی، «دائمی ✓» بی
        شمارش. هر برنامه‌ای که قاعدهٔ خودش را بنویسد، روزی رنگش با
        رنگِ پنل فرق می‌کند.
     ========================================================== */
  function viewOf(sub) {
    if (!sub) return null;
    var out = {
      source: sub.source || '', status: sub.status || 'none', active: !!sub.active,
      plan: sub.plan || '', startsAt: sub.startsAt || 0, endsAt: sub.endsAt || 0,
      permanent: !!sub.permanent,
      daysLeft: sub.permanent ? null : (sub.daysLeft || 0),
      progress: typeof sub.progress === 'number' ? sub.progress : 0,
      color: sub.color || 'red',
      label: sub.label || '',
      features: sub.features || [], addons: sub.addons || [],
      trial: sub.trial || null,
    };
    out.urgent = !out.permanent && out.active && out.daysLeft <= 7;
    out.expired = !out.active && !out.permanent;
    //  ⚠️ فقط وقتی به کار می‌آید که سرور برچسب نداده باشد (نسخهٔ کهنه)؛
    //  رقم فارسی است، مثلِ خودِ سرور.
    if (!out.label) {
      out.label = out.permanent ? 'دائمی ✓'
        : (out.active ? Number(out.daysLeft || 0).toLocaleString('fa-AF') + ' روز مانده' : 'منقضی');
    }
    return out;
  }

  /* ==========================================================
     API
     ========================================================== */

  function init(options) {
    options = options || {};
    cfg.app = options.app || cfg.app;
    cfg.version = options.version || cfg.version;
    cfg.base = options.base !== undefined ? options.base : cfg.base;
    cfg.platform = options.platform || cfg.platform;
    if (options.heartbeatMs) cfg.heartbeatMs = options.heartbeatMs;
    if (options.autoHeartbeat === false) cfg.autoHeartbeat = false;

    //  عکسِ آخرین تپش — تا برنامه پیش از رسیدنِ جوابِ سرور هم چیزی
    //  برای نشان دادن داشته باشد. بی‌اینترنت یعنی «همان که دیروز بود»،
    //  نه صفحهٔ خالی.
    var beat = read('beat');
    if (beat && beat.data) applyBeat(beat.data, true);
    state.signedIn = !!tokens().accessToken;
    state.ready = true;
    emit();

    if (state.signedIn && cfg.autoHeartbeat) {
      startTimer();
      heartbeat().catch(function () { /* آفلاین — عکسِ کهنه سرِ جایش */ });
    }
    return snapshot();
  }

  function startTimer() {
    stopTimer();
    if (typeof setInterval !== 'function') return;
    timer = setInterval(function () { heartbeat().catch(function () {}); }, cfg.heartbeatMs);
    if (timer && typeof timer.unref === 'function') timer.unref();
  }
  function stopTimer() { if (timer) { clearInterval(timer); timer = null; } }

  /** پلهٔ یک: کد به ایمیل برود. */
  function login(email) {
    pendingEmail = String(email || '').trim();
    return request('POST', '/api/auth/otp/request', { email: pendingEmail, app: cfg.app })
      .then(function (out) { return { sent: true, resendSeconds: (out && out.resendSeconds) || 0 }; });
  }

  /** پلهٔ دو: کدِ شش‌رقمی ⇒ نشست. */
  function verify(code, email) {
    var addr = String(email || pendingEmail || '').trim();
    return request('POST', '/api/auth/otp/verify', {
      email: addr, code: String(code || '').trim(), app: cfg.app,
      device: { deviceId: deviceId(), name: cfg.platform, platform: cfg.platform, appVersion: cfg.version },
    }).then(function (out) {
      setTokens(out);
      state.signedIn = true;
      state.user = out.user || null;
      pendingEmail = '';
      emit();
      if (cfg.autoHeartbeat) startTimer();
      return heartbeat().then(function () { return snapshot(); }, function () { return snapshot(); });
    });
  }

  function logout() {
    var t = tokens();
    stopTimer();
    var done = t.refreshToken
      ? request('POST', '/api/auth/logout', { refreshToken: t.refreshToken }, { token: t.accessToken }).catch(function () {})
      : Promise.resolve();
    return done.then(function () { signOutLocal(); return snapshot(); });
  }

  function applyBeat(data, fromCache) {
    state.tenantId = data.tenantId || '';
    state.subscription = viewOf(data.subscription);
    state.entitlement = data.entitlement || null;
    state.unread = data.unreadNotices || 0;
    state.latest = data.latest || null;
    state.serverTime = data.serverTime || 0;
    if (!fromCache) state.lastBeatAt = Date.now();
  }

  /**
   * تپش: اشتراک، شمارِ اعلانِ نخوانده و آخرین نسخه — در یک درخواست.
   *
   * ⚠️ نتیجه در حافظهٔ مرورگر کَش می‌شود؛ `heartbeat({force:true})`
   *    ترمزِ ۱۵ دقیقه‌ای را رد می‌کند (دکمهٔ «تازه‌سازی»ِ خودِ کاربر).
   */
  function heartbeat(opts) {
    opts = opts || {};
    var beat = read('beat');
    if (!opts.force && beat && beat.at && (Date.now() - beat.at) < cfg.heartbeatMs && beat.data) {
      applyBeat(beat.data, true);
      emit();
      return Promise.resolve(beat.data);
    }
    var q = cfg.version ? ('?version=' + encodeURIComponent(cfg.version)) : '';
    return authed('GET', '/api/portal/heartbeat' + q).then(function (data) {
      write('beat', { at: Date.now(), data: data });
      state.signedIn = true;
      state.error = '';
      applyBeat(data, false);
      emit();
      return data;
    }, function (err) {
      //  ⛔ بی‌اینترنت هیچ‌وقت کسی را از حسابش بیرون نمی‌اندازد؛ فقط
      //  ۴۰۱ِ خودِ سرور نشست را می‌کشد (بالاتر، در `authed`).
      state.error = err.message || '';
      emit();
      throw err;
    });
  }

  /** نمای اشتراک — از آخرین تپش، بی درخواستِ تازه. */
  function subscription() { return state.subscription; }
  function entitlement() { return state.entitlement; }
  /** آیا این قابلیت روی این اشتراک باز است؟ */
  function can(feature) {
    var e = state.entitlement;
    if (!e || !e.features) return false;
    return e.features.indexOf(feature) !== -1;
  }

  /** پلن‌ها و قیمت‌ها — ⛔ همیشه از سرور، هیچ عددی در برنامه نیست. */
  function plans() {
    return request('GET', '/api/plans?app=' + encodeURIComponent(cfg.app)).then(function (out) { return out; });
  }

  /** سنجیدنِ کدِ تخفیف پیش از خرید. */
  function redeem(code, plan) {
    return authed('POST', '/api/portal/redeem', { code: code, plan: plan || '', app: cfg.app });
  }

  function notices(opts) {
    opts = opts || {};
    return authed('GET', '/api/portal/notices?limit=' + (opts.limit || 50)).then(function (out) {
      state.notices = out.notices || [];
      state.unread = out.unread || 0;
      emit();
      return out;
    });
  }

  function markRead(id) {
    return authed('POST', '/api/portal/notices/' + encodeURIComponent(id) + '/read').then(function (out) {
      state.unread = Math.max(0, state.unread - 1);
      emit();
      return out;
    });
  }

  /* پشتیبانی — یک رشته برای هر مشتری، همان که پنل می‌بیند. */
  var support = {
    thread: function (opts) {
      opts = opts || {};
      return authed('GET', '/api/portal/support' + (opts.after ? '?after=' + opts.after : ''));
    },
    send: function (body, subject) {
      return authed('POST', '/api/portal/support/messages', { body: body, subject: subject || '' });
    },
  };

  /**
   * گزارشِ خطا. هیچ‌وقت استثنا بیرون نمی‌دهد — گزارشِ خطا نباید خودش
   * برنامه را بشکند. بی نشست، خطا در صف می‌ماند و با ورود می‌رود.
   */
  function reportError(err, context) {
    var payload = {
      message: String((err && err.message) || err || '').slice(0, 1000),
      stack: String((err && err.stack) || '').slice(0, 8000),
      version: cfg.version, platform: cfg.platform, context: context || {},
    };
    if (!tokens().accessToken) {
      var queue = read('errq') || [];
      queue.push(payload);
      write('errq', queue.slice(-20));
      return Promise.resolve({ queued: true });
    }
    return flushErrors().then(function () {
      return authed('POST', '/api/portal/errors', payload).catch(function () { return { ok: false }; });
    });
  }

  function flushErrors() {
    var queue = read('errq') || [];
    if (!queue.length || !tokens().accessToken) return Promise.resolve(0);
    drop('errq');
    var chain = Promise.resolve();
    queue.forEach(function (p) {
      chain = chain.then(function () { return authed('POST', '/api/portal/errors', p).catch(function () {}); });
    });
    return chain.then(function () { return queue.length; });
  }

  /**
   * نسخهٔ تازه آمده؟ مسیرِ `/api/downloads` **باز** است (توکن نمی‌خواهد)،
   * تا برنامه‌ای که هنوز وارد نشده هم بتواند خودش را به‌روز کند.
   */
  function checkUpdate() {
    return request('GET', '/api/downloads?app=' + encodeURIComponent(cfg.app)).then(function (out) {
      var d = (out.downloads || [])[0] || null;
      var latest = d ? (d.version || '') : '';
      return {
        current: cfg.version, latest: latest, url: d ? (d.url || '') : '',
        notes: d ? (d.notes || '') : '', minVersion: d ? (d.minVersion || '') : '',
        hasUpdate: !!(latest && compareVersions(latest, cfg.version) > 0),
        mustUpdate: !!(d && d.minVersion && compareVersions(d.minVersion, cfg.version) > 0),
        apps: out.apps || [],
      };
    });
  }

  /** «۲.۱۰.۰» از «۲.۹.۰» بزرگ‌تر است — مقایسهٔ عددی، نه متنی. */
  function compareVersions(a, b) {
    var x = String(a || '').split('.').map(Number);
    var y = String(b || '').split('.').map(Number);
    for (var i = 0; i < Math.max(x.length, y.length); i++) {
      var p = x[i] || 0, q = y[i] || 0;
      if (isNaN(p)) p = 0; if (isNaN(q)) q = 0;
      if (p !== q) return p > q ? 1 : -1;
    }
    return 0;
  }

  /** Cloud Sync — انتخابِ خودِ مشتری، نه تصمیمِ برنامه. */
  function settings() { return authed('GET', '/api/portal/settings'); }
  function setCloudSync(on) { return authed('PUT', '/api/portal/settings', { app: cfg.app, cloudSync: !!on }); }

  function onChange(cb) {
    if (typeof cb !== 'function') return function () {};
    listeners.push(cb);
    if (state.ready) { try { cb(snapshot()); } catch (e) { /* بی‌اهمیت */ } }
    return function () {
      var i = listeners.indexOf(cb);
      if (i !== -1) listeners.splice(i, 1);
    };
  }

  return {
    version: '1.0.0',
    init: init, login: login, verify: verify, logout: logout,
    heartbeat: heartbeat, subscription: subscription, entitlement: entitlement, can: can,
    plans: plans, redeem: redeem,
    notices: notices, markRead: markRead, support: support,
    reportError: reportError, flushErrors: flushErrors,
    checkUpdate: checkUpdate, compareVersions: compareVersions,
    settings: settings, setCloudSync: setCloudSync,
    onChange: onChange, state: snapshot, config: function () { return cfg; },
    stop: stopTimer, _viewOf: viewOf,
  };
}));
