'use strict';
/**
 * ورود با اپل (Sign in with Apple) — همتای `lib/google.js`.
 *
 * برنامهٔ آیفون از اپل یک identity token (JWT، RS256) می‌گیرد و همان را به سرور
 * می‌دهد. سرور امضا را با کلیدهای عمومیِ اپل (JWKS) می‌سنجد؛ گوشی نمی‌تواند ادعای
 * دروغ بکند. تصمیمِ «این چه کسی است» فقط این‌جا گرفته می‌شود.
 *
 * فرقِ اپل با گوگل: nonce. پیش از پنجرهٔ اپل، برنامه از سرور یک nonce ِ خام می‌گیرد
 * (`POST /api/auth/apple/nonce`) و SHA-256 ِ آن را به اپل می‌دهد؛ اپل همان را در
 * ادعای `nonce` ِ توکن می‌گذارد. سرور با nonce ِ خام سه چیز را می‌سنجد:
 *   ۱) امضای HMAC ِ خودِ سرور و مهلتِ ده‌دقیقه‌ای (nonce ِ ساختگی یا کهنه نه)؛
 *   ۲) `nonce` ِ توکن = SHA-256 ِ همان nonce ِ خام (توکنِ گرفته‌شده برای nonce ِ دیگر نه)؛
 *   ۳) یک‌بار مصرف: با صدورِ نشست در `idempotency_keys` نشانه می‌خورد (`consumeNonce`)،
 *      پس توکن + nonce ِ دزدیده‌شده پس از ورودِ صاحبش دیگر نشست نمی‌سازد. ۴۰۹ ِ «اثباتِ
 *      روشِ قبلی» و کدِ وصل مصرفش نمی‌کنند — گامِ دوم همان توکن را دوباره می‌فرستد.
 *
 * ⛔ بی `APPLE_CLIENT_IDS` (شناسهٔ برنامه = bundle id، از حسابِ اپلِ صاحب‌کار) خاموش است:
 * هر درخواست ۴۰۳ `apple_not_configured` و `/config` ⇐ `appleLogin: false`.
 */
const { createHash, createHmac, createPublicKey, createVerify, randomBytes, timingSafeEqual } = require('crypto');
const config = require('../config');
const { query } = require('../db');
const { badRequest, forbidden, unauthorized } = require('../middleware/errors');

const ISSUER = 'https://appleid.apple.com';
const NONCE_SCOPE = 'auth.apple.nonce';

let cache = { keys: null, expiresAt: 0 };

