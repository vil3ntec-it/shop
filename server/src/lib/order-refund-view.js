'use strict';
/**
 * NTF-08 — پیگیریِ بازپرداخت برای مشتریِ ویترین (کلیدِ `refund` در `GET /api/store/:slug/orders/:token`).
 *
 *   پرچم ── `shop.orders.refund_tracking` (همان قاعدهٔ `orders.flagOf`؛ از ۱۴۰۵/۰۸/۲۲ پیش‌فرض روشن، `lib/flag-defaults.js`؛
 *           `false` ِ صریح ⇒ کلید نیست، پاسخِ پیگیری بیت‌به‌بیت همان پیشین).
 *   قرارداد ─ `order.refund?` ِ نسخهٔ ۲ ِ نمای عمومی (`lib/public-views.js` ⇒ `orderRefund`، `refundEntry`، `refundReceipt`؛
 *           نمونه از [shapeOf] در `core/test-vectors/market-public-v2.json`). با پرچمِ `market.public_views` فهرستِ سفید می‌بُرد.
 *   منبع ── فقط دفترِ بازپرداختِ دکان (`online_order_refunds`، #255) و خطِ زمانیِ سفارش؛ این‌جا هیچ
 *           نوشتنی نیست.
 *   نما ─── `null` تا درخواستِ برگشتی نیامده و ردیفی در دفتر نیست؛ وگرنه
 *           `{status, requestedAt, refundedMinor, remainingMinor, currency, entries, updatedAt}`:
 *             • `requested`  — «درخواستِ برگشت» ثبت شد، هنوز پولی برنگشته
 *             • `in_progress`— بخشی برگشته و دکان هنوز نبسته
 *             • `partial` / `full` — بسته شد (بازپرداختِ بخشی / کامل)
 *           هر ردیف فقط مبلغ، روش، زمان و «پایانی» — ⛔ نه دلیلِ نوشتهٔ کارمند، نه کننده.
 *           E2E-RET-11: با پرچمِ `shop.orders.refund_receipt` هم `receipts` (رسیدِ اصلاحیِ هر ردیف، `order-refund-receipt`).
 */
const db = require('../db');
const orders = require('./orders');
const views = require('./public-views');
const refunds = require('./order-refunds');
const receipt = require('./order-refund-receipt');

const FLAG_KEY = 'shop.orders.refund_tracking';
const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);

const CLOSED = Object.freeze({ refunded_partial: 'partial', refunded_full: 'full' });

/**
 * نمای مشتری از خلاصهٔ دفتر (خالص، بی پایگاه) — `null` تا درخواستِ برگشت یا ردیفی نیست.
 * @param {{status:string, orderNo:number}} order وضعیت و شمارهٔ سفارش
 * @param {number|null} requestedAt نخستین «درخواستِ برگشت» ِ خطِ زمانی
 * @param {{refundedMinor:number, remainingMinor:number, totalMinor:number, currency:string, entries:object[]}} sum `refunds.summary`
 * @param {boolean} withReceipts پرچمِ `shop.orders.refund_receipt`
 */
function shapeOf(order, requestedAt, sum, withReceipts) {
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
    ...(withReceipts ? { receipts: receipt.receiptsOf({ orderNo: order.orderNo, totalMinor: sum.totalMinor, currency: sum.currency }, sum.entries) } : {}),
  };
}

/** نمای بازپرداختِ یک سفارش — بی پرچم `undefined` (کلید نیاید). */
async function viewOf(shopId, orderId) {
  if (!(await flagOf(shopId))) return undefined;
  const id = String(orderId || '');
  const sum = await refunds.summary(shopId, id);
  if (!sum) return null;
  const order = await db.one('SELECT status, order_no FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id]);
  const asked = await db.one(
    `SELECT MIN(created_at) AS at FROM online_order_events
      WHERE shop_id=$1 AND order_id=$2 AND kind='status' AND to_status='return_requested'`, [shopId, id]);
  const requestedAt = asked && asked.at != null ? Number(asked.at) : null;
  if (requestedAt === null && sum.entries.length === 0) return null;
  const shape = shapeOf({ status: order.status, orderNo: Number(order.order_no) }, requestedAt, sum, await receipt.flagOn(shopId));
  return views.pub('orderRefund', shape);
}

module.exports = { FLAG_KEY, flagOf, viewOf, shapeOf };
