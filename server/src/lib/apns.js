'use strict';
/**
 * APNs — پوشِ برنامهٔ آیفون (docs/IOS-PLAN-fa.md §۰.۱۰).
 *
 * برنامهٔ آیفون توکنِ APNs را با `provider = apns` در `POST /api/me/push` ثبت می‌کند (گامِ ۴). این‌جا
 * همان توکن‌ها از درِ خودِ اپل فرستاده می‌شوند — کنارِ FCM ِ `push.js`، نه به‌جای آن.
 *
 * ── راه ────────────────────────────────────────────────────────────
 * HTTP/2 به `api.push.apple.com` (یا `api.sandbox.push.apple.com`) با «توکنِ فراهم‌کننده»: JWT ِ ES256 که
 * با کلیدِ p8 ِ حسابِ اپل امضا می‌شود (`kid` = Key ID، `iss` = Team ID). همه با `http2` و `crypto` ِ خودِ
 * Node — بی وابستگیِ تازه. اپل می‌خواهد توکن نه زودتر از ۲۰ دقیقه و نه دیرتر از ۶۰ دقیقه تازه شود ⇒ ۴۰ دقیقه.
 *
 * ── تنظیم (فقط از محیط؛ فقط صاحب‌کار — §۰.۶ بندِ ۶) ─────────────────
 *   APNS_KEY_ID     ده نویسه (Key ID ِ کلیدِ APNs)
 *   APNS_TEAM_ID    ده نویسه (Team ID)
 *   APNS_KEY_P8     متنِ فایلِ ‎.p8‎ (PEM؛ «\n» ِ نوشته‌شده هم پذیرفته است، یا base64 ِ همان فایل/بدنه)
 *   APNS_BUNDLE_ID  شناسهٔ برنامه، مثلاً ir.vil3ntec.tohid (همان `apns-topic`)
 *   APNS_ENV        production (پیش‌فرض) یا sandbox (= development؛ ساختِ Xcode ِ Debug)
 * هیچ‌کدام نیست ⇒ خاموشِ بی‌صدا و همان رفتارِ گامِ ۴: توکن‌های apns نگه داشته می‌شوند، نه فرستاده، نه کهنه.
 * ناقص یا خراب ⇒ خاموش با یک هشدارِ کوتاه که فقط **نامِ** متغیر/دلیل را می‌گوید.
 *
 * ⛔ توکنِ دستگاه، کلید و JWT هرگز در لاگ، خطا، `last_error` یا پاسخ نمی‌آیند. نتیجهٔ هر فرستادن فقط
 * «کد + دلیلِ اپل» است (مثلاً `410 Unregistered`).
 *
 * ── پاسخ‌ها ─────────────────────────────────────────────────────────
 *   200                         ⇒ رسید
 *   410 ‌/ 400 BadDeviceToken   ⇒ توکن مرده ⇒ `dead` (ردیف «کهنه»؛ برنامه هر روز دوباره ثبتش می‌کند و زنده می‌شود)
 *   429، 500، 503، قطعِ اتصال  ⇒ دوباره با عقب‌نشینیِ نمایی (پیش‌فرض ۳ بار)
 *   403 ExpiredProviderToken    ⇒ یک بار با JWT ِ تازه (فقط اگر کهنه‌تر از ۲۰ دقیقه است)
 *   403 دیگر (کلید/تیمِ نادرست) ⇒ `fatal`: بقیهٔ همین دسته فرستاده نمی‌شود
 */
const http2 = require('node:http2');
const { createHash, createPrivateKey, sign } = require('node:crypto');

const HOSTS = Object.freeze({
  production: 'https://api.push.apple.com',
  sandbox: 'https://api.sandbox.push.apple.com',
});
const REQUIRED = ['APNS_KEY_ID', 'APNS_TEAM_ID', 'APNS_KEY_P8', 'APNS_BUNDLE_ID'];

/** بیشینهٔ بدنهٔ اعلانِ اپل (بایت) */
const MAX_PAYLOAD = 4096;
/** تازه کردنِ JWT: اپل ≥ ۲۰ و ≤ ۶۰ دقیقه می‌خواهد */
const TOKEN_REFRESH_MS = 40 * 60 * 1000;
const TOKEN_MIN_AGE_MS = 20 * 60 * 1000;
/** نگه‌داشتنِ پیام نزدِ اپل وقتی گوشی خاموش است */
const DEFAULT_TTL_SEC = 24 * 3600;
const IDLE_MS = 10 * 60 * 1000;