function b64u(s) {
  return Buffer.from(String(s).replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

function sameText(a, b) {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length === y.length && timingSafeEqual(x, y);
}

const configured = () => config.apple.clientIds.length > 0;

function assertConfigured() {
  if (!configured()) throw forbidden('ورود با اپل روی این سرور تنظیم نشده است', 'apple_not_configured');
}

async function keys() {
  const t = Date.now();
  if (cache.keys && cache.expiresAt > t) return cache.keys;
  const res = await fetch(config.apple.keysUrl, { signal: AbortSignal.timeout(10000) });
  if (!res.ok) throw new Error(`کلیدهای اپل در دسترس نیست (${res.status})`);
  const body = await res.json();
  //  اپل Cache-Control ِ روشن نمی‌دهد؛ کف یک ساعت، و کلیدِ ناشناخته کش را یک بار تازه می‌کند (پایین)
  const cc = res.headers.get('cache-control') || '';
  const m = /max-age=(\d+)/.exec(cc);
  const ttl = Math.max(3600, m ? Number(m[1]) : 3600) * 1000;
  cache = { keys: Array.isArray(body.keys) ? body.keys : [], expiresAt: t + ttl };
  return cache.keys;
}

/** کلیدِ `kid`؛ نبود ⇒ یک بار JWKS ِ تازه (اپل کلیدها را می‌چرخاند) */
async function keyOf(kid) {
  let jwk = (await keys()).find(k => k.kid === kid);
  if (!jwk && cache.keys) {
    cache = { keys: null, expiresAt: 0 };
    jwk = (await keys()).find(k => k.kid === kid);
  }
  return jwk || null;
}

/*
 *  nonce ِ سرور: `v1.<مهلت به ثانیه>.<۱۶ بایتِ تصادفی>.<HMAC>` — بی هیچ نوشتنی هنگامِ صدور
 *  (مسیرِ عمومی است؛ نوشتنِ پایگاه در هر درخواستِ بی‌نام راهِ پرکردنِ دیسک می‌شد).
 */
function nonceKey() {
  return createHash('sha256').update(`apple-nonce|${config.secrets.otp}|${config.secrets.api}`).digest();
}

function sign(body) {
  return createHmac('sha256', nonceKey()).update(body).digest('base64url');
}

function issueNonce(nowMs = Date.now()) {
  const exp = Math.floor((nowMs + config.apple.nonceTtlMs) / 1000);
  const body = `v1.${exp}.${randomBytes(16).toString('base64url')}`;
  return { nonce: `${body}.${sign(body)}`, expiresIn: Math.round(config.apple.nonceTtlMs / 1000) };
}

/** nonce ِ خامِ همین سرور و هنوز در مهلت؟ */
function checkNonce(raw, nowMs = Date.now()) {
  const parts = typeof raw === 'string' && raw.length <= 200 ? raw.split('.') : [];
  if (parts.length !== 4 || parts[0] !== 'v1') throw badRequest('nonce ِ ورود با اپل درست نیست', 'bad_apple_nonce');
  const body = parts.slice(0, 3).join('.');
  if (!sameText(sign(body), parts[3])) throw forbidden('nonce ِ ورود با اپل از این سرور نیست', 'bad_apple_nonce');
  const exp = Number(parts[1]);
  if (!Number.isFinite(exp) || exp * 1000 < nowMs) throw forbidden('مهلتِ ورود با اپل گذشت؛ دوباره بزنید', 'apple_nonce_expired');
}

/** SHA-256 ِ هگز — همان چیزی که برنامه به اپل داد و اپل در ادعای `nonce` گذاشت */
const nonceHash = (raw) => createHash('sha256').update(String(raw), 'utf8').digest('hex');

/**
 * @param {string} idToken  identity token ِ اپل
 * @param {string} rawNonce  nonce ِ خامی که همین سرور داده بود
 * @returns {Promise<{sub:string, email:string|null, emailVerified:boolean, privateEmail:boolean, name:string, nonceKey:string}>}
 */
async function verifyIdToken(idToken, rawNonce) {
  if (typeof idToken !== 'string' || idToken.length > 8192 || idToken.split('.').length !== 3) {
    throw badRequest('توکنِ اپل معتبر نیست', 'bad_apple_token');
  }
  assertConfigured();
  checkNonce(rawNonce);

  const [headB64, payloadB64, sigB64] = idToken.split('.');
  let head, payload;
  try {
    head = JSON.parse(b64u(headB64).toString('utf8'));
    payload = JSON.parse(b64u(payloadB64).toString('utf8'));
  } catch {
    throw badRequest('توکنِ اپل خوانده نشد', 'bad_apple_token');
  }
  if (!head || head.alg !== 'RS256') throw badRequest('الگوریتمِ توکن پشتیبانی نمی‌شود', 'bad_apple_token');

  const jwk = await keyOf(head.kid);
  if (!jwk) throw forbidden('کلیدِ امضای اپل شناخته نشد', 'bad_apple_token');
  const verifier = createVerify('RSA-SHA256');
  verifier.update(`${headB64}.${payloadB64}`);
  verifier.end();
  if (!verifier.verify(createPublicKey({ key: jwk, format: 'jwk' }), b64u(sigB64))) {
    throw forbidden('امضای توکنِ اپل درست نیست', 'bad_apple_token');
  }

  const nowSec = Math.floor(Date.now() / 1000);
  if (payload.iss !== ISSUER) throw forbidden('صادرکنندهٔ توکن اپل نیست', 'bad_apple_token');
  const exp = Number(payload.exp);
  const iat = Number(payload.iat);
  if (!Number.isFinite(exp) || !Number.isFinite(iat)) throw forbidden('زمانِ توکنِ اپل درست نیست', 'bad_apple_token');
  if (exp < nowSec - 60) throw forbidden('توکنِ اپل منقضی شده است', 'apple_token_expired');
  if (iat > nowSec + 300) throw forbidden('زمانِ توکنِ اپل درست نیست', 'bad_apple_token');
  //  `aud` ِ اپل یک رشته است (bundle id)؛ آرایه پذیرفته نمی‌شود
  if (typeof payload.aud !== 'string' || !config.apple.clientIds.some(id => sameText(payload.aud, id))) {
    throw forbidden('این توکن برای برنامهٔ دیگری صادر شده است', 'bad_audience');
  }
  if (!payload.sub) throw forbidden('توکنِ اپل شناسه ندارد', 'bad_apple_token');
  if (typeof payload.nonce !== 'string' || !sameText(payload.nonce.toLowerCase(), nonceHash(rawNonce))) {
    throw forbidden('توکنِ اپل برای این درخواست نیست', 'bad_apple_nonce');
  }

  const bool = (x) => x === true || x === 'true';
  return {
    sub: String(payload.sub),
    email: payload.email ? String(payload.email).toLowerCase() : null,
    emailVerified: bool(payload.email_verified),
    privateEmail: bool(payload.is_private_email),
    //  اپل نام را در توکن نمی‌گذارد (فقط بارِ نخست به خودِ برنامه می‌دهد)
    name: '',
    nonceKey: nonceHash(rawNonce),
  };
}

/**
 * یک‌بار مصرف — درست پیش از ساختن/وصل کردن و صدورِ نشست. بارِ دوم ⇒ ۴۰۱ `apple_nonce_used`.
 * نگه‌داریِ `idempotency_keys` هفت روز است، بسیار بیشتر از مهلتِ ده‌دقیقه‌ایِ nonce.
 */
async function consumeNonce(key) {
  const r = await query(
    `INSERT INTO idempotency_keys (scope, key, request_hash, created_at)
     VALUES ($1, $2, '', $3) ON CONFLICT (scope, key) DO NOTHING`,
    [NONCE_SCOPE, key, Date.now()]
  );
  if (!r.rowCount) throw unauthorized('این ورود با اپل یک بار به کار رفته است؛ دوباره بزنید', 'apple_nonce_used');
}

module.exports = { verifyIdToken, issueNonce, checkNonce, consumeNonce, nonceHash, configured, assertConfigured, keys };
