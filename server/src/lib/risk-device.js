'use strict';
/**
 * «دستگاه» ِ مشتریِ ویترین برای فهرستِ سیاه و سفید — RSK-04 (§۶.۲۱)؛ جدول در ‎migrations/840_risk_list_devices.sql‎.
 *
 *   ثبتِ ویترین (`order-rate.guard`) ─▶ keyOf(دکان، `deviceId` ِ بدنه) ─▶ matchIn ِ فهرست ─▶ rememberIn (همان تراکنش)
 *   برنامهٔ دکان ─▶ «بستنِ این دستگاه» از سفارشِ خودش ─▶ risk-lists.add ─▶ ofOrderIn (چکیدهٔ همان سفارش)
 *
 * ── آن‌چه این‌جا قفل است ─────────────────────────────────────────────
 *   • شناسه همان رشتهٔ **تصادفیِ** مرورگر (`store-device` ِ `public/store/checkout.js`) یا نصبِ برنامهٔ خرید
 *     (`ShopperMemory.withDevice`) است — نه شناسهٔ سخت‌افزار، نه چیزی که مشتری را بیرون از همین دکان نشان دهد.
 *   • سرور خودِ شناسه را هرگز نگه نمی‌دارد: فقط چکیدهٔ SHA-256 ِ «دکان + شناسه» (۳۲ هگز) — پس چکیدهٔ یک دستگاه در دو
 *     دکان یکی نیست و هیچ دکانی چکیده را نمی‌بیند (نه در فهرست، نه در نمای ریسک).
 *   • یادِ «دستگاهِ این سفارش» فقط وقتی پرچمِ `shop.order_risk` ِ همان دکان روشن است، و در همان تراکنشِ ثبت؛
 *     «حذفِ دادهٔ مشتری» (SEC-10) پاکش می‌کند (`eraseIn`).
 *   • شناسهٔ نادرست یا نبودنش ⇒ «نامعلوم» (هیچ خطا، هیچ ردیف) — دستگاه سدِ سخت نیست؛ سدِ سخت همان شمارهٔ تأییدشده است.
 */
const crypto = require('crypto');

const DEVICE_RE = /^[A-Za-z0-9_-]{8,128}$/;
const KEY_RE = /^[0-9a-f]{32}$/;

/** شناسهٔ خامِ بدنه ⇒ چکیدهٔ همین دکان؛ نادرست/نبود ⇒ '' */
function keyOf(shopId, raw) {
  if (!shopId || typeof raw !== 'string' || !DEVICE_RE.test(raw)) return '';
  return crypto.createHash('sha256').update(`risk-device\n${shopId}\n${raw}`).digest('hex').slice(0, 32);
}

/** درونِ تراکنشِ ثبت (`run`): دستگاهِ همین سفارش (دکان + کلیدِ یکتایی) — فقط با پرچمِ روشن؛ تکرار بی‌اثر */
async function rememberIn(run, shopId, clientKey, deviceKey, at) {
  if (!clientKey || !KEY_RE.test(String(deviceKey || ''))) return false;
  if (!(await require('./order-risk').flagIn(run, shopId))) return false;
  await run(
    `INSERT INTO online_order_devices (shop_id, client_key, device_key, created_at) VALUES ($1,$2,$3,$4)
     ON CONFLICT (shop_id, client_key) DO NOTHING`,
    [shopId, String(clientKey), deviceKey, at]
  );
  return true;
}

/** چکیدهٔ دستگاهِ یک سفارشِ **همین** دکان و شمارهٔ سفارش؛ سفارشِ دکانِ دیگر ⇒ null؛ بی دستگاه ⇒ `deviceKey: ''` */
async function ofOrderIn(run, shopId, orderId) {
  const r = (await run(
    `SELECT o.order_no, COALESCE(d.device_key, '') AS device_key
       FROM online_orders o
       LEFT JOIN online_order_devices d ON d.shop_id = o.shop_id AND d.client_key = o.client_key
      WHERE o.shop_id = $1 AND o.id = $2`,
    [shopId, String(orderId || '').slice(0, 40)]
  )).rows[0];
  return r ? { orderNo: Number(r.order_no), deviceKey: r.device_key } : null;
}

/** SEC-10: دستگاهِ سفارش‌های پاک‌شدهٔ همین دکان ⇒ شمارِ ردیف‌های پاک‌شده */
async function eraseIn(c, shopId, orderIds) {
  if (!orderIds.length) return 0;
  return (await c.query(
    `DELETE FROM online_order_devices d USING online_orders o
      WHERE d.shop_id = $1 AND o.shop_id = $1 AND o.id = ANY($2::text[]) AND d.client_key = o.client_key
      RETURNING d.client_key`,
    [shopId, orderIds]
  )).rows.length;
}

module.exports = { DEVICE_RE, KEY_RE, keyOf, rememberIn, ofOrderIn, eraseIn };
