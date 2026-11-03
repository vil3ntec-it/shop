'use strict';
/**
 * تماسِ پیک با مشتری (DLV-02 «دکمهٔ تماس») — از `lib/delivery.js` جدا شد تا زیرِ سقفِ اندازه بماند؛
 * رفتار همان.
 */
const { one } = require('../db');
const orders = require('./orders');
const { notFound, conflict, ApiError } = require('../middleware/errors');

/** پرچمِ جدا و پیش‌فرض خاموش: شمارهٔ کامل از درِ پیک فقط با این بیرون می‌رود. */
const CALL_FLAG = 'shop.delivery_courier_call';
/**
 * پس از «قبول» و تا پایانِ مأموریت (§۳۸: نشانی و تماسِ کامل فقط برای پیکِ سپرده‌شده و
 * فقط وقتی لازم است؛ پیش از قبول پیک فقط آن‌چه برای تصمیم لازم است را می‌بیند).
 */
const CALLABLE = Object.freeze(['assigned', 'picked_up', 'moving', 'arrived', 'failed']);

const callFlagOf = (shopId) => orders.flagOf(shopId, CALL_FLAG);

/**
 * شمارهٔ کاملِ مشتری برای همین یک تماس. ⛔ فقط مأموریتِ پذیرفته و زندهٔ خودِ این پیک،
 * فقط پیکِ فعال (پیکِ غیرفعال هنوز رویدادهایش را می‌فرستد، ولی شمارهٔ تازه نمی‌گیرد؛
 * دکان خودش زنگ می‌زند). فهرست و کارت همچنان شمارهٔ پوشیده دارند.
 */
async function callCustomer(shopId, assignmentId, actor) {
  if (!(await callFlagOf(shopId))) throw notFound('تماس از برنامهٔ پیک برای این دکان روشن نیست', 'courier_call_disabled');
  const r = await one(
    `SELECT a.id, a.order_id, a.status, a.mode, a.courier_id, o.customer_phone
       FROM delivery_assignments a JOIN online_orders o ON o.id = a.order_id
      WHERE a.shop_id=$1 AND a.id=$2`,
    [shopId, String(assignmentId || '')]
  );
  //  ⛔ مأموریتِ دیگری «پیدا نشد» است، نه «مالِ تو نیست»
  if (!r || r.mode !== 'shop_courier' || r.courier_id !== actor.courierId) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
  if (actor.active !== true) throw new ApiError(403, 'courier_inactive', 'این پیک غیرفعال است');
  if (r.status === 'requested') throw conflict('اول مأموریت را بپذیرید', 'not_accepted');
  if (!CALLABLE.includes(r.status)) throw conflict('این مأموریت تمام شده است', 'not_live');
  if (!r.customer_phone) throw conflict('مشتری شماره نداده است', 'no_phone');
  return { assignmentId: r.id, orderId: r.order_id, status: r.status, phone: r.customer_phone };
}

module.exports = { CALL_FLAG, CALLABLE, callFlagOf, callCustomer };
