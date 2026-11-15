'use strict';
/**
 * «سفارش‌های من» ِ ویترین (S138): مالکیتِ سفارش و فهرست/تاریخچهٔ سفارش‌های همین مشتری در همین ویترین — از
 * `lib/storefront.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ رفتار همان. «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): پیوندِ مشتری ⇄ سفارش ایمیلِ
 * تأییدشده و شناسهٔ حساب است، نه شماره. `verification` (بلیت/نشست)، `linkPolicyOn` و `listTokenOf` (پیوندِ پیگیری) از همان
 * پرونده تزریق می‌شوند؛ `ownsOrder`/`OWNED_SQL` از راهِ `require('./storefront')` هم در دسترس‌اند.
 */
const { many } = require('../db');
const views = require('./public-views');
const { badRequest, ApiError } = require('../middleware/errors');

module.exports = ({ verification, linkPolicyOn, listTokenOf }) => {
/**
 * «این سفارش مالِ همین مشتری است؟» — ایمیلِ تأییدشدهٔ ثبت، یا حسابِ همان مشتری (سفارش‌های پیشین پس از تغییرِ ایمیل).
 * `who` همان `verification(...)` است.
 */
function ownsOrder(row, who) {
  if (!who || !row) return false;
  if (who.customerId && row.store_customer_id === who.customerId) return true;
  return Boolean(who.email) && row.customer_email === who.email
    && (!row.store_customer_id || row.store_customer_id === who.customerId);
}

/**
 * شرطِ SQL ِ «سفارش‌های همین مشتری» (`ownsOrder` در SQL): `$e` ایمیل، `$c` شناسهٔ حساب ('' = بی حساب). سفارشی که
 * به حسابِ دیگری گره خورده (ایمیلش پس از ثبت عوض شده) به دارندهٔ تازهٔ همان ایمیل نشان داده نمی‌شود.
 */
const OWNED_SQL = (a, e, c) => `((${c} <> '' AND ${a}store_customer_id = ${c})
  OR (${a}customer_email = ${e} AND ${e} <> '' AND (${a}store_customer_id IS NULL OR ${a}store_customer_id = ${c}
      OR ${a}store_customer_id IN (SELECT sc.id FROM store_customers sc WHERE sc.email = ${e}))))`;

/** S138: سفارش‌های همین مشتری (ایمیلِ تأییدشده یا حسابش) در همین ویترین — از هر دستگاهی. */
async function myOrders(sf, ticket, session = '') {
  const who = await verification(ticket, session);
  if (!who) throw new ApiError(401, 'verification_required', 'برای دیدنِ سفارش‌ها، ایمیلِ خود را تأیید کنید');
  const rows = await many(
    `SELECT id, order_no, status, total_minor, items_count, created_at, track_gen FROM online_orders
      WHERE shop_id = $1 AND source = 'storefront' AND ${OWNED_SQL('', '$2', '$3')}
      ORDER BY created_at DESC, id DESC LIMIT 20`,
    [sf.shop_id, who.email, who.customerId || '']
  );
  const on = await linkPolicyOn(sf.shop_id);
  return views.pubList('orderSummary', rows.map((r) => ({
    orderNo: Number(r.order_no), status: r.status, totalMinor: Number(r.total_minor),
    itemsCount: Number(r.items_count), createdAt: Number(r.created_at), token: listTokenOf(sf.shop_id, r, on) })));
}

/**
 * تاریخچهٔ حسابِ مشتری در همین ویترین — صفحه‌بندیِ کلیدی (تازه‌ترین اول)، با نامِ چند قلمِ اول. فقط همین مشتری
 * (`who` = `{ email, customerId }`: سفارش‌های گره‌خورده به حساب، پیش و پس از هر تغییرِ ایمیل/شماره) و همین دکان (S138).
 */
async function ordersOfCustomer(sf, who, { cursor, limit } = {}) {
  const lim = Math.min(50, Math.max(1, Number.parseInt(limit, 10) || 20));
  const params = [sf.shop_id, (who && who.email) || '', (who && who.customerId) || ''];
  let where = `shop_id = $1 AND source = 'storefront' AND ${OWNED_SQL('', '$2', '$3')}`;
  if (cursor) {
    const m = /^(\d{1,15})\.([0-9A-Z]{26})$/.exec(String(cursor));
    if (!m) throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
    params.push(Number(m[1]), m[2]);
    where += ` AND (created_at, id) < ($4, $5)`;
  }
  params.push(lim + 1);
  const rows = await many(
    `SELECT id, order_no, status, fulfillment, total_minor, items_count, scheduled_for, created_at, track_gen FROM online_orders
      WHERE ${where} ORDER BY created_at DESC, id DESC LIMIT $${params.length}`,
    params
  );
  const more = rows.length > lim;
  const page = more ? rows.slice(0, lim) : rows;
  const names = page.length ? await many(
    `SELECT order_id, name FROM online_order_lines WHERE shop_id = $1 AND order_id = ANY($2::text[]) AND line_no <= 3
      ORDER BY order_id, line_no`,
    [sf.shop_id, page.map((r) => r.id)]
  ) : [];
  const last = page[page.length - 1];
  const on = await linkPolicyOn(sf.shop_id);
  return {
    orders: views.pubList('orderSummary', page.map((r) => ({
      orderNo: Number(r.order_no), status: r.status, fulfillment: r.fulfillment, totalMinor: Number(r.total_minor),
      itemsCount: Number(r.items_count), createdAt: Number(r.created_at),
      scheduledFor: r.scheduled_for === null ? null : Number(r.scheduled_for),
      preview: names.filter((n) => n.order_id === r.id).map((n) => n.name),
      token: listTokenOf(sf.shop_id, r, on),
    }))),
    nextCursor: more ? `${Number(last.created_at)}.${last.id}` : null,
  };
}

return { ownsOrder, OWNED_SQL, myOrders, ordersOfCustomer };
};
