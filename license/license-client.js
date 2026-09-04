/* ==========================================================
   توحید | مدیریت فروشگاه — لایه اشتراک و License (سمت برنامه)
   ----------------------------------------------------------
   این فایل هیچ بخشی از منطق فروشگاه را تغییر نمی‌دهد. فقط:
     ۱) با سرور خانگی حرف می‌زند (همیشه برنامه آغازکننده است)
     ۲) License امضاشده را ذخیره و با WebCrypto بررسی می‌کند
     ۳) دستکاری ساعت دستگاه را تشخیص می‌دهد
     ۴) قابلیت‌های قفل را روی رابط کاربری می‌پوشاند

   نکته‌ی صادقانه درباره‌ی امنیت:
   این برنامه تماماً سمت کلاینت اجرا می‌شود. امضای دیجیتال جلوی «ساختن
   License تقلبی» را می‌گیرد و کسی نمی‌تواند تاریخ یا قابلیت‌ها را دستکاری
   کند. ولی کسی که با DevTools بلد است کار کند، می‌تواند قفلِ نمایشیِ
   همین فایل را دور بزند. تنها محافظت واقعی برای قابلیت‌های حساس،
   بررسی دوباره در سمت سرور است (requireFeature در API) — که پیاده شده.
   ========================================================== */
