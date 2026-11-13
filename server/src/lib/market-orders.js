'use strict';
/**
 * «سفارش‌های من» از همهٔ ویترین‌ها (S138) — با بلیتِ ایمیل (`myOrders`؛ «فقط ایمیل»، ۱۴۰۵/۰۸/۲۳) و با حسابِ
 * مشتری (`ordersOfCustomer`، S9). جدا از `lib/market.js` تا آن زیرِ سقفِ اندازه بماند (`tools/quality`)؛ رفتار
 * همان. کمک‌های همان پرونده (`storeOpen`، `filterAsync`) تزریق می‌شوند.
 */
const { many } = require('../db');
const sf = require('./storefront');
const { ApiError } = require('../middleware/errors');
const views = require('./public-views');   //  S2: فهرستِ سفیدِ `orderSummary` (پشتِ `market.public_views`)

module.exports = ({ storeOpen, filterAsync }) => {
  async function myOrders(ticket) {
    //  ناهم‌گام: نسلِ بلیتِ ایمیل (خروج از همه‌جا / پاک کردنِ حساب) هم سنجیده می‌شود
    const who = await sf.verification(ticket, '');
    if (!who) throw new ApiError(401, 'verification_required', 'برای دیدنِ سفارش‌ها، ایمیلِ خود را تأیید کنید');
    return ordersOfCustomer(who);
  }

  /** سفارش‌های ویترینیِ یک مشتری (ایمیلِ تأییدشده یا حسابش؛ `sf.OWNED_SQL`) در همهٔ دکان‌های باز (۵۰ تای تازه). */
  async function ordersOfCustomer(who) {
    const rows = await many(
      `SELECT o.id, o.shop_id, o.order_no, o.status, o.total_minor, o.items_count, o.fulfillment, o.created_at, o.track_gen,
              sf.slug, sf.title, sf.accent
         FROM online_orders o JOIN storefronts sf ON sf.shop_id = o.shop_id
        WHERE o.source = 'storefront' AND ${sf.OWNED_SQL('o.', '$1', '$2')}
        ORDER BY o.created_at DESC, o.id DESC LIMIT 50`,
      [who.email, who.customerId || '']
    );
    const slugs = [...new Set(rows.map((r) => r.slug))];
    const [open, linked] = (await Promise.all([filterAsync(slugs, storeOpen), filterAsync([...new Set(rows.map((r) => r.shop_id))], sf.linkPolicyOn)])).map((x) => new Set(x));   //  linked: TEN-03، به‌ازای پرچمِ هر دکان
    return rows.filter((r) => open.has(r.slug)).map((r) => views.pub('orderSummary', {
      orderNo: Number(r.order_no), status: r.status, totalMinor: Number(r.total_minor),
      itemsCount: Number(r.items_count), fulfillment: r.fulfillment, createdAt: Number(r.created_at),
      token: sf.listTokenOf(r.shop_id, r, linked.has(r.shop_id)),
      store: { slug: r.slug, title: r.title, accent: r.accent },
    }));
  }

  return { myOrders, ordersOfCustomer };
};