/** توکنِ دستگاهِ APNs: بایت‌های هگز (امروز ۳۲ بایت؛ اپل می‌گوید طولش ثابت نیست) */
function validToken(token) {
  return typeof token === 'string' && /^(?:[0-9a-fA-F]{2}){16,200}$/.test(token);
}

function b64url(buf) {
  return Buffer.from(buf).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** متنِ کلید ⇐ PEM: خودِ PEM، PEM با «\n» ِ نوشته‌شده، base64 ِ کلِ فایل، یا فقط بدنهٔ base64 */
function pemOf(raw) {
  let s = String(raw || '').trim().replace(/\\n/g, '\n');
  if (s.includes('-----BEGIN')) return s;
  const compact = s.replace(/\s+/g, '');
  const decoded = Buffer.from(compact, 'base64').toString('utf8');
  if (decoded.includes('-----BEGIN')) return decoded;
  return `-----BEGIN PRIVATE KEY-----\n${(compact.match(/.{1,64}/g) || []).join('\n')}\n-----END PRIVATE KEY-----\n`;
}

/**
 * تنظیم از محیط. هرگز مقدار را برنمی‌گرداند جز آنچه فرستادن لازم دارد؛ دلیلِ خرابی فقط نام است.
 * @returns {{ok:true, cfg:object} | {ok:false, reason:string, missing?:string[]}}
 */
function readConfig(env = process.env) {
  const val = (k) => String(env[k] == null ? '' : env[k]).trim();
  const missing = REQUIRED.filter((k) => !val(k));
  if (missing.length === REQUIRED.length) return { ok: false, reason: 'off' };
  if (missing.length) return { ok: false, reason: 'missing', missing };

  const keyId = val('APNS_KEY_ID');
  const teamId = val('APNS_TEAM_ID');
  const bundleId = val('APNS_BUNDLE_ID');
  if (!/^[A-Z0-9]{10}$/.test(keyId)) return { ok: false, reason: 'bad_key_id' };
  if (!/^[A-Z0-9]{10}$/.test(teamId)) return { ok: false, reason: 'bad_team_id' };
  if (!/^[A-Za-z0-9][A-Za-z0-9.-]{0,154}$/.test(bundleId)) return { ok: false, reason: 'bad_bundle_id' };

  const name = (val('APNS_ENV') || 'production').toLowerCase();
  const mode = name === 'development' ? 'sandbox' : name;
  if (!HOSTS[mode]) return { ok: false, reason: 'bad_env' };

  let key;
  try {
    key = createPrivateKey(pemOf(val('APNS_KEY_P8')));
  } catch {
    return { ok: false, reason: 'bad_key' };
  }
  //  کلیدِ APNs همیشه EC ِ P-256 است (ES256)؛ هر کلیدِ دیگری امضای پذیرفتنی نمی‌سازد
  if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails?.namedCurve !== 'prime256v1') {
    return { ok: false, reason: 'bad_key' };
  }
  return { ok: true, cfg: { keyId, teamId, bundleId, env: mode, origin: HOSTS[mode], key } };
}

/** بدنهٔ اعلان: `aps` + دادهٔ پیام در سطحِ بالا (رشته)؛ بیش از ۴KB ⇒ متن کوتاه می‌شود، نه خطا */
function payloadOf(message = {}) {
  const extra = {};
  for (const [k, v] of Object.entries(message.data || {})) {
    if (k !== 'aps' && v !== undefined && v !== null) extra[k] = String(v);
  }
  const build = (title, body, withData) => {
    const aps = { alert: { title, body }, sound: 'default' };
    if (message.channel) aps['thread-id'] = String(message.channel);
    return JSON.stringify({ aps, ...(withData ? extra : {}) });
  };
  const title = Array.from(String(message.title || '')).slice(0, 200).join('');
  let body = Array.from(String(message.body || ''));
  let withData = true;
  if (Buffer.byteLength(build(title, '', true)) > MAX_PAYLOAD) withData = false;
  let json = build(title, body.join(''), withData);
  while (Buffer.byteLength(json) > MAX_PAYLOAD && body.length) {
    body = body.slice(0, Math.floor(body.length * 0.8));
    json = build(title, `${body.join('')}…`, withData);
  }
  return json;
}

/**
 * فرستنده‌ای روی یک نشستِ HTTP/2 (بازمانده میانِ پیام‌ها، همان خواستِ اپل).
 *
 * `opts` فقط برای آزمون: `origin` (سرورِ ساختگیِ HTTP/2 ِ محلی)، `sleep`، `now`، `retries`، `timeoutMs`.
 * در کارِ عادی `origin` همان نشانیِ اپل ِ `readConfig` است.
 */