(function () {
  'use strict';

  /* ----------------------------------------------------------
     کلید اصلی: اجرای محدودیت اشتراک
     false = برنامه دقیقاً مثل قبل کار می‌کند، هیچ قابلیتی قفل نمی‌شود.
             (لایه اشتراک بارگذاری می‌شود ولی چیزی را نمی‌بندد.)
     true  = قابلیت‌ها بر اساس License امضاشده قفل/باز می‌شوند.

     تا وقتی روی نسخه اندروید کار نکرده‌ای، این را false بگذار.
     ---------------------------------------------------------- */
  const LICENSE_ENFORCED = false;

  // ---------- پیکربندی ----------
  const CFG = {
    // کلید عمومی سرور (خروجی `npm run generate-keys`، قالب base64 SPKI).
    // اگر خالی بماند، کلید در اولین فعال‌سازی از سرور گرفته و ذخیره می‌شود
    // (Trust On First Use). گذاشتن کلید اینجا امن‌تر است.
    PINNED_PUBLIC_KEY: '',

    ISSUER: 'tohid-license-server',
    AUDIENCE: 'tohid-shop-app',

    // آستانه‌ی تشخیص دستکاری ساعت
    CLOCK_BACK_TOLERANCE_MS: 10 * 60 * 1000,             // عقب‌رفتن عادی (اصلاح NTP)
    CLOCK_FORWARD_FLAG_MS: 365 * 24 * 60 * 60 * 1000,    // جهش جلوی آشکارا نامعقول (فقط هشدار)

    AUTO_SYNC_INTERVAL_MS: 6 * 60 * 60 * 1000,      // تلاش خودکار Sync
    REQUEST_TIMEOUT_MS: 15000,
  };

  const STORE_KEY = 'tohid-license-v1';
  const DEVICE_KEY = 'tohid-device-uid-v1';
  const SERVER_KEY = 'tohid-license-server-url';

  // نگاشت قابلیت → صفحه‌های برنامه (فقط برای پوشاندن رابط کاربری)
  const FEATURE_PAGES = {
    sales:      ['sale', 'quick-sale', 'sales'],
    warehouse:  ['warehouse'],
    debtors:    ['debtors', 'debtor-account'],
    expenses:   ['expenses'],
    purchasing: ['purchasing', 'supplier-account'],
    reports:    ['reports'],
    audit_log:  ['audit-log'],
  };
  const FEATURE_LABELS = {
    sales: 'فروش (صندوق)', warehouse: 'انبار', debtors: 'قرض‌داران',
    expenses: 'مصارف', purchasing: 'خرید و تأمین‌کننده', reports: 'گزارشات',
    audit_log: 'سابقه عملیات', barcode: 'اسکنر بارکد',
    backup: 'پشتیبان‌گیری و بازیابی', csv_export: 'خروجی CSV و چاپ گزارش',
  };
  // عناصری که به قابلیت وابسته‌اند ولی صفحه نیستند
  const FEATURE_ELEMENTS = {
    backup:     ['#btn-export-backup', '#btn-import-backup'],
    csv_export: ['#btn-export-report-csv', '#btn-print-report'],
    barcode:    ['#btn-test-camera'],
  };

  // ---------- ابزار ----------
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

  function readStore() {
    try { return JSON.parse(localStorage.getItem(STORE_KEY) || 'null') || {}; }
    catch { return {}; }
  }
  function writeStore(patch) {
    const next = Object.assign(readStore(), patch);
    try { localStorage.setItem(STORE_KEY, JSON.stringify(next)); } catch {}
    return next;
  }
  function clearStore() { try { localStorage.removeItem(STORE_KEY); } catch {} }

  /* بعضی مرورگرها و WebViewها localStorage را می‌بندند (فایل محلی،
     حالت ناشناس، ذخیره‌سازی خاموش). آن‌جا خواندن خطا می‌دهد و اگر
     نگیریمش، این لایه وسط کار می‌ایستد. */
  const lsGet = (k) => { try { return localStorage.getItem(k); } catch { return null; } };
  const lsSet = (k, v) => { try { localStorage.setItem(k, v); } catch {} };
  const lsDel = (k) => { try { localStorage.removeItem(k); } catch {} };

  /* نشانی سرور از یک جا می‌آید: `license/api-config.js`.

     تا دیروز همین منطق چهار بار در چهار فایل تکرار شده بود و هر بار
     کمی فرق داشت. اگر آن فایل به هر دلیل بالا نیامده باشد، به همان
     خواندنِ سادهٔ قبلی برمی‌گردیم تا این لایه وسط کار نایستد. */
  function apiConfig() { return window.TohidApiConfig || null; }

  function getServerUrl() {
    const cfg = apiConfig();
    if (cfg) return cfg.baseUrl();
    return (lsGet(SERVER_KEY) || '').trim().replace(/\/+$/, '');
  }
  function setServerUrl(v) {
    const cfg = apiConfig();
    if (cfg) { cfg.setBaseUrl(v); return; }
    lsSet(SERVER_KEY, String(v || '').trim().replace(/\/+$/, ''));
  }

  /* ==========================================================
     کلیدهای حساب
     ----------------------------------------------------------
     دو کلید متفاوت ساخته می‌شود:

     ۱) کلید حساب (TSH-…) — شناسه‌ی یکتای هر حساب. فروشنده با همین
        می‌فهمد اشتراک را روی کدام حساب فعال کند و سرور خودش هم با
        همین کلید حسابِ طرف را می‌شناسد.

     ۲) کد شاگرد (SHG-…) — صاحب دکان این را به شاگردهایش می‌دهد تا
        در صفحه‌ی ورود بزنند و روی همان دکان بیایند.

     چرا تکراری نمی‌شود:
     هر کلید از ۱۲۸ بیت تصادفیِ crypto.getRandomValues ساخته می‌شود
     (۲ به توان ۱۲۸ حالت). حتی با میلیاردها کلید، احتمال برخورد عملاً
     صفر است. یک بخش زمانی هم اولش می‌آید تا دو دستگاهی که هم‌زمان
     ساخته نمی‌شوند، هرگز به هم نرسند.
     ========================================================== */
  const APIKEY_KEY = 'tohid-account-key-v1';
  const STAFFCODE_KEY = 'tohid-staff-code-v1';

  // بدون I/O/0/1 تا موقع خواندن و گفتن اشتباه نشود
  const KEY_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

  /** n کاراکترِ تصادفی از الفبای بالا، با بایت‌های امنِ مرورگر. */
  function randomChars(n) {
    const bytes = new Uint8Array(n);
    (self.crypto || window.crypto).getRandomValues(bytes);
    let out = '';
    for (let i = 0; i < n; i++) out += KEY_ALPHABET[bytes[i] % KEY_ALPHABET.length];
    return out;
  }

  /** زمانِ ساخت، فشرده در ۶ کاراکتر — تا کلیدهای دو زمان متفاوت هم‌ریشه نباشند. */
  function timeChunk() {
    let t = Date.now();
    let out = '';
    for (let i = 0; i < 6; i++) { out = KEY_ALPHABET[t % KEY_ALPHABET.length] + out; t = Math.floor(t / KEY_ALPHABET.length); }
    return out;
  }

  function group(str, size) {
    const parts = [];
    for (let i = 0; i < str.length; i += size) parts.push(str.slice(i, i + size));
    return parts.join('-');
  }

  const API_KEY_RE  = /^TSH-[A-Z0-9]{5}(-[A-Z0-9]{5}){4}$/;
  const STAFF_KEY_RE = /^SHG-[A-Z0-9]{5}(-[A-Z0-9]{5}){2}$/;

  /** کلید حساب — ۲۵ کاراکتر (۶ زمانی + ۱۹ تصادفی) در پنج دسته‌ی پنج‌تایی. */
  function getApiKey() {
    let key = '';
    try { key = localStorage.getItem(APIKEY_KEY) || ''; } catch {}
    if (!API_KEY_RE.test(key)) {
      key = 'TSH-' + group(timeChunk() + randomChars(19), 5);
      try { localStorage.setItem(APIKEY_KEY, key); } catch {}
    }
    return key;
  }

  /** کد شاگرد — ۱۵ کاراکتر، برای دادن به کارکنانِ همان دکان. */
  function getStaffCode() {
    let code = '';
    try { code = localStorage.getItem(STAFFCODE_KEY) || ''; } catch {}
    if (!STAFF_KEY_RE.test(code)) {
      code = 'SHG-' + group(timeChunk() + randomChars(9), 5);
      try { localStorage.setItem(STAFFCODE_KEY, code); } catch {}
    }
    return code;
  }

  /** کد تازه می‌سازد — وقتی صاحب دکان بخواهد کد قبلی دیگر کار نکند. */
  function rotateStaffCode() {
    const code = 'SHG-' + group(timeChunk() + randomChars(9), 5);
    try { localStorage.setItem(STAFFCODE_KEY, code); } catch {}
    return code;
  }

  /** شناسه‌ی پایدار دستگاه — یک بار ساخته و ذخیره می‌شود. */
  function getDeviceUid() {
    let uid = '';
    try { uid = localStorage.getItem(DEVICE_KEY) || ''; } catch {}
    if (!/^[A-Za-z0-9_-]{8,128}$/.test(uid)) {
      const bytes = new Uint8Array(16);
      (self.crypto || window.crypto).getRandomValues(bytes);
      uid = Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('');
      try { localStorage.setItem(DEVICE_KEY, uid); } catch {}
    }
    return uid;
  }

  /** اثر انگشت سبک دستگاه — فقط برای تشخیص جابه‌جایی License، نه ردیابی. */
  function deviceFingerprint() {
    const n = navigator;
    return [
      n.userAgent || '', n.language || '', n.platform || '',
      String(screen.width) + 'x' + String(screen.height),
      String(new Date().getTimezoneOffset()),
      String(n.hardwareConcurrency || 0),
    ].join('|');
  }
  function deviceName() {
    const ua = navigator.userAgent || '';
    if (/Android/i.test(ua)) return 'اندروید';
    if (/iPhone|iPad|iPod/i.test(ua)) return 'آی‌او‌اس';
    if (/Windows/i.test(ua)) return 'ویندوز';
    if (/Mac OS/i.test(ua)) return 'مک';
    if (/Linux/i.test(ua)) return 'لینوکس';
    return 'دستگاه';
  }

  function b64uToBytes(s) {
    const t = String(s).replace(/-/g, '+').replace(/_/g, '/');
    const pad = t + '='.repeat((4 - (t.length % 4)) % 4);
    const bin = atob(pad);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  function b64ToBytes(s) {
    const bin = atob(String(s));
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  function bytesToText(b) { return new TextDecoder().decode(b); }

  function fmtDate(ms, tz) {
    if (!ms) return '—';
    try {
      return new Intl.DateTimeFormat('fa-IR', {
        timeZone: tz || 'Asia/Kabul', year: 'numeric', month: '2-digit', day: '2-digit',
      }).format(new Date(ms));
    } catch { return new Date(ms).toISOString().slice(0, 10); }
  }
  function daysBetween(a, b) { return Math.ceil((b - a) / 86400000); }

  // ---------- ساعت مقاوم در برابر دستکاری ----------
  /**
   * زمان مؤثر = بیشترینِ (تخمین زمان واقعی، بالاترین زمان دیده‌شده تا امروز).
   *
   * چرا: اگر کاربر ساعت را عقب ببرد، «بالاترین زمان دیده‌شده» ثابت می‌ماند و
   * اشتراک تمدید نمی‌شود. تغییر منطقه زمانی هیچ اثری ندارد، چون Date.now()
   * همیشه UTC است.
   */
  const Clock = {
    read() {
      const st = readStore();
      const raw = Date.now();
      const offset = (typeof st.serverTimeOffset === 'number') ? st.serverTimeOffset : 0;
      const candidate = raw + offset;
      const highWater = (typeof st.timeHighWater === 'number') ? st.timeHighWater : 0;

      // تنها حالتی که از نظر امنیتی مهم است: عقب رفتن ساعت.
      // جلو رفتن ساعت به سود کاربر نیست (فقط زودتر قفل می‌شود)، پس فقط
      // وقتی آشکارا نامعقول است هشدار داده می‌شود تا کاربر Sync کند.
      let status = 'ok';
      if (candidate < highWater - CFG.CLOCK_BACK_TOLERANCE_MS) status = 'rolled_back';
      else if (highWater && candidate > highWater + CFG.CLOCK_FORWARD_FLAG_MS) status = 'jumped_forward';

      // برای تصمیم انقضا هرگز عقب‌تر از بالاترین زمان دیده‌شده نمی‌رویم
      const effective = Math.max(candidate, highWater);
      return { raw, candidate, effective, highWater, status, offset };
    },

    /**
     * ثبت گذر زمان.
     * بالاترین زمان دیده‌شده همیشه با حرکت رو به جلو بالا می‌رود — حتی اگر
     * جهش بزرگ باشد. دلیل: برنامه ممکن است هفته‌ها بسته بوده باشد، و اگر
     * آن گذرِ واقعی ثبت نشود، محافظت در برابر عقب بردن ساعت از کار می‌افتد.
     * جهش جلو به سود کاربر نیست؛ با یک بار Sync هم کاملاً اصلاح می‌شود.
     */
    tick() {
      const c = Clock.read();
      const patch = { lastRunAt: c.raw, clockStatus: c.status };
      if (c.candidate > c.highWater) patch.timeHighWater = c.candidate;
      writeStore(patch);
      return c;
    },

    /**
     * زمان سرور مرجع مطلق است. بالاترین زمان دقیقاً روی زمان سرور تنظیم
     * می‌شود (نه max) تا اگر ساعت دستگاه قبلاً جلو برده شده بود، یک Sync
     * وضعیت را کاملاً درست کند.
     */
    syncWithServer(serverTimeMs) {
      const raw = Date.now();
      writeStore({
        serverTimeOffset: serverTimeMs - raw,
        lastServerTime: serverTimeMs,
        lastServerSyncClientTime: raw,
        timeHighWater: serverTimeMs,
        clockStatus: 'ok',
      });
    },
  };

  // ---------- بررسی License ----------
  let publicKeyPromise = null;
  async function getPublicKey() {
    if (publicKeyPromise) return publicKeyPromise;
    publicKeyPromise = (async () => {
      const st = readStore();
      const b64 = CFG.PINNED_PUBLIC_KEY || st.publicKey || '';
      if (!b64) throw new Error('کلید عمومی سرور در دسترس نیست');
      return crypto.subtle.importKey(
        'spki', b64ToBytes(b64), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']
      );
    })();
    return publicKeyPromise;
  }
  function resetPublicKey() { publicKeyPromise = null; }

  /** بررسی امضا و ساختار License. بدون امضای معتبر هیچ چیزی پذیرفته نمی‌شود. */
  async function verifyLicense(token) {
    if (typeof token !== 'string' || token.split('.').length !== 3) {
      return { ok: false, reason: 'format' };
    }
    const [h, b, s] = token.split('.');
    let header, payload;
    try {
      header = JSON.parse(bytesToText(b64uToBytes(h)));
      payload = JSON.parse(bytesToText(b64uToBytes(b)));
    } catch { return { ok: false, reason: 'format' }; }

    if (!header || header.alg !== 'ES256' || header.typ !== 'TLIC') {
      return { ok: false, reason: 'header' };
    }
    let key;
    try { key = await getPublicKey(); }
    catch { return { ok: false, reason: 'no_key' }; }

    const data = new TextEncoder().encode(h + '.' + b);
    let ok = false;
    try {
      ok = await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, b64uToBytes(s), data);
    } catch { return { ok: false, reason: 'crypto_error' }; }
    if (!ok) return { ok: false, reason: 'signature' };

    // بررسی‌های محتوایی — امضا معتبر است، حالا ببینیم مال همین برنامه/دستگاه است
    if (payload.iss !== CFG.ISSUER)   return { ok: false, reason: 'issuer' };
    if (payload.aud !== CFG.AUDIENCE) return { ok: false, reason: 'audience' };
    if (payload.duid !== getDeviceUid()) return { ok: false, reason: 'device_mismatch' };

    return { ok: true, header, payload };
  }

  // ---------- وضعیت ----------
  const State = {
    ready: false,
    licenseValid: false,
    reason: null,          // چرا نامعتبر
    payload: null,
    state: 'unknown',      // unknown | none | active | grace | expired | invalid | clock_error
    features: [],          // قابلیت‌های مجاز همین حالا
    core: ['dashboard', 'products', 'settings'],
    clock: null,
    lastSyncAt: null,
    expiresAt: null,
    subEndsAt: null,
    timezone: 'Asia/Kabul',
    plan: null,
  };

  function hasFeature(key) {
    if (!LICENSE_ENFORCED) return true;        // اجرا خاموش است — همه چیز باز
    if (State.core.includes(key)) return true;
    return State.features.includes(key);
  }

  /** ارزیابی License ذخیره‌شده — همان چیزی که در حالت آفلاین اجرا می‌شود. */
  async function evaluateLocal() {
    const st = readStore();
    const clock = Clock.tick();
    State.clock = clock;
    State.lastSyncAt = st.lastSyncOkAt || null;

    if (!st.license) {
      Object.assign(State, { licenseValid: false, payload: null, state: 'none',
                             features: [], reason: 'no_license', ready: true });
      return State;
    }

    const v = await verifyLicense(st.license);
    if (!v.ok) {
      Object.assign(State, { licenseValid: false, payload: null, state: 'invalid',
                             features: [], reason: v.reason, ready: true });
      return State;
    }

    const p = v.payload;

    // سرور آخرین بار صریحاً دسترسی را رد کرده و آن رد تازه‌تر از این License است:
    // حرف سرور مقدم است و قابلیت‌ها قفل می‌شوند (داده‌های کاربر دست‌نخورده می‌ماند).
    if (st.serverDenied && st.serverDenied.at >= (p.iat || 0)) {
      Object.assign(State, {
        licenseValid: true, payload: p, features: [], ready: true,
        state: st.serverDenied.code === 'device_revoked' ? 'revoked' : 'expired',
        reason: st.serverDenied.code,
        timezone: p.tz || 'Asia/Kabul', expiresAt: p.exp, subEndsAt: p.sub_ends,
        core: Array.isArray(p.core) ? p.core : State.core,
      });
      return State;
    }
    State.payload = p;
    State.licenseValid = true;
    State.timezone = p.tz || 'Asia/Kabul';
    State.plan = p.plan || null;
    State.expiresAt = p.exp;
    State.subEndsAt = p.sub_ends;
    State.core = Array.isArray(p.core) ? p.core : State.core;

    const t = clock.effective;
    if (t < p.nbf) {
      Object.assign(State, { state: 'pending', features: [], reason: 'not_started', ready: true });
    } else if (t > p.exp) {
      Object.assign(State, { state: 'expired', features: [], reason: 'expired', ready: true });
    } else {
      const inGrace = t > p.sub_ends;
      Object.assign(State, {
        state: inGrace ? 'grace' : 'active',
        features: Array.isArray(p.feat) ? p.feat.slice() : [],
        reason: null, ready: true,
      });
    }
    return State;
  }

  // ---------- ارتباط با سرور ----------
  async function api(path, { method = 'GET', body = null, auth = true } = {}) {
    const base = getServerUrl();
    if (!base) throw new ApiError('آدرس سرور تنظیم نشده است', 'no_server');
    if (!/^https?:\/\//i.test(base)) throw new ApiError('آدرس سرور باید با http:// یا https:// شروع شود', 'bad_server');

    const st = readStore();
    const headers = { 'Content-Type': 'application/json' };
    if (auth && st.accessToken) headers.Authorization = 'Bearer ' + st.accessToken;

    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), CFG.REQUEST_TIMEOUT_MS);
    let res;
    try {
      res = await fetch(base + path, {
        method, headers, signal: ctrl.signal,
        body: body ? JSON.stringify(body) : undefined,
      });
    } catch (e) {
      throw new ApiError(
        e.name === 'AbortError' ? 'سرور پاسخ نداد (زمان تمام شد)' : 'اتصال به سرور برقرار نشد',
        'network'
      );
    } finally { clearTimeout(timer); }

    let data = null;
    try { data = await res.json(); } catch {}

    if (!res.ok) {
      const err = new ApiError(
        (data && data.error && data.error.message) || `خطای سرور (${res.status})`,
        (data && data.error && data.error.code) || 'http_' + res.status
      );
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  class ApiError extends Error {
    constructor(message, code) { super(message); this.code = code; }
  }

  /** تازه‌سازی خودکار توکن دسترسی در صورت انقضا. */
  async function apiWithRefresh(path, opts) {
    try {
      return await api(path, opts);
    } catch (e) {
      if (e.code !== 'invalid_token' && e.status !== 401) throw e;
      const st = readStore();
      if (!st.refreshToken) throw e;
      const r = await api('/api/v1/auth/refresh', {
        method: 'POST', auth: false, body: { refreshToken: st.refreshToken },
      });
      writeStore({ accessToken: r.accessToken, accessExpiresAt: r.accessExpiresAt });
      return api(path, opts);
    }
  }

  function devicePayload() {
    return {
      uid: getDeviceUid(),
      name: deviceName(),
      platform: (navigator.userAgent || '').slice(0, 120),
      fingerprint: deviceFingerprint(),
    };
  }

  /** اگر کلید عمومی پین نشده، یک بار از سرور گرفته و ذخیره می‌شود. */
  async function ensurePublicKey() {
    if (CFG.PINNED_PUBLIC_KEY) return;
    const st = readStore();
    if (st.publicKey) return;
    const r = await api('/api/v1/license/public-key', { auth: false });
    if (!r || !r.publicKey) throw new ApiError('کلید عمومی از سرور دریافت نشد', 'no_key');
    writeStore({ publicKey: r.publicKey, publicKeyId: r.keyId });
    resetPublicKey();
  }

  async function login(identifier, password) {
    const r = await api('/api/v1/auth/login', {
      method: 'POST', auth: false, body: { identifier, password },
    });
    writeStore({
      accessToken: r.accessToken, accessExpiresAt: r.accessExpiresAt,
      refreshToken: r.refreshToken, userId: r.user.id,
      userLabel: r.user.name || r.user.email || r.user.phone || '',
    });
    return r;
  }

  async function register(payload) {
    return api('/api/v1/auth/register', { method: 'POST', auth: false, body: payload });
  }

  /* ==========================================================
     ثبت‌نام سه‌مرحله‌ای — فقط با ایمیل
     ----------------------------------------------------------
     قرار صاحب مخزن: شماره‌ی موبایل برداشته شد؛ «همان ایمیل بس است».
     سه پله:
       ۱) نام، ایمیل، رمز و تکرارش → کد به همان ایمیل می‌رود
       ۲) کد شش‌رقمی → «بلیت» بیست‌دقیقه‌ای
       ۳) بلیت + لوکیشن + پذیرش شرایط → حساب ساخته و همان‌جا وارد می‌شود

     رمز روی سرور تا پله‌ی سوم نمی‌ماند؛ برنامه خودش تا آن لحظه نگهش
     می‌دارد. یعنی اگر کاربر وسط کار پنجره را ببندد، هیچ حسابِ نیم‌بندی
     روی سرور جا نمی‌ماند و ایمیلش هم آزاد می‌ماند.
     ========================================================== */

  async function registerStart({ name, email, password, passwordConfirm }) {
    return api('/api/v1/auth/register/start', {
      method: 'POST', auth: false, body: { name, email, password, passwordConfirm },
    });
  }

  async function registerVerify({ email, code }) {
    return api('/api/v1/auth/register/verify', {
      method: 'POST', auth: false, body: { email, code, device: devicePayload() },
    });
  }

  async function registerComplete({ ticket, name, password, terms, location }) {
    const r = await api('/api/v1/auth/register/complete', {
      method: 'POST', auth: false,
      body: { ticket, name, password, terms, location, device: devicePayload() },
    });
    if (r && r.accessToken) {
      writeStore({
        accessToken: r.accessToken, accessExpiresAt: r.accessExpiresAt,
        refreshToken: r.refreshToken, userId: r.user.id,
        userLabel: r.user.name || r.user.email || '',
      });
    }
    return r;
  }

  /**
   * فرستادن لوکیشن به سرور.
   *
   * حساب لازم نیست: قرار صاحب مخزن این بود که لوکیشن حتی پیش از ثبت‌نام
   * هم ثبت شود. اگر توکنی باشد، سرور ردیف را به همان حساب می‌بندد؛ اگر
   * نباشد، ردیف به شناسه‌ی دستگاه بسته می‌شود و روزی که همان دستگاه حساب
   * ساخت، به آن حساب می‌چسبد.
   *
   * شکست هیچ‌وقت به بیرون پرت نمی‌شود — لوکیشن یک خبر است، نه یک شرط.
   */
  async function sendLocation(location) {
    if (!location || !getServerUrl()) return null;
    try {
      return await api('/api/v1/location', {
        method: 'POST', auth: true, body: { device: devicePayload(), location },
      });
    } catch (e) {
      if (e.code !== 'network') console.warn('[location] ثبت نشد:', e.message);
      return null;
    }
  }

  /** فعال‌سازی: دستگاه ثبت و License صادر می‌شود. */
  async function activate() {
    await ensurePublicKey();
    let r;
    try {
      r = await apiWithRefresh('/api/v1/license/activate', {
        method: 'POST', body: { device: devicePayload() },
      });
    } catch (e) {
      if (e.code === 'subscription_inactive' || e.code === 'device_revoked' ||
          e.code === 'no_subscription' || e.code === 'account_disabled') {
        writeStore({ serverDenied: { code: e.code, message: e.message, at: Date.now() } });
        await evaluateLocal();
        Gate.apply();
        notify();
      }
      throw e;
    }
    if (r.accessToken) writeStore({ accessToken: r.accessToken, accessExpiresAt: r.accessExpiresAt });
    writeStore({ serverDenied: null });
    await storeLicense(r);
    return r;
  }

  /**
   * همگام‌سازی: License تازه گرفته می‌شود (تمدید/تغییر اشتراک).
   *
   * نکته‌ی مهم: اگر سرور صریحاً بگوید اشتراک فعال نیست یا دستگاه لغو شده،
   * همان لحظه ثبت می‌شود و برنامه دیگر روی License قدیمی ادامه نمی‌دهد.
   * کار کردن با License ذخیره‌شده فقط برای وقتی است که سرور در دسترس نباشد.
   */
  async function sync() {
    await ensurePublicKey();
    let r;
    try {
      r = await apiWithRefresh('/api/v1/license/sync', {
        method: 'POST', body: { device: devicePayload() },
      });
    } catch (e) {
      if (e.code === 'subscription_inactive' || e.code === 'device_revoked' ||
          e.code === 'no_subscription' || e.code === 'account_disabled') {
        writeStore({ serverDenied: { code: e.code, message: e.message, at: Date.now() } });
        await evaluateLocal();
        Gate.apply();
        notify();
      }
      throw e;
    }
    writeStore({ serverDenied: null });   // سرور تأیید کرد؛ رد قبلی پاک می‌شود
    await storeLicense(r);
    return r;
  }

  async function storeLicense(r) {
    if (r.serverTime) Clock.syncWithServer(r.serverTime);
    if (r.license) {
      // پیش از ذخیره، امضا بررسی می‌شود تا License خراب ذخیره نشود
      const v = await verifyLicense(r.license);
      if (!v.ok) throw new ApiError('License دریافتی معتبر نیست (' + v.reason + ')', 'bad_license');
      writeStore({
        license: r.license, licenseId: r.licenseId,
        licenseExpiresAt: r.expiresAt, lastSyncOkAt: Date.now(),
      });
    }
    await evaluateLocal();
    Gate.apply();
    notify();
  }

  function logout() {
    clearStore();
    resetPublicKey();
    Object.assign(State, { licenseValid: false, payload: null, state: 'none', features: [], reason: 'no_license' });
    Gate.apply();
    notify();
  }

  // ---------- شنوندگان ----------
  const listeners = new Set();
  function onChange(fn) { listeners.add(fn); return () => listeners.delete(fn); }
  function notify() {
    for (const fn of listeners) { try { fn(State); } catch (e) { console.error(e); } }
    document.dispatchEvent(new CustomEvent('tohid:license-change', { detail: State }));
  }

  // ---------- قفل رابط کاربری ----------
  const Gate = {
    /** فهرست قابلیت‌های قفل — هر چیزی که در License نیست و core هم نیست. */
    lockedFeatures() {
      const all = Object.keys(FEATURE_LABELS);
      return all.filter(k => !hasFeature(k));
    },

    apply() {
      const root = document.documentElement;
      if (!LICENSE_ENFORCED) {
        // هر اثری که قبلاً گذاشته شده پاک شود تا برنامه کاملاً آزاد باشد
        root.removeAttribute('data-lic-state');
        root.removeAttribute('data-lic-locked');
        Object.keys(FEATURE_LABELS).forEach(k => root.classList.remove('lic-lock-' + k));
        $$('.lic-overlay').forEach(el => el.remove());
        $$('[data-lic-feature]').forEach(el => {
          el.classList.remove('lic-locked-el');
          el.removeAttribute('data-lic-feature');
        });
        const bar = $('#lic-banner');
        if (bar) bar.remove();
        return;
      }
      const locked = Gate.lockedFeatures();

      root.setAttribute('data-lic-state', State.state);
      root.setAttribute('data-lic-locked', locked.join(' '));

      // کلاس برای هر قابلیت قفل — CSS بقیه کار را می‌کند
      Object.keys(FEATURE_LABELS).forEach(k => {
        root.classList.toggle('lic-lock-' + k, locked.includes(k));
      });

      Gate.paintPages(locked);
      Gate.paintElements(locked);
      Gate.paintBanner();
    },

    /** روی هر صفحه‌ی قفل، یک پوشش «نیازمند اشتراک» می‌گذارد. */
    paintPages(locked) {
      Object.keys(FEATURE_PAGES).forEach(feature => {
        FEATURE_PAGES[feature].forEach(pageId => {
          const page = document.getElementById('page-' + pageId);
          if (!page) return;
          let ov = page.querySelector(':scope > .lic-overlay');
          const isLocked = locked.includes(feature);

          if (!isLocked) { if (ov) ov.remove(); return; }
          if (!ov) {
            ov = document.createElement('div');
            ov.className = 'lic-overlay';
            ov.innerHTML = Gate.overlayHtml(feature);
            ov.querySelector('[data-lic-open]').addEventListener('click', () => UI.open());
            page.appendChild(ov);
          }
        });
      });
    },

    overlayHtml(feature) {
      const label = FEATURE_LABELS[feature] || feature;
      const msg = State.state === 'expired' ? 'اشتراک شما به پایان رسیده است'
                : State.state === 'invalid' ? 'مجوز برنامه معتبر نیست'
                : State.state === 'pending' ? 'اشتراک شما هنوز شروع نشده است'
                : 'این بخش نیازمند اشتراک است';
      return `
        <div class="lic-overlay-card">
          <div class="lic-overlay-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round"><rect x="3" y="11" width="18" height="11" rx="2"/>
                 <path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          </div>
          <h3>${label}</h3>
          <p>${msg}</p>
          <button type="button" class="lic-btn" data-lic-open>مدیریت اشتراک</button>
        </div>`;
    },

    /** دکمه‌های وابسته به قابلیت را غیرفعال می‌کند. */
    paintElements(locked) {
      Object.keys(FEATURE_ELEMENTS).forEach(feature => {
        const isLocked = locked.includes(feature);
        FEATURE_ELEMENTS[feature].forEach(sel => {
          $$(sel).forEach(el => {
            el.classList.toggle('lic-locked-el', isLocked);
            if (isLocked) el.setAttribute('data-lic-feature', feature);
            else el.removeAttribute('data-lic-feature');
          });
        });
      });
    },

    /** نوار وضعیت بالای صفحه، وقتی اشتراک نزدیک پایان یا تمام‌شده است. */
    paintBanner() {
      let bar = $('#lic-banner');
      const st = readStore();
      let text = '', tone = '', show = false;

      if (State.state === 'revoked') {
        text = 'دسترسی این دستگاه لغو شده است — اطلاعات شما محفوظ است. با مدیر تماس بگیرید.';
        tone = 'danger'; show = true;
      } else if (State.state === 'expired') {
        text = 'اشتراک شما به پایان رسیده است — اطلاعات شما محفوظ است، برای باز شدن قابلیت‌ها تمدید کنید.';
        tone = 'danger'; show = true;
      } else if (State.state === 'invalid') {
        text = 'مجوز برنامه معتبر نیست. لطفاً دوباره فعال‌سازی کنید.';
        tone = 'danger'; show = true;
      } else if (State.state === 'none' && st.license === undefined) {
        show = false;
      } else if (State.clock && State.clock.status === 'rolled_back') {
        text = 'ساعت دستگاه عقب‌تر از زمان معتبر ثبت‌شده است. برای اصلاح، به سرور وصل شوید.';
        tone = 'warning'; show = true;
      } else if (State.state === 'grace') {
        text = 'اشتراک شما تمام شده و در مهلت تمدید هستید.';
        tone = 'warning'; show = true;
      } else if (State.state === 'active' && State.subEndsAt) {
        const left = daysBetween(State.clock.effective, State.subEndsAt);
        if (left <= 7) {
          text = `${left.toLocaleString('fa-IR')} روز تا پایان اشتراک باقی مانده است.`;
          tone = 'warning'; show = true;
        }
      }

      if (!show) { if (bar) bar.remove(); return; }
      if (!bar) {
        bar = document.createElement('div');
        bar.id = 'lic-banner';
        bar.innerHTML = '<span class="lic-banner-text"></span>' +
                        '<button type="button" class="lic-btn lic-btn-sm" data-lic-open>مدیریت اشتراک</button>';
        bar.querySelector('[data-lic-open]').addEventListener('click', () => UI.open());
        document.body.appendChild(bar);
      }
      bar.className = 'lic-banner-' + tone;
      bar.querySelector('.lic-banner-text').textContent = text;
    },
  };

  /**
   * جلوگیری از کلیک روی دکمه‌های قفل — در فاز capture، پیش از آنکه
   * شنونده‌ی خود برنامه اجرا شود. منطق برنامه دست‌نخورده می‌ماند.
   */
  document.addEventListener('click', function (e) {
    const el = e.target.closest && e.target.closest('[data-lic-feature]');
    if (!el) return;
    const feature = el.getAttribute('data-lic-feature');
    if (hasFeature(feature)) return;
    e.preventDefault();
    e.stopPropagation();
    if (e.stopImmediatePropagation) e.stopImmediatePropagation();
    UI.open(FEATURE_LABELS[feature] || feature);
  }, true);

  // ---------- رابط کاربری اشتراک ----------
  const UI = {
    el: null,

    ensure() {
      if (UI.el) return UI.el;
      const wrap = document.createElement('div');
      wrap.id = 'lic-modal';
      wrap.className = 'lic-scrim';
      wrap.innerHTML = `
        <div class="lic-box" role="dialog" aria-modal="true" aria-label="مدیریت اشتراک">
          <div class="lic-head">
            <h3>اشتراک و مجوز برنامه</h3>
            <button type="button" class="lic-close" aria-label="بستن">&times;</button>
          </div>
          <div class="lic-body">
            <div class="lic-status" id="lic-status"></div>

            <div class="lic-section" id="lic-auth-section">
              <div class="lic-tabs">
                <button type="button" class="lic-tab lic-tab-active" data-lic-tab="login">ورود</button>
                <button type="button" class="lic-tab" data-lic-tab="register">ثبت‌نام</button>
              </div>

              <div data-lic-pane="login">
                <label class="lic-label">ایمیل یا شماره موبایل</label>
                <input type="text" id="lic-identifier" class="lic-input" dir="ltr" autocomplete="username">
                <label class="lic-label">رمز عبور</label>
                <input type="password" id="lic-password" class="lic-input" dir="ltr" autocomplete="current-password">
                <button type="button" class="lic-btn lic-btn-block" id="lic-do-login">ورود و فعال‌سازی</button>
              </div>

              <div data-lic-pane="register" hidden>
                <label class="lic-label">نام</label>
                <input type="text" id="lic-reg-name" class="lic-input">
                <p class="lic-hint">ایمیل یا شماره موبایل — هرکدام را داشتید کافی است، لازم نیست هر دو.</p>
                <label class="lic-label">ایمیل</label>
                <input type="email" id="lic-reg-email" class="lic-input" dir="ltr" autocomplete="email">
                <label class="lic-label">شماره موبایل</label>
                <input type="tel" id="lic-reg-phone" class="lic-input" dir="ltr" autocomplete="tel">
                <label class="lic-label">رمز عبور (حداقل ۸ کاراکتر)</label>
                <input type="password" id="lic-reg-password" class="lic-input" dir="ltr" autocomplete="new-password">
                <button type="button" class="lic-btn lic-btn-block" id="lic-do-register">ساخت حساب</button>
              </div>
            </div>

            <div class="lic-section" id="lic-actions-section" hidden>
              <button type="button" class="lic-btn lic-btn-block" id="lic-do-sync">همگام‌سازی با سرور</button>
              <button type="button" class="lic-btn lic-btn-ghost lic-btn-block" id="lic-do-logout">خروج از حساب</button>
            </div>

            <div class="lic-msg" id="lic-msg" hidden></div>

            <details class="lic-details">
              <summary>جزئیات فنی</summary>
              <div id="lic-tech" class="lic-tech"></div>
            </details>
          </div>
        </div>`;
      document.body.appendChild(wrap);
      UI.el = wrap;

      wrap.querySelector('.lic-close').addEventListener('click', UI.close);
      wrap.addEventListener('mousedown', (e) => { if (e.target === wrap) UI.close(); });

      $$('[data-lic-tab]', wrap).forEach(tab => {
        tab.addEventListener('click', () => {
          const name = tab.getAttribute('data-lic-tab');
          $$('[data-lic-tab]', wrap).forEach(t => t.classList.toggle('lic-tab-active', t === tab));
          $$('[data-lic-pane]', wrap).forEach(p => { p.hidden = p.getAttribute('data-lic-pane') !== name; });
        });
      });

      $('#lic-do-login', wrap).addEventListener('click', UI.doLogin);
      $('#lic-do-register', wrap).addEventListener('click', UI.doRegister);
      $('#lic-do-sync', wrap).addEventListener('click', UI.doSync);
      $('#lic-do-logout', wrap).addEventListener('click', () => { logout(); UI.render(); UI.msg('از حساب خارج شدید', 'ok'); });

      return wrap;
    },

    open(featureLabel) {
      /*
       * تا وارد حساب نشده‌اید، همان صفحه‌ی کامل «خوش آمدید» باز می‌شود —
       * ورود، ثبت‌نام و کد شاگرد یک‌جا. این پنجره‌ی کوچک فقط برای
       * مدیریت حسابِ واردشده است.
       */
      if (!readStore().accessToken && typeof window.openAuthScreen === 'function') {
        window.openAuthScreen(featureLabel
          ? `برای «${featureLabel}» اول وارد حساب شوید یا ثبت‌نام کنید.`
          : '');
        return;
      }
      UI.ensure();
      UI.render();
      if (featureLabel) UI.msg(`قابلیت «${featureLabel}» در اشتراک فعلی شما فعال نیست.`, 'warn');
      UI.el.classList.add('lic-open');
    },
    close() { if (UI.el) UI.el.classList.remove('lic-open'); },

    msg(text, kind) {
      const el = $('#lic-msg', UI.el);
      if (!el) return;
      el.hidden = !text;
      el.textContent = text || '';
      el.className = 'lic-msg' + (kind ? ' lic-msg-' + kind : '');
    },

    render() {
      const wrap = UI.ensure();
      const st = readStore();

      const loggedIn = !!st.accessToken;
      $('#lic-auth-section', wrap).hidden = loggedIn;
      $('#lic-actions-section', wrap).hidden = !loggedIn;

      // وضعیت
      const badge = { active: ['فعال', 'ok'], grace: ['مهلت تمدید', 'warn'],
                      expired: ['پایان‌یافته', 'bad'], invalid: ['نامعتبر', 'bad'],
                      revoked: ['دستگاه لغو شده', 'bad'],
                      pending: ['شروع نشده', 'warn'], none: ['بدون اشتراک', 'bad'] }[State.state]
                      || ['نامشخص', 'warn'];
      const rows = [
        ['وضعیت اشتراک', badge[0]],
        ['کاربر', st.userLabel || '—'],
        ['پایان اشتراک', State.subEndsAt ? fmtDate(State.subEndsAt, State.timezone) : '—'],
        ['اعتبار مجوز تا', State.expiresAt ? fmtDate(State.expiresAt, State.timezone) : '—'],
        ['آخرین همگام‌سازی', st.lastSyncOkAt ? fmtDate(st.lastSyncOkAt, State.timezone) : 'هرگز'],
      ];
      $('#lic-status', wrap).innerHTML =
        `<div class="lic-badge lic-badge-${badge[1]}">${badge[0]}</div>` +
        rows.slice(1).map(r => `<div class="lic-row"><span>${r[0]}</span><b>${r[1]}</b></div>`).join('');

      // قابلیت‌ها
      const items = Object.keys(FEATURE_LABELS).map(k => {
        const on = hasFeature(k);
        return `<li class="${on ? 'lic-on' : 'lic-off'}">${FEATURE_LABELS[k]}<span>${on ? 'فعال' : 'قفل'}</span></li>`;
      }).join('');
      $('#lic-tech', wrap).innerHTML =
        `<ul class="lic-featlist">${items}</ul>` +
        `<div class="lic-row"><span>شناسه دستگاه</span><b dir="ltr">${getDeviceUid().slice(0, 12)}…</b></div>` +
        `<div class="lic-row"><span>شناسه مجوز</span><b dir="ltr">${st.licenseId || '—'}</b></div>` +
        `<div class="lic-row"><span>وضعیت ساعت</span><b>${
          { ok: 'عادی', rolled_back: 'عقب‌رفته', jumped_forward: 'جهش جلو' }[State.clock && State.clock.status] || '—'
        }</b></div>` +
        `<div class="lic-row"><span>منطقه زمانی</span><b dir="ltr">${State.timezone}</b></div>`;
    },

    async guard(btn, fn) {
      const old = btn.textContent;
      btn.disabled = true; btn.textContent = 'لطفاً صبر کنید…';
      try { await fn(); }
      catch (e) { UI.msg(e.message || 'خطای ناشناخته', 'bad'); }
      finally { btn.disabled = false; btn.textContent = old; UI.render(); }
    },

    doLogin() {
      const btn = $('#lic-do-login', UI.el);
      UI.msg('');
      UI.guard(btn, async () => {
        const id = $('#lic-identifier', UI.el).value.trim();
        const pass = $('#lic-password', UI.el).value;
        if (!id || !pass) throw new Error('نام کاربری و رمز عبور را وارد کنید');
        await login(id, pass);
        try {
          await activate();
          UI.msg('فعال‌سازی انجام شد؛ اشتراک شما به‌روز است.', 'ok');
        } catch (e) {
          if (e.code === 'no_subscription' || e.code === 'subscription_inactive') {
            await evaluateLocal(); Gate.apply(); notify();
            UI.msg(e.message + ' — بخش‌های رایگان برنامه در دسترس است.', 'warn');
          } else throw e;
        }
        $('#lic-password', UI.el).value = '';
      });
    },

    doRegister() {
      const btn = $('#lic-do-register', UI.el);
      UI.msg('');
      UI.guard(btn, async () => {
        const email = $('#lic-reg-email', UI.el).value.trim();
        const phone = $('#lic-reg-phone', UI.el).value.trim();
        // یکی از این دو کافی است؛ اجبار به هر دو، ثبت‌نام را بی‌دلیل سخت می‌کرد
        if (!email && !phone) throw new Error('ایمیل یا شماره موبایل را وارد کنید — یکی کافی است');
        await register({
          name: $('#lic-reg-name', UI.el).value.trim(),
          email: email || undefined,
          phone: phone || undefined,
          password: $('#lic-reg-password', UI.el).value,
        });
        UI.msg('حساب ساخته شد. حالا از تب «ورود» وارد شوید.', 'ok');
      });
    },

    doSync() {
      const btn = $('#lic-do-sync', UI.el);
      UI.msg('');
      UI.guard(btn, async () => {
        try {
          await sync();
          UI.msg('همگام‌سازی انجام شد.', 'ok');
        } catch (e) {
          if (e.code === 'subscription_inactive') {
            await evaluateLocal(); Gate.apply(); notify();
            UI.msg(e.message, 'warn');
          } else throw e;
        }
      });
    },
  };

  // ---------- همگام‌سازی خودکار ----------
  async function autoSync(reason) {
    if (!LICENSE_ENFORCED) return;
    const st = readStore();
    if (!st.accessToken || !getServerUrl()) return;
    if (!navigator.onLine) return;
    const last = st.lastSyncAttemptAt || 0;
    if (reason === 'interval' && Date.now() - last < CFG.AUTO_SYNC_INTERVAL_MS) return;
    writeStore({ lastSyncAttemptAt: Date.now() });
    try { await sync(); }
    catch (e) {
      // شکست Sync هرگز نباید برنامه را از کار بیندازد — آفلاین ادامه می‌دهد
      if (e.code !== 'network') console.warn('[license] همگام‌سازی ناموفق:', e.message);
    }
  }

  // ---------- راه‌اندازی ----------
  async function init() {
    try {
      await evaluateLocal();
    } catch (e) {
      console.error('[license] ارزیابی مجوز ناموفق بود:', e);
      // در بدترین حالت فقط قابلیت‌های پایه باز می‌مانند؛ داده‌های کاربر دست‌نخورده است
      Object.assign(State, { ready: true, state: 'invalid', features: [], reason: 'init_error' });
    }
    Gate.apply();
    notify();

    autoSync('startup');
    const t = setInterval(() => autoSync('interval'), 30 * 60 * 1000);
    if (t.unref) t.unref();
    window.addEventListener('online', () => autoSync('online'));

    // هر ۵ دقیقه ساعت بررسی می‌شود تا انقضا در همان نشست هم اثر کند
    setInterval(async () => {
      const before = State.state;
      await evaluateLocal();
      if (State.state !== before) { Gate.apply(); notify(); }
    }, 5 * 60 * 1000);
  }

  // ---------- API عمومی ----------
  window.TohidLicense = {
    get enforced() { return LICENSE_ENFORCED; },
    get state() { return State; },
    hasFeature, onChange,
    open: (label) => UI.open(label),
    sync, activate, login, register, logout,
    registerStart, registerVerify, registerComplete, sendLocation,
    getServerUrl, setServerUrl, getDeviceUid,
    getApiKey, getStaffCode, rotateStaffCode,
    isLoggedIn: () => !!readStore().accessToken,
    userLabel: () => readStore().userLabel || '',
    verifyLicense, evaluate: evaluateLocal,
    _clock: Clock,
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
