'use strict';
/**
 * رازِ دستگاهِ شاگرد — اثباتِ «همان دستگاه» برای ورودِ دوباره با کدِ شاگرد (مهاجرتِ ۳۲۰).
 *
 * ⛔ شناسهٔ دستگاه (`device.uid`، `X-Device`) **راز نیست**: همکار در Pull و هر درخواستِ همگام‌سازی آن را می‌بیند.
 * پس `/auth/staff` عضوِ موجود را دیگر با «کد + شناسهٔ دستگاه» برنمی‌گرداند؛ دستگاه باید رازی را نشان دهد که در
 * نخستین پیوستن (یا از نشستِ بازِ خودش، `POST /api/auth/staff/device-key`) گرفته و در حافظهٔ امنِ خودش نگه داشته.
 *
 * - راز ۳۲ بایتِ تصادفی (`randomBytes`) است و فقط **یک بار** در پاسخ می‌آید؛ سرور فقط HMAC ِ آن را با رازِ سرور
 *   نگه می‌دارد (`key_hash`) — نه خودش، نه چیزی که از آن برگردد. هرگز در لاگ یا سابقه نمی‌نشیند.
 * - راز به «دکان + کاربر + شناسهٔ دستگاه» بسته است: رازِ دکان یا دستگاهِ دیگر چیزی را باز نمی‌کند.
 * - یک رازِ زنده برای هر سه‌تایی؛ رازِ تازه پیشین را باطل می‌کند (درونِ یک تراکنش).
 */
const { createHmac, randomBytes } = require('crypto');
const { one, tx, newId, now } = require('../db');

/** پرچمِ کلیدِ اضطراری — پیش‌فرض **روشن**؛ فقط `false` ِ صریح (دکان یا all) رفتارِ پیشین را برمی‌گرداند. */
const FLAG_KEY = 'auth.staff_session_guard';

/** بلندترین رازی که پذیرفته می‌شود (رازِ ما ۴۳ نویسه است) — ورودیِ بلندتر بی پرسشِ پایگاه رد */
const MAX_LEN = 128;

/** همان رازِ سرورِ کدِ شاگرد (`staff-codes.pepper`)، با پیشوندِ جدا تا HMAC ِ راز و کد هرگز هم‌دامنه نباشند */
function hashKey(secret) {
  return createHmac('sha256', require('./staff-codes').pepper()).update(`staff-device:${secret}`).digest('hex');
}

/** رازِ فرستاده از بدنه — فقط رشتهٔ ناتهیِ کوتاه؛ هر چیزِ دیگر یعنی «رازی نیامد» */
function presented(device) {
  const raw = device && typeof device === 'object' && !Array.isArray(device) ? device.key : undefined;
  if (typeof raw !== 'string') return '';
  const s = raw.trim();
  return s.length > 0 && s.length <= MAX_LEN ? s : '';
}

/** برنامه رازِ دستگاه را می‌شناسد؟ (`device.keyVersion` ≥ ۱ یا رازی فرستاده) — نسخهٔ کهنه راز نمی‌گیرد که دور بریزد */
function capable(device) {
  if (presented(device)) return true;
  const v = device && typeof device === 'object' && !Array.isArray(device) ? Number(device.keyVersion) : 0;
  return Number.isFinite(v) && v >= 1;
}

async function guardOn(shopId) {
  return require('./default-on-flag').defaultOn(shopId, FLAG_KEY);
}

/**
 * رازِ نشان‌داده درست است؟ ⇒ ردیفِ زنده (`user_id`)، وگرنه `null`. فقط با همان دکان و همان شناسهٔ دستگاه.
 */
async function verify({ shopId, deviceUid, secret }) {
  if (!shopId || !deviceUid || !secret) return null;
  const row = await one(
    `UPDATE staff_device_keys SET last_used_at=$4
      WHERE key_hash=$1 AND shop_id=$2 AND device_uid=$3 AND revoked_at IS NULL
      RETURNING id, user_id`,
    [hashKey(secret), shopId, deviceUid, now()]
  );
  return row || null;
}

/** این کاربر روی این دستگاهِ این دکان رازِ زنده دارد؟ */
async function hasLive({ shopId, userId, deviceUid }) {
  if (!shopId || !userId || !deviceUid) return false;
  const row = await one(
    `SELECT 1 AS ok FROM staff_device_keys
      WHERE shop_id=$1 AND user_id=$2 AND device_uid=$3 AND revoked_at IS NULL`,
    [shopId, userId, deviceUid]
  );
  return Boolean(row);
}

/**
 * رازِ تازه برای «دکان + کاربر + دستگاه»؛ رازِ زندهٔ پیشینِ همان سه‌تایی باطل می‌شود. خودِ راز فقط همین‌جا برمی‌گردد.
 * `onlyIfNone` ⇒ اگر رازِ زنده‌ای هست هیچ کاری نمی‌شود و `null` برمی‌گردد (برای گرفتن از نشستِ باز — هرگز چرخاندن).
 */
async function issue({ shopId, userId, deviceUid, onlyIfNone = false }) {
  if (!shopId || !userId || !deviceUid) return null;
  const secret = randomBytes(32).toString('base64url');
  const t = now();
  return tx(async (c) => {
    //  دو درخواستِ هم‌زمانِ همان دستگاه پشتِ هم، نه کنارِ هم (نمایهٔ یکتای جزئی سدِّ دوم است)
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`staff-device-key:${shopId}:${userId}:${deviceUid}`]);
    const live = (await c.query(
      `SELECT id FROM staff_device_keys WHERE shop_id=$1 AND user_id=$2 AND device_uid=$3 AND revoked_at IS NULL`,
      [shopId, userId, deviceUid]
    )).rows[0];
    if (live && onlyIfNone) return null;
    if (live) await c.query('UPDATE staff_device_keys SET revoked_at=$2 WHERE id=$1', [live.id, t]);
    await c.query(
      `INSERT INTO staff_device_keys (id, shop_id, user_id, device_uid, key_hash, created_at)
       VALUES ($1,$2,$3,$4,$5,$6)`,
      [newId('sdk'), shopId, userId, deviceUid, hashKey(secret), t]
    );
    return secret;
  });
}

module.exports = { FLAG_KEY, MAX_LEN, presented, capable, guardOn, verify, hasLive, issue, hashKey };