function createSender(cfg, opts = {}) {
  const origin = opts.origin || cfg.origin;
  const sleep = opts.sleep || ((ms) => new Promise((r) => setTimeout(r, ms)));
  const clock = opts.now || Date.now;
  const retries = Number.isInteger(opts.retries) ? opts.retries : 3;
  const timeoutMs = opts.timeoutMs || 15000;
  const baseDelay = opts.baseDelayMs || 500;

  let session = null;
  let inflight = 0;
  let jwt = { value: '', at: 0 };

  function providerToken(force = false) {
    const t = clock();
    if (!force && jwt.value && t - jwt.at < TOKEN_REFRESH_MS) return jwt.value;
    const head = b64url(JSON.stringify({ alg: 'ES256', kid: cfg.keyId }));
    const claims = b64url(JSON.stringify({ iss: cfg.teamId, iat: Math.floor(t / 1000) }));
    //  JWS ِ ES256 امضای خامِ r‖s (۶۴ بایت) می‌خواهد، نه DER
    const sig = sign('sha256', Buffer.from(`${head}.${claims}`), { key: cfg.key, dsaEncoding: 'ieee-p1363' });
    jwt = { value: `${head}.${claims}.${b64url(sig)}`, at: t };
    return jwt.value;
  }

  function connect() {
    if (session && !session.closed && !session.destroyed) return session;
    const s = http2.connect(origin);
    //  خطای نشست فقط نشست را می‌بندد؛ درخواستِ در جریان خطای خودش را می‌گیرد و دوباره می‌رود
    s.on('error', () => {});
    s.on('goaway', () => { if (session === s) session = null; });
    s.on('close', () => { if (session === s) session = null; });
    s.setTimeout(IDLE_MS, () => s.close());
    s.unref();
    session = s;
    return s;
  }

  function drop(s) {
    if (session === s) session = null;
    try { s.destroy(); } catch { /* بسته است */ }
  }

  function once(token, json, headers) {
    return new Promise((resolve, reject) => {
      const s = connect();
      inflight += 1;
      s.ref();
      let settled = false;
      const done = (fn, v) => {
        if (settled) return;
        settled = true;
        inflight -= 1;
        if (!inflight && !s.destroyed) s.unref();
        fn(v);
      };
      let req;
      try {
        req = s.request({
          ':method': 'POST',
          ':path': `/3/device/${token}`,
          'content-type': 'application/json',
          ...headers,
        });
      } catch (err) {
        drop(s);
        done(reject, err);
        return;
      }
      let status = 0;
      let apnsId = '';
      let retryAfter = 0;
      const chunks = [];
      let size = 0;
      req.setTimeout(timeoutMs, () => {
        req.close(http2.constants.NGHTTP2_CANCEL);
        done(reject, new Error('apns_timeout'));
      });
      req.on('response', (h) => {
        status = Number(h[':status']) || 0;
        apnsId = String(h['apns-id'] || '');
        retryAfter = Number(h['retry-after']) || 0;
      });
      req.on('data', (c) => { size += c.length; if (size <= 4096) chunks.push(c); });
      req.on('error', (err) => { drop(s); done(reject, err); });
      //  «close» همیشه پس از پایانِ بدنه می‌آید؛ بی پاسخ ⇒ جریان بریده شد
      req.on('close', () => {
        if (!status) { done(reject, new Error('apns_stream_closed')); return; }
        let reason = '';
        try { reason = String(JSON.parse(Buffer.concat(chunks).toString('utf8')).reason || ''); } catch { /* بی بدنه */ }
        done(resolve, { status, apnsId, reason: reason.slice(0, 80), retryAfter });
      });
      req.end(json);
    });
  }

  /**
   * یک اعلان به یک دستگاه.
   * @returns {Promise<{ok:boolean, status:number, reason:string, dead?:boolean, fatal?:boolean, attempts:number}>}
   */
  async function send(token, message = {}) {
    if (!validToken(token)) return { ok: false, dead: true, status: 0, reason: 'BadTokenFormat', attempts: 0 };
    const json = payloadOf(message);
    const expiry = Math.floor(clock() / 1000) + (Number.isFinite(message.ttlSec) ? message.ttlSec : DEFAULT_TTL_SEC);
    const headers = {
      'apns-topic': cfg.bundleId,
      'apns-push-type': 'alert',
      'apns-priority': '10',
      'apns-expiration': String(Math.max(0, expiry)),
    };
    const collapse = String(message.collapseId || '');
    if (collapse && Buffer.byteLength(collapse) <= 64) headers['apns-collapse-id'] = collapse;

    let refreshed = false;
    let last = { status: 0, reason: '' };
    for (let attempt = 0; attempt <= retries; attempt++) {
      if (attempt > 0) {
        const wait = last.retryAfter
          ? Math.min(last.retryAfter * 1000, 30000)
          : Math.min(baseDelay * 2 ** (attempt - 1), 8000) * (0.8 + Math.random() * 0.4);
        await sleep(Math.round(wait));
      }
      let r;
      try {
        r = await once(token, json, { ...headers, authorization: `bearer ${providerToken()}` });
      } catch (err) {
        //  قطعِ اتصال، مهلت، GOAWAY ⇒ نشستِ تازه و دوباره
        last = { status: 0, reason: String(err && err.code ? err.code : err && err.message ? err.message : 'network').slice(0, 60) };
        continue;
      }
      last = r;
      if (r.status === 200) return { ok: true, status: 200, reason: '', apnsId: r.apnsId, attempts: attempt + 1 };
      if (r.status === 410 || (r.status === 400 && r.reason === 'BadDeviceToken')) {
        return { ok: false, dead: true, status: r.status, reason: r.reason || 'Unregistered', attempts: attempt + 1 };
      }
      if (r.status === 403 && r.reason === 'ExpiredProviderToken' && !refreshed && clock() - jwt.at >= TOKEN_MIN_AGE_MS) {
        refreshed = true;
        providerToken(true);
        attempt -= 1; // تازه کردنِ توکن تلاشِ شبکه‌ای حساب نمی‌شود
        continue;
      }
      if (r.status === 403) return { ok: false, fatal: true, status: 403, reason: r.reason, attempts: attempt + 1 };
      if (r.status === 429 || r.status >= 500) {
        if (r.reason === 'Shutdown' && session) drop(session);
        continue;
      }
      //  ۴۰۰/۴۱۳ ِ دیگر (بدنه، سرآیند، topic) ⇒ دوباره فرستادن همان را می‌دهد
      return { ok: false, status: r.status, reason: r.reason, attempts: attempt + 1 };
    }
    return { ok: false, status: last.status, reason: last.reason || 'unavailable', attempts: retries + 1 };
  }

  function close() {
    if (session) { try { session.close(); } catch { /* بسته است */ } }
    session = null;
  }

  return { send, close, env: cfg.env, providerToken };
}

