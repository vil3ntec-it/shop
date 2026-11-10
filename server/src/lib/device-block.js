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
const { badRequest, notFound, conflict } = require('../middleware/errors');

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

const OF_SHOP = `FROM devices d
       JOIN shop_members m ON m.user_id = d.user_id AND m.shop_id=$1 AND m.status <> 'removed'
       JOIN users u ON u.id = d.user_id`;

/**
 * دستگاه‌های اعضای این دکان (عضوِ برداشته‌شده نه). `page` (‎{ limit, offset }‎، پنلِ مدیر) ⇒ ترتیبِ پایدارِ «ساخت + شناسه»
 * تا صفحه‌ها با تازه شدنِ `last_seen_at` جابه‌جا نشوند؛ بی آن همان فهرستِ کاملِ امروز.
 */
async function listForShop(shopId, page = null) {
  const rows = await many(
    `SELECT d.id, d.name, d.platform, d.status, d.created_at, d.last_seen_at,
            d.disabled_at, d.blocked_attempts, d.last_blocked_at,
            m.user_id, m.role, u.name AS user_name
       ${OF_SHOP}
      ${page ? 'ORDER BY d.created_at ASC, d.id ASC LIMIT $2 OFFSET $3' : 'ORDER BY d.last_seen_at DESC NULLS LAST'}`,
    page ? [shopId, page.limit, page.offset] : [shopId]
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

/** شمارِ همان فهرست (برای صفحه‌بندی). */
async function countForShop(shopId) {
  return Number((await one(`SELECT COUNT(*)::int AS n ${OF_SHOP}`, [shopId])).n);
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

/** دستگاه را دکانِ **دیگری** انداخته که هنوز همین کاربر را دارد؟ (کاربرِ چنددکانی) */
async function heldByOtherShop(dev, shopId) {
  if (dev.disabled_at === null || dev.disabled_at === undefined || !dev.disabled_shop_id || dev.disabled_shop_id === shopId) return false;
  return Boolean(await one(
    `SELECT 1 FROM shop_members WHERE shop_id=$1 AND user_id=$2 AND status <> 'removed'`, [dev.disabled_shop_id, dev.user_id]));
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
  //  ⛔ انداخته‌شده به دستِ دکانِ دیگر: نشست‌ها باز هم باطل، ولی «انداخته به دستِ» همان دکان می‌ماند — وگرنه این دکان با
  //  انداختنِ دوباره صاحبِ نشان می‌شد و بعد برش می‌گرداند (دورزدنِ `disabled_by_other_shop`)
  const keep = await heldByOtherShop(dev, shopId);
  const t = keep ? Number(dev.disabled_at) : now();
  if (!keep) {
    await query(
      `UPDATE devices SET status='revoked', disabled_at=$2, disabled_by=$3, disabled_shop_id=$4 WHERE id=$1`,
      [dev.id, t, String(byId || ''), shopId]
    );
  }
  const sessions = await tokens.revokeAllForDevice(dev.id);
  //  O-01: دستگاهِ از کار افتاده پوش هم نمی‌گیرد
  await require('./push').revokeDevice({ deviceId: dev.id, reason: 'device_disabled' });
  await audit.log({
    shopId, actorType, userId: actorType === 'user' ? byId : '', action: 'device.disabled',
    targetType: 'device', targetId: dev.id,
    detail: { owner: dev.user_id, sessions, reason: String(reason || '').slice(0, 200), by: byId }, ip,
  });
  return { id: dev.id, disabledAt: t, sessionsRevoked: sessions };
}

/**
 * برگرداندنِ دستگاهِ پیداشده — نشست‌ها برنمی‌گردند؛ دوباره وارد می‌شود.
 * ⛔ دستگاهی را که دکانِ **دیگری** (کاربرِ عضوِ هر دو) انداخته، این دکان برنمی‌گرداند (۴۰۹ `disabled_by_other_shop`) —
 *   مگر آن دکان دیگر این کاربر را نداشته باشد (وگرنه راه برای همیشه بسته می‌ماند).
 * ⛔ نوشتن شرطی (`disabled_at IS NOT NULL`) ⇒ دو برگرداندنِ هم‌زمان یک سابقه، دومی همان ۴۰۰.
 * `reason` (پنلِ مدیر) فقط وقتی آمده در سابقه می‌نشیند؛ سابقهٔ صاحبِ دکان همان پیشین.
 */
async function enable(shopId, deviceId, { byId = '', actorType = 'user', ip = '', reason = '' } = {}) {
  const dev = await deviceOfShop(shopId, deviceId);
  const notDisabled = () => badRequest('این دستگاه از سوی دکان از کار نیفتاده است', 'device_not_disabled');
  if (dev.disabled_at === null || dev.disabled_at === undefined) throw notDisabled();
  if (await heldByOtherShop(dev, shopId)) {
    throw conflict('این دستگاه را دکانِ دیگری از کار انداخته است؛ فقط همان دکان برش می‌گرداند', 'disabled_by_other_shop');
  }
  const r = await query(
    `UPDATE devices SET status='active', disabled_at=NULL, disabled_by=NULL, disabled_shop_id=NULL
      WHERE id=$1 AND disabled_at IS NOT NULL`,
    [dev.id]
  );
  if (!r.rowCount) throw notDisabled();
  const why = String(reason || '').slice(0, 200);
  await audit.log({
    shopId, actorType, userId: actorType === 'user' ? byId : '', action: 'device.enabled',
    targetType: 'device', targetId: dev.id, detail: { owner: dev.user_id, by: byId, ...(why ? { reason: why } : {}) }, ip,
  });
  return { id: dev.id };
}

module.exports = { MESSAGE, CODE, explain, isDisabledRow, listForShop, countForShop, disable, enable };
