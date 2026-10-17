'use strict';
/**
 * دستگاهِ گم‌شده — STF-10 و سناریوی S44.
 *
 * صاحب دکان دستگاهِ یکی از اعضای دکانش را از کار می‌اندازد:
 *   • `devices.status = 'revoked'` — همان سدی که امروز هر مسیری می‌شناسد؛
 *   • همهٔ نشست‌های آن دستگاه باطل می‌شوند (`tokens.revokeAllForDevice`)؛
 *   • هر درخواستِ بعدیِ آن دستگاه (همگام‌سازی هم) به‌جای «نشست منقضی شد»
 *     خطای روشنِ `403 device_disabled` می‌گیرد و شمرده می‌شود
 *     (`blocked_attempts`، `last_blocked_at`) — «رد و ثبت».
 *
 * ⛔ دستگاهی که همین درخواست از آن آمده از کار نمی‌افتد (صاحب دکان خودش را
 * بیرون نمی‌اندازد)، و دستگاهِ کسی که عضوِ این دکان نیست هم دست نمی‌خورد.
 */
const tokens = require('./tokens');
const audit = require('./audit');
const { one, many, query, now } = require('../db');
const { badRequest, notFound } = require('../middleware/errors');

const MESSAGE = 'این دستگاه از سوی صاحب دکان غیرفعال شده است. برای بازگشت با صاحب دکان تماس بگیرید.';
const CODE = 'device_disabled';

/** شمردنِ تلاشِ ردشده — بی‌صدا شکست می‌خورد تا خودِ پاسخ را خراب نکند */
function countAttempt(deviceId) {
  return query(
    'UPDATE devices SET blocked_attempts = blocked_attempts + 1, last_blocked_at=$2 WHERE id=$1',
    [deviceId, now()]
  ).catch(() => {});
}

/**
 * توکنی که پذیرفته نشد، مالِ دستگاهِ ازکارافتاده است؟
 * فقط در مسیرِ شکست صدا زده می‌شود (یک پرسشِ کلیدی روی `token_hash`).
 */
async function explain(token) {
  if (typeof token !== 'string' || token.length < 20) return false;
  const row = await one(
    `SELECT d.id, d.disabled_at FROM tokens t JOIN devices d ON d.id = t.device_id
      WHERE t.token_hash=$1 LIMIT 1`,
    [tokens.hashToken(token)]
  );
  if (!row || row.disabled_at === null || row.disabled_at === undefined) return false;
  await countAttempt(row.id);
  return true;
}

/** ردیفِ دستگاه از کار افتاده است؟ (توکن هنوز معتبر ولی دستگاه بسته) */
async function isDisabledRow(dev) {
  if (!dev || dev.disabled_at === null || dev.disabled_at === undefined) return false;
  await countAttempt(dev.id);
  return true;
}

/** دستگاه‌های اعضای این دکان (عضوِ برداشته‌شده نه) */
async function listForShop(shopId) {
  const rows = await many(
    `SELECT d.id, d.name, d.platform, d.status, d.created_at, d.last_seen_at,
            d.disabled_at, d.blocked_attempts, d.last_blocked_at,
            m.user_id, m.role, u.name AS user_name
       FROM devices d
       JOIN shop_members m ON m.user_id = d.user_id AND m.shop_id=$1 AND m.status <> 'removed'
       JOIN users u ON u.id = d.user_id
      ORDER BY d.last_seen_at DESC NULLS LAST`,
    [shopId]
  );
  return rows.map((r) => ({
    id: r.id, name: r.name || '', platform: r.platform || '', status: r.status,
    createdAt: Number(r.created_at), lastSeenAt: r.last_seen_at === null ? null : Number(r.last_seen_at),
    disabledAt: r.disabled_at === null ? null : Number(r.disabled_at),
    blockedAttempts: Number(r.blocked_attempts || 0),
    lastBlockedAt: r.last_blocked_at === null ? null : Number(r.last_blocked_at),
    member: { userId: r.user_id, role: r.role, name: r.user_name || '' },
  }));
}

async function deviceOfShop(shopId, deviceId) {
  const dev = await one(
    `SELECT d.* FROM devices d
       JOIN shop_members m ON m.user_id = d.user_id AND m.shop_id=$2 AND m.status <> 'removed'
      WHERE d.id=$1`,
    [String(deviceId || ''), shopId]
  );
  if (!dev) throw notFound('این دستگاه مالِ اعضای این دکان نیست', 'device_not_found');
  return dev;
}

/**
 * از کار انداختن. `currentDeviceId` = دستگاهِ همین درخواست (اگر بود).
 * @param {{actorType?: string}} [opts]  پنل: actorType='admin'
 */
async function disable(shopId, deviceId, { byId = '', currentDeviceId = null, reason = '', actorType = 'user', ip = '' } = {}) {
  const dev = await deviceOfShop(shopId, deviceId);
  if (currentDeviceId && dev.id === currentDeviceId) {
    throw badRequest('دستگاهی را که با آن کار می‌کنید نمی‌شود از کار انداخت', 'current_device');
  }
  const t = now();
  await query(
    `UPDATE devices SET status='revoked', disabled_at=$2, disabled_by=$3, disabled_shop_id=$4 WHERE id=$1`,
    [dev.id, t, String(byId || ''), shopId]
  );
  const sessions = await tokens.revokeAllForDevice(dev.id);
  await audit.log({
    shopId, actorType, userId: actorType === 'user' ? byId : '', action: 'device.disabled',
    targetType: 'device', targetId: dev.id,
    detail: { owner: dev.user_id, sessions, reason: String(reason || '').slice(0, 200), by: byId }, ip,
  });
  return { id: dev.id, disabledAt: t, sessionsRevoked: sessions };
}

/** برگرداندنِ دستگاهِ پیداشده — نشست‌ها برنمی‌گردند؛ دوباره وارد می‌شود */
async function enable(shopId, deviceId, { byId = '', actorType = 'user', ip = '' } = {}) {
  const dev = await deviceOfShop(shopId, deviceId);
  if (dev.disabled_at === null || dev.disabled_at === undefined) {
    throw badRequest('این دستگاه از سوی دکان از کار نیفتاده است', 'device_not_disabled');
  }
  await query(
    `UPDATE devices SET status='active', disabled_at=NULL, disabled_by=NULL, disabled_shop_id=NULL WHERE id=$1`,
    [dev.id]
  );
  await audit.log({
    shopId, actorType, userId: actorType === 'user' ? byId : '', action: 'device.enabled',
    targetType: 'device', targetId: dev.id, detail: { owner: dev.user_id, by: byId }, ip,
  });
  return { id: dev.id };
}

module.exports = { MESSAGE, CODE, explain, isDisabledRow, listForShop, disable, enable };