/* --------------------------- فرستندهٔ محیط --------------------------- */

let cached = { print: null, sender: null, state: { ok: false, reason: 'off' } };
let override; // undefined ⇒ محیط
const warned = new Set();

function fingerprint(env) {
  const h = createHash('sha256');
  for (const k of [...REQUIRED, 'APNS_ENV']) h.update(`${k}=${env[k] || ''}\n`);
  return h.digest('hex');
}

/** فرستندهٔ ساخته‌شده از محیط، یا null (خاموش/خراب). تغییرِ محیط ⇒ فرستندهٔ تازه. */
function sender(env = process.env) {
  if (override !== undefined) return override;
  const print = fingerprint(env);
  if (cached.print === print) return cached.sender;
  if (cached.sender) cached.sender.close();
  const r = readConfig(env);
  cached = { print, sender: r.ok ? createSender(r.cfg) : null, state: r.ok ? { ok: true, env: r.cfg.env } : r };
  if (!r.ok && r.reason !== 'off' && !warned.has(r.reason)) {
    warned.add(r.reason);
    console.warn(`[apns] خاموش: ${r.reason}${r.missing ? ` (${r.missing.join('، ')})` : ''}`);
  }
  return cached.sender;
}

/** برای پنلِ مدیر: فقط «روشن؟، کدام محیط، چرا خاموش» — هیچ مقداری از کلید */
function status(env = process.env) {
  if (override !== undefined) return { configured: !!override, env: override ? override.env : '', reason: override ? '' : 'off' };
  sender(env);
  const s = cached.state;
  return { configured: !!s.ok, env: s.ok ? s.env : '', reason: s.ok ? '' : s.reason };
}

/**
 * ⛔ فقط آزمون: فرستنده‌ای روی سرورِ ساختگی (یا null = «خاموش»)؛ `undefined` ⇒ برگشت به محیط.
 */
function useSender(s) { override = s; }

module.exports = { readConfig, createSender, sender, status, useSender, payloadOf, validToken, HOSTS };
