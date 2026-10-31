'use strict';
/**
 * NTF-08 — پیگیریِ بازپرداخت برای مشتریِ ویترین (کلیدِ `refund` در `GET /api/store/:slug/orders/:token`).
 *
 *   پرچم ── `shop.orders.refund_tracking` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش ⇒ کلید نیست،
 *           پاسخِ پیگیری بیت‌به‌بیت همان پیشین).
 *   منبع ── فقط دفترِ بازپرداختِ دکان (`online_order_refunds`، #255) و خطِ زمانیِ سفارش؛ این‌جا هیچ
 *           نوشتنی نیست.
 *   نما ─── `null` تا درخواستِ برگشتی نیامده و ردیفی در دفتر نیست؛ وگرنه
 *           `{status, requestedAt, refundedMinor, remainingMinor, currency, entries, updatedAt}`:
 *             • `requested`  — «درخواستِ برگشت» ثبت شد، هنوز پولی برنگشته
 *             • `in_progress`— بخشی برگشته و دکان هنوز نبسته
 *             • `partial` / `full` — بسته شد (بازپرداختِ بخشی / کامل)
 *           هر ردیف فقط مبلغ، روش، زمان و «پایانی» — ⛔ نه دلیلِ نوشتهٔ کارمند، نه کننده.
 */
const db = require('../db');
const orders = require('./orders');
const refunds = require('./order-refunds');

const FLAG_KEY = 'shop.orders.refund_tracking';
const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);

const CLOSED = Object.freeze({ refunded_partial: 'partial', refunded_full: 'full' });

/** نمای بازپرداختِ یک سفارش — بی پرچم `undefined` (کلید نیاید). */
async function viewOf(shopId, orderId) {
  if (!(await flagOf(shopId))) return undefined;
  const id = String(orderId || '');
  const sum = await refunds.summary(shopId, id);
  if (!sum) return null;
  const order = await db.one('SELECT status FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id]);
  const asked = await db.one(
    `SELECT MIN(created_at) AS at FROM online_order_events
      WHERE shop_id=$1 AND order_id=$2 AND kind='status' AND to_status='return_requested'`, [shopId, id]);
  const requestedAt = asked && asked.at != null ? Number(asked.at) : null;
  if (requestedAt === null && sum.entries.length === 0) return null;
  const status = CLOSED[order.status] || (sum.refundedMinor > 0 ? 'in_progress' : 'requested');
  const last = sum.entries[sum.entries.length - 1];
  return {
    status,
    requestedAt,
    refundedMinor: sum.refundedMinor,
    remainingMinor: sum.remainingMinor,
    currency: sum.currency,
    entries: sum.entries.map((e) => ({ amountMinor: e.amountMinor, method: e.method, at: e.at, final: e.final })),
    updatedAt: last ? last.at : requestedAt,
  };
}

module.exports = { FLAG_KEY, flagOf, viewOf };
