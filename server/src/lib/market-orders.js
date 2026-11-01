'use strict';
/**
 * «سفارش‌های من» از همهٔ ویترین‌ها (S138) — با بلیتِ شماره (`myOrders`) و با حسابِ مشتریِ بازار
 * (`ordersOfPhone`، S9). جدا از `lib/market.js` تا آن زیرِ سقفِ اندازه بماند (`tools/quality`)؛ رفتار
 * همان. کمک‌های همان پرونده (`storeOpen`، `filterAsync`) تزریق می‌شوند.
 */
const { many } = require('../db');
const sf = require('./storefront');
const { ApiError } = require('../middleware/errors');

module.exports = ({ storeOpen, filterAsync }) => {
  async function myOrders(ticket) {
    //  ناهم‌گام: نسلِ بلیتِ شماره (خروج از همه‌جا / پاک کردنِ حساب) هم سنجیده می‌شود
    const phone = await sf.verifiedPhoneOfTicket(ticket);
    if (!phone) throw new ApiError(401, 'verification_required', 'برای دیدنِ سفارش‌ها، شمارهٔ خود را تأیید کنید');
    return ordersOfPhone(phone);
  }

  /** سفارش‌های ویترینیِ یک شماره در همهٔ دکان‌های باز (۵۰ تای تازه). */
  async function ordersOfPhone(phone) {
    const rows = await many(
      `SELECT o.id, o.shop_id, o.order_no, o.status, o.total_minor, o.items_count, o.fulfillment, o.created_at, o.track_gen,
              sf.slug, sf.title, sf.accent
         FROM online_orders o JOIN storefronts sf ON sf.shop_id = o.shop_id
        WHERE o.source = 'storefront' AND o.customer_phone = $1
        ORDER BY o.created_at DESC, o.id DESC LIMIT 50`,
      [phone]
    );
    const slugs = [...new Set(rows.map((r) => r.slug))];
    const [open, linked] = (await Promise.all([filterAsync(slugs, storeOpen), filterAsync([...new Set(rows.map((r) => r.shop_id))], sf.linkPolicyOn)])).map((x) => new Set(x));   //  linked: TEN-03، به‌ازای پرچمِ هر دکان
    return rows.filter((r) => open.has(r.slug)).map((r) => ({
      orderNo: Number(r.order_no), status: r.status, totalMinor: Number(r.total_minor),
      itemsCount: Number(r.items_count), fulfillment: r.fulfillment, createdAt: Number(r.created_at),
      token: sf.listTokenOf(r.shop_id, r, linked.has(r.shop_id)),
      store: { slug: r.slug, title: r.title, accent: r.accent },
    }));
  }

  return { myOrders, ordersOfPhone };
};
