'use strict';
/**
 * E2E-RET-11 (§۲۴.۶، §۶.۸) — رسیدِ اصلاحیِ بازپرداختِ سفارشِ آنلاین، ساخته از همان دفترِ فقط‌افزودنیِ
 * `online_order_refunds` (#255). پیش‌تر دفتر مبلغ و تطبیق داشت ولی هیچ سندی که بگوید «سفارشِ N با این بازپرداخت
 * حالا چند است» نبود.
 *
 *   پرچم ── `shop.orders.refund_receipt` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش ⇒ هیچ کلیدِ تازه).
 *   سند ─── هر ردیفِ دفتر یک رسیدِ اصلاحی: شمارهٔ `<شمارهٔ سفارش>-R<seq>` (seq ِ همان ردیف زیرِ قفلِ سفارش، پس
 *           یکتا و پایدار؛ ردیف هرگز ویرایش یا پاک نمی‌شود، پس رسید هم عوض نمی‌شود)، جمعِ اصلیِ سفارش، برگشته پیش
 *           از این رسید، مبلغِ همین رسید، برگشته تا این رسید و مبلغِ درستِ سفارش پس از آن — همه عددِ صحیحِ واحدِ کوچک.
 *   کارمند ─ `GET /api/shop/orders/:id` ⇒ `order.refund.receipts` (با دلیل و کننده، همان دفتر).
 *   مشتری ── پیگیری (`refund.receipts` ِ NTF-08) ⇒ بی دلیل و بی کننده؛ صفحه در `store/order-changes.js` می‌نویسد.
 *   ⛔ سرور تنها سازنده است؛ هیچ ورودی‌ای از کاربر نمی‌گیرد و چیزی نمی‌نویسد.
 */
const orders = require('./orders');

const FLAG = 'shop.orders.refund_receipt';
const flagOn = (shopId) => orders.flagOf(shopId, FLAG);

const noOf = (orderNo, seq) => `${Number(orderNo)}-R${Number(seq)}`;

/**
 * رسیدهای اصلاحیِ یک سفارش به ترتیبِ seq (خالص).
 * @param {{orderNo:number, totalMinor:number, currency:string}} order
 * @param {{seq:number, amountMinor:number, method:string, final:boolean, at:number, reason?:string, actorId?:string}[]} entries
 */
function receiptsOf(order, entries, { staff = false } = {}) {
  const totalMinor = Number(order.totalMinor);
  let before = 0;
  return [...(entries || [])].sort((a, b) => a.seq - b.seq).map((e) => {
    const after = before + Number(e.amountMinor);
    const r = {
      no: noOf(order.orderNo, e.seq), seq: e.seq, at: e.at, orderNo: Number(order.orderNo), currency: order.currency,
      method: e.method, final: e.final === true, totalMinor, refundedBeforeMinor: before, amountMinor: Number(e.amountMinor),
      refundedAfterMinor: after, netMinor: totalMinor - after,
      ...(staff ? { reason: e.reason || '', actorId: e.actorId || '' } : {}),
    };
    before = after;
    return r;
  });
}

/** کنارِ `refunds.summary` ِ کارمند: پرچمِ روشن و دفترِ نا‌تهی ⇒ `summary.receipts`. */
async function attachStaff(shopId, order, summary) {
  if (!summary || !(await flagOn(shopId))) return summary;
  return { ...summary, receipts: receiptsOf({ orderNo: order.orderNo, totalMinor: summary.totalMinor, currency: summary.currency }, summary.entries, { staff: true }) };
}

module.exports = { FLAG, flagOn, noOf, receiptsOf, attachStaff };
