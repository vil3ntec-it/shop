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

async function issue({ kind, subjectId, deviceId = null, ttlMs }) {
  const token = generateToken();
  const issuedAt = now();
  const expiresAt = issuedAt + ttlMs;
  await query(
    `INSERT INTO tokens (token_hash, kind, subject_id, device_id, issued_at, expires_at)
     VALUES ($1,$2,$3,$4,$5,$6)`,
    [hashToken(token), kind, subjectId, deviceId, issuedAt, expiresAt]
  );
  return { token, expiresAt };
}

/** توکن را بررسی می‌کند؛ نامعتبر/منقضی/باطل → null. */
async function verify(token, kind) {
  if (typeof token !== 'string' || token.length < 20) return null;
  const row = await one(
    'SELECT * FROM tokens WHERE token_hash = $1 AND kind = $2',
    [hashToken(token), kind]
  );
  if (!row) return null;
  if (row.revoked_at) return null;
  if (Number(row.expires_at) < now()) return null;
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
