'use strict';
/**
 * توکن‌های نشست — مات (opaque) و تصادفی، نه JWT.
 * فقط SHA-256 توکن در دیتابیس ذخیره می‌شود؛ اگر دیتابیس لو برود،
 * نمی‌توان از روی آن توکن معتبر ساخت.
 */
const { randomBytes, createHash, timingSafeEqual } = require('crypto');
const { getDb, now } = require('../db');

function generateToken() { return randomBytes(32).toString('base64url'); }
function hashToken(token) { return createHash('sha256').update(String(token)).digest('hex'); }

function issue({ kind, subjectId, deviceId = null, ttlMs }) {
  const token = generateToken();
  getDb().prepare(`
    INSERT INTO tokens (token_hash, kind, subject_id, device_id, issued_at, expires_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(hashToken(token), kind, subjectId, deviceId, now(), now() + ttlMs);
  return { token, expiresAt: now() + ttlMs };
}

/** توکن را بررسی می‌کند؛ در صورت نامعتبر/منقضی/باطل بودن null برمی‌گرداند. */
function verify(token, kind) {
  if (typeof token !== 'string' || token.length < 20) return null;
  const row = getDb().prepare(
    'SELECT * FROM tokens WHERE token_hash = ? AND kind = ?'
  ).get(hashToken(token), kind);
  if (!row) return null;
  if (row.revoked_at) return null;
  if (row.expires_at < now()) return null;
  return row;
}

function revoke(token, kind) {
  return getDb().prepare(
    'UPDATE tokens SET revoked_at = ? WHERE token_hash = ? AND kind = ? AND revoked_at IS NULL'
  ).run(now(), hashToken(token), kind).changes > 0;
}

/** باطل کردن همه‌ی توکن‌های یک کاربر/مدیر (خروج از همه دستگاه‌ها، تغییر رمز). */
function revokeAllForSubject(subjectId, kind = null) {
  const sql = kind
    ? 'UPDATE tokens SET revoked_at = ? WHERE subject_id = ? AND kind = ? AND revoked_at IS NULL'
    : 'UPDATE tokens SET revoked_at = ? WHERE subject_id = ? AND revoked_at IS NULL';
  const args = kind ? [now(), subjectId, kind] : [now(), subjectId];
  return getDb().prepare(sql).run(...args).changes;
}

/** باطل کردن توکن‌های وابسته به یک دستگاه (هنگام لغو دسترسی دستگاه). */
function revokeAllForDevice(deviceId) {
  return getDb().prepare(
    'UPDATE tokens SET revoked_at = ? WHERE device_id = ? AND revoked_at IS NULL'
  ).run(now(), deviceId).changes;
}

/** مقایسه‌ی امن رشته‌ها (برای مقایسه‌ی مقادیر حساس غیر توکنی). */
function safeEqual(a, b) {
  const ba = Buffer.from(String(a)), bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return timingSafeEqual(ba, bb);
}

module.exports = { generateToken, hashToken, issue, verify, revoke, revokeAllForSubject, revokeAllForDevice, safeEqual };
