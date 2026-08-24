'use strict';
/**
 * تولید و صدور License امضاشده.
 *
 * قالب: مثل JWS فشرده →  base64url(header).base64url(payload).base64url(signature)
 * امضا روی بایت‌های ASCII رشته‌ی «header.payload» زده می‌شود.
 *
 * نکته‌ی امنیتی: هیچ تصمیمی درباره‌ی اعتبار اشتراک از ورودی کلاینت گرفته
 * نمی‌شود. تاریخ شروع/پایان و قابلیت‌ها همه از دیتابیس سرور و ساعت سرور
 * خوانده می‌شوند.
 */
const { createHash } = require('crypto');
const ck = require('./crypto-keys');
const { sanitizeFeatures, CORE_KEYS } = require('./features');

const HEADER = { alg: 'ES256', typ: 'TLIC' }; // Tohid LICense

function canonicalJson(obj) {
  // کلیدها مرتب می‌شوند تا خروجی برای یک ورودی همیشه یکسان باشد
  if (obj === null || typeof obj !== 'object') return JSON.stringify(obj);
  if (Array.isArray(obj)) return '[' + obj.map(canonicalJson).join(',') + ']';
  const keys = Object.keys(obj).sort();
  return '{' + keys.map(k => JSON.stringify(k) + ':' + canonicalJson(obj[k])).join(',') + '}';
}

/**
 * ساخت بدنه‌ی License.
 * @param {object} p
 * @param {number} p.nowMs      زمان سرور (نه زمان کلاینت)
 * @param {number} p.licenseEndsAt  پایان اعتبار خود License
 */
function buildPayload(p) {
  const features = sanitizeFeatures(p.features);
  return {
    // شناسه‌ها
    lid:  p.licenseId,
    ver:  p.version,
    uid:  p.userId,
    did:  p.deviceId,
    duid: p.deviceUid,
    sid:  p.subscriptionId,
    kid:  p.keyId,

    // اعتبارسنجی
    iss:  p.issuer,
    aud:  p.audience,

    // زمان‌ها — همه epoch میلی‌ثانیه UTC، تعیین‌شده توسط سرور
    iat:  p.nowMs,          // زمان صدور (ساعت معتبر سرور)
    nbf:  p.startsAt,       // پیش از این تاریخ معتبر نیست
    exp:  p.licenseEndsAt,  // پایان اعتبار این License
    sub_ends: p.subEndsAt,  // پایان خود اشتراک
    grace_ms: Math.max(0, p.graceDays || 0) * 24 * 60 * 60 * 1000,
    tz:   p.timezone,

    // دسترسی‌ها
    plan: p.plan,
    feat: features,          // قابلیت‌های اشتراکی
    core: CORE_KEYS,         // قابلیت‌های همیشه‌آزاد
    maxdev: p.maxDevices,

    // اثر انگشت دستگاه — برنامه بررسی می‌کند License مال همین دستگاه باشد
    dfp:  p.deviceFingerprint || '',
  };
}

/** امضای بدنه و ساخت رشته‌ی نهایی License. */
async function signLicense(privateKey, payload) {
  const h = Buffer.from(canonicalJson(HEADER), 'utf8').toString('base64url');
  const b = Buffer.from(canonicalJson(payload), 'utf8').toString('base64url');
  const signingInput = Buffer.from(`${h}.${b}`, 'ascii');
  const sig = await ck.sign(privateKey, signingInput);
  return `${h}.${b}.${sig.toString('base64url')}`;
}

/**
 * بررسی امضا و ساختار License. سمت سرور برای تست/تشخیص استفاده می‌شود؛
 * سمت کلاینت همین منطق با WebCrypto مرورگر پیاده شده است.
 */
async function verifyLicense(publicKey, token) {
  if (typeof token !== 'string') return { ok: false, reason: 'format' };
  const parts = token.split('.');
  if (parts.length !== 3) return { ok: false, reason: 'format' };
  const [h, b, s] = parts;
  let header, payload;
  try {
    header = JSON.parse(Buffer.from(h, 'base64url').toString('utf8'));
    payload = JSON.parse(Buffer.from(b, 'base64url').toString('utf8'));
  } catch { return { ok: false, reason: 'format' }; }
  if (!header || header.alg !== 'ES256' || header.typ !== 'TLIC') {
    return { ok: false, reason: 'header' };
  }
  const okSig = await ck.verify(
    publicKey,
    Buffer.from(s, 'base64url'),
    Buffer.from(`${h}.${b}`, 'ascii')
  );
  if (!okSig) return { ok: false, reason: 'signature' };
  return { ok: true, header, payload };
}

/** هش اثر انگشت دستگاه — خام ذخیره نمی‌شود. */
function fingerprintHash(raw) {
  return createHash('sha256').update(String(raw || '')).digest('hex').slice(0, 32);
}

/**
 * محاسبه‌ی پایان اعتبار License:
 *   کوتاه‌ترینِ (پایان اشتراک + مهلت) و (اکنون + TTL).
 * TTL کوتاه‌تر یعنی لغو دسترسی سریع‌تر اثر می‌کند، ولی کاربر باید زودتر
 * دوباره آنلاین شود. TTL خالی = تا پایان اشتراک آفلاین کار می‌کند.
 */
function computeLicenseEnd({ nowMs, subEndsAt, graceDays, licenseTtlDays }) {
  const graceMs = Math.max(0, graceDays || 0) * 24 * 60 * 60 * 1000;
  const hardEnd = subEndsAt + graceMs;
  if (licenseTtlDays === null || licenseTtlDays === undefined || licenseTtlDays <= 0) {
    return hardEnd;
  }
  const ttlEnd = nowMs + licenseTtlDays * 24 * 60 * 60 * 1000;
  return Math.min(hardEnd, ttlEnd);
}

module.exports = { HEADER, canonicalJson, buildPayload, signLicense, verifyLicense, fingerprintHash, computeLicenseEnd };
