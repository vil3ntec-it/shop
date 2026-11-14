'use strict';
/**
 * S44 (د۱۸) — بازیابیِ کارهای همگام‌نشدهٔ **دستگاهِ گم‌شده** از پشتیبانش به دفترِ دکان.
 *
 * دستگاهِ گم‌شده را صاحب غیرفعال می‌کند (STF-10، `device-block.disable`) ⇒ push ِ بعدیِ خودش رد. فروشِ آفلاینی که هنوز در
 * صفش بود فقط در پشتیبانِ همان گوشی می‌ماند، و «بازیابی هرگز op نمی‌سازد» (S143) ⇒ گم. حالا پشتیبانِ برنامه صفِ نرفتهٔ
 * دستگاه را هم دارد (`pending-ops.json` ِ `BackupBundle`، با شناسهٔ همگام‌سازیِ همان دستگاه)، و صاحب از برنامهٔ خودش همان
 * opها را — **همان op_id و همان فیلدها، `$inc` هم** — به این در می‌فرستد:
 *
 *   POST /api/shop/devices/:id/replay  { deviceId: <شناسهٔ همگام‌سازی>, ops: [...] (≤ ۵۰۰) }   (`routes/shop-staff.js`)
 *     ⇒ { results: [{ op_id, status, reason? }], counts: { applied, duplicate, rejected } }
 *
 * opها از همان زنجیرهٔ زندهٔ `POST /api/sync/v1/push` (`conflict-inbox.pushV1`، نه کپی) با **نقشِ صاحب** و **دستگاهِ
 * سازنده** می‌نشینند: هر نگهبانِ امروز (نقش، سند، موجودی، زنجیره) همان؛ op ِ از پیش رسیده «تکراری» (یکتاییِ `op_id`)؛ دستگاهِ
 * سازنده (غیرفعال) چیزی pull نمی‌کند و بقیهٔ دستگاه‌ها مثلِ هر op ِ دیگری می‌گیرند.
 *
 * ⛔ پرچمِ `shop.lost_device_replay` (`live_config`، پیش‌فرض خاموش) ⇒ ۴۰۴ پیش از بدنه. ⛔ فقط `members.manage` (همان
 *    غیرفعال کردنِ دستگاه). ⛔ دستگاه باید از همین دکان و **غیرفعال** باشد (دستگاهِ زنده خودش می‌فرستد) و شناسهٔ
 *    همگام‌سازی همان دستگاه (`device_uid` یا `<device_uid>-…` ِ `SyncReplica`). ⛔ سقفِ نرخِ دکان؛ سابقهٔ
 *    `lost_device.replayed` فقط با شمارش‌ها.
 */
const { one } = require('../db');
const audit = require('./audit');
const { flagOf } = require('./orders');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const FLAG = 'shop.lost_device_replay';
const MAX_OPS = 500;
const ID_RE = /^[A-Za-z0-9_.:-]{1,80}$/;

async function requireOn(shopId) {
  if (!(await flagOf(shopId, FLAG))) throw notFound('بازیابیِ دستگاهِ گم‌شده روشن نیست', 'lost_device_replay_off');
}

/** شکلِ درخواست ⇒ `{ device, deviceId, ops }`؛ بدشکل ⇒ ۴۰۰ */
function parse(id, body) {
  const b = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
  const device = typeof id === 'string' && ID_RE.test(id) ? id : '';
  const deviceId = typeof b.deviceId === 'string' && ID_RE.test(b.deviceId) ? b.deviceId : '';
  const ops = Array.isArray(b.ops) ? b.ops : null;
  if (!device || !deviceId) throw badRequest('دستگاه درست نیست', 'bad_device');
  if (!ops || !ops.length || ops.length > MAX_OPS) throw badRequest('فهرستِ کارها درست نیست', 'bad_ops');
  return { device, deviceId, ops };
}

async function replay(shopId, actor, { device, deviceId, ops }, ip = '') {
  const dev = await one('SELECT id, device_uid, disabled_at, disabled_shop_id FROM devices WHERE id=$1', [device]);
  const mine = dev && (dev.disabled_shop_id === shopId || (await one(
    "SELECT 1 AS x FROM shop_members m JOIN devices d ON d.user_id = m.user_id WHERE d.id=$1 AND m.shop_id=$2 LIMIT 1", [device, shopId])));
  if (!dev || !mine) throw notFound('دستگاه پیدا نشد', 'device_not_found');
  if (!dev.disabled_at) throw conflict('اول دستگاهِ گم‌شده را غیرفعال کنید', 'device_not_disabled');
  const uid = String(dev.device_uid || '');
  if (!uid || !(deviceId === uid || deviceId.startsWith(uid + '-'))) throw badRequest('این پشتیبان مالِ همین دستگاه نیست', 'device_mismatch');
  const inbox = require('./conflict-inbox');
  const ctx = await inbox.syncCtx(shopId, actor);
  const results = await inbox.pushV1(ctx, deviceId, ops, ip);
  const counts = { applied: 0, duplicate: 0, rejected: 0 };
  for (const r of results) {
    if (r && r.status === 'applied') counts.applied++;
    else if (r && r.status === 'duplicate') counts.duplicate++;
    else counts.rejected++;
  }
  await audit.log({
    shopId, userId: actor.userId, action: 'lost_device.replayed', targetType: 'device', targetId: device,
    detail: { ...counts, ops: ops.length }, ip,
  });
  return { results, counts };
}

module.exports = { FLAG, MAX_OPS, requireOn, parse, replay };
