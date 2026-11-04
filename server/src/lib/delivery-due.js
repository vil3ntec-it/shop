'use strict';
/**
 * «نقدِ موردِ انتظار» ِ مأموریتِ تحویل (DLV-11/12، امانتِ نقدِ پیک) = بدهیِ **امروزِ** سفارش، نه عکسِ لحظهٔ سپردن.
 *
 * سپردن (`delivery.assign`) همین [dueOf] را می‌نشاند. هر نویسنده‌ای که پس از سپردن جمع یا پرداختِ سفارش را عوض
 * می‌کند — ویرایش (ORD-14)، افزودنِ قلم (CHK-21)، پرداختِ آنلاین (PAY-21) — درونِ همان تراکنش و پس از قفلِ ردیفِ
 * سفارش [syncIn] را می‌خواند تا کارتِ پیک، سقفِ نقد، «نقدِ من»، میز و پیش‌نمایشِ تسویه و سنجشِ «گرفتم» ِ «تحویل شد»
 * همه یک بدهی را بخوانند. پیش از این، سفارشی که پس از سپردن با حساب‌پی پرداخت می‌شد هنوز «جمع را بگیر» به پیک
 * می‌گفت (پرداختِ دوبارهٔ مشتری) و ویرایشِ پس از سپردن مبلغِ کهنه را می‌خواست.
 * ⛔ فقط مأموریتِ زنده: نقدِ گرفته‌شدهٔ «تحویل شد» واقعیت است و دست نمی‌خورد. نسخه بالا می‌رود تا برنامهٔ پیک تازه شود.
 */
const { LIVE } = require('./delivery-states');

/** بدهیِ نقدِ سفارش: فقط «نقد هنگامِ تحویل» ِ پرداخت‌نشده ⇒ جمعِ سفارش؛ وگرنه صفر. */
const dueOf = (o) => (o.payment_method === 'cod' && o.payment_status !== 'paid' ? Number(o.total_minor) : 0);

/** @returns {Promise<number>} شمارِ مأموریت‌های زندهٔ این سفارش که بدهی‌شان عوض شد */
async function syncIn(c, shopId, orderId, at) {
  const o = (await c.query(
    'SELECT payment_method, payment_status, total_minor FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, orderId]
  )).rows[0];
  if (!o) return 0;
  return (await c.query(
    `UPDATE delivery_assignments SET expected_collect_minor=$3, version=version+1, updated_at=$4
      WHERE shop_id=$1 AND order_id=$2 AND status = ANY($5::text[]) AND expected_collect_minor <> $3`,
    [shopId, orderId, dueOf(o), at, LIVE]
  )).rowCount;
}

module.exports = { dueOf, syncIn };
