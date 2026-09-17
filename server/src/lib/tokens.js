'use strict';
/**
 * توکن‌های نشست — مات (opaque) و تصادفی.
 *
 * فقط SHA-256 توکن در دیتابیس ذخیره می‌شود؛ اگر دیتابیس لو برود نمی‌توان
 * از روی آن توکن معتبر ساخت. برخلاف JWT، این توکن‌ها فوراً قابل باطل
 * کردن‌اند: خروج از حساب یا حذف شاگرد بلافاصله اثر می‌کند.
 */
const { randomBytes, createHash, timingSafeEqual } = require('crypto');
const { query, one, now } = require('../db');

function generateToken() { return randomBytes(32).toString('base64url'); }
function hashToken(token) { return createHash('sha256').update(String(token)).digest('hex'); }

/**
 *  @param appVersion نسخه‌ی برنامه‌ای که این نشست را می‌سازد.
 *
 *  خالی یعنی «نگفت» — نسخه‌های امروزِ دستِ کاربر چیزی نمی‌فرستند و
 *  نباید هم از کار بیفتند. شرحش سرِ `migrations/015`.
 */
async function issue({ kind, subjectId, deviceId = null, ttlMs, app = 'shop', appVersion = '' }) {
  const token = generateToken();
  const issuedAt = now();
  const expiresAt = issuedAt + ttlMs;
  await query(
    `INSERT INTO tokens (token_hash, kind, subject_id, device_id, issued_at, expires_at, app, app_version)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
    [hashToken(token), kind, subjectId, deviceId, issuedAt, expiresAt, app, String(appVersion || '').slice(0, 32)]
  );
  return { token, expiresAt, app };
}

/**
 * توکن را بررسی می‌کند؛ نامعتبر/منقضی/باطل → null.
 *
 * ── چرا `app` هم سنجیده می‌شود ────────────────────────────────────
 * خواستهٔ صاحب مخزن: «شاپ و پمپ ربطی به هم نداشته باشند، حتی یک ذره.»
 *
 * تا دیروز یک توکن هر دو بخش را باز می‌کرد. یعنی کسی که فقط دکان
 * داشت، با همان توکن می‌توانست مسیرهای پمپ را صدا بزند — و برعکس.
 * حالا توکنِ یک بخش در بخشِ دیگر **انگار اصلاً وجود ندارد**: نه
 * «دسترسی نداری»، بلکه «چنین نشستی نیست».
 *
 * `app = null` یعنی «هر بخشی» و فقط برای کارهای درونیِ خودِ سرور
 * (مثلِ باطل کردن) به کار می‌رود، نه برای مسیرها.
 */
async function verify(token, kind, app = 'shop') {
  if (typeof token !== 'string' || token.length < 20) return null;
  const row = await one(
    'SELECT * FROM tokens WHERE token_hash = $1 AND kind = $2',
    [hashToken(token), kind]
  );
  if (!row) return null;
  if (row.revoked_at) return null;
  if (Number(row.expires_at) < now()) return null;
  //  توکنِ مدیر بخش ندارد؛ پنل یکی است و بالای هر دو می‌نشیند
  if (app !== null && kind !== 'admin' && (row.app || 'shop') !== app) return null;
  return row;
}

async function revoke(token, kind) {
  const r = await query(
    'UPDATE tokens SET revoked_at = $1 WHERE token_hash = $2 AND kind = $3 AND revoked_at IS NULL',
    [now(), hashToken(token), kind]
  );
  return r.rowCount > 0;
}

/** خروج از همه‌ی دستگاه‌ها. */
async function revokeAllForSubject(subjectId, kind = null) {
  const r = kind
    ? await query('UPDATE tokens SET revoked_at=$1 WHERE subject_id=$2 AND kind=$3 AND revoked_at IS NULL', [now(), subjectId, kind])
    : await query('UPDATE tokens SET revoked_at=$1 WHERE subject_id=$2 AND revoked_at IS NULL', [now(), subjectId]);
  return r.rowCount;
}

async function revokeAllForDevice(deviceId) {
  const r = await query(
    'UPDATE tokens SET revoked_at=$1 WHERE device_id=$2 AND revoked_at IS NULL', [now(), deviceId]
  );
  return r.rowCount;
}

/** مقایسه‌ی امن (زمان‌ثابت) دو رشته. */
function safeEqual(a, b) {
  const ba = Buffer.from(String(a)), bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return timingSafeEqual(ba, bb);
}

module.exports = {
  generateToken, hashToken, issue, verify, revoke,
  revokeAllForSubject, revokeAllForDevice, safeEqual,
};
