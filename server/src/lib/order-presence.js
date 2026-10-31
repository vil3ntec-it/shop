'use strict';
/**
 * ORD-03 — نشانگرِ بزرگِ «باز / بسته» در بخشِ «سفارش‌ها»ی کارمند.
 *
 * ⛔ پرچمِ `shop.orders.presence` (همان قاعدهٔ `orders.flagOf`؛ پیش‌فرض خاموش). خاموش ⇒
 * `GET /orders/summary` همان پاسخِ پیشین است و برنامه هیچ نشانگری نمی‌سازد.
 *
 * داوری فقط با سرور است: همان `storefront.statusOf` که مشتری در ویترین می‌بیند (حضورِ دکان
 * از آخرین تماسِ واقعیِ همگام‌سازی، اعلامِ مالک، ساعتِ کاری و تعطیلی؛ D-18، §۳۱.۱). کارمند
 * همان چیزی را می‌بیند که مشتری می‌بیند، نه حدسِ ساعتِ دستگاهِ خودش. «قطعِ اتصالِ همین
 * دستگاه» را خودِ برنامه از شکستِ همین پرسش می‌فهمد (سرور در آن لحظه در دسترس نیست).
 *
 *   published ── ویترین برای مشتری باز است (پرچم‌ها، اشتراک و `enabled` ِ خودِ دکان)؛
 *                نه ⇒ «ویترین بسته است» (سفارشِ تلفنی و دستی همچنان در همین صف می‌آید).
 *   badge / connection / orderAcceptance / declaredUntil / lastConfirmedAt ── همان معنای ویترین.
 */
const { one, now } = require('../db');
const orders = require('./orders');

const FLAG_KEY = 'shop.orders.presence';

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);

/** وضعیتِ «باز / بسته» ِ دکان برای کارمند — `null` یعنی ویترینی ساخته نشده. */
async function presenceOf(shopId, at = now()) {
  const row = await one('SELECT * FROM storefronts WHERE shop_id = $1', [String(shopId)]);
  if (!row) return null;
  const sf = require('./storefront');
  const [st, state] = await Promise.all([sf.statusOf(row, at), sf.stateOf(shopId)]);
  return {
    published: row.enabled === true && state.enabled === true,
    badge: st.badge,
    mode: st.mode,
    declaredUntil: st.declaredUntil,
    connection: st.connection,
    lastConfirmedAt: st.lastConfirmedAt,
    orderAcceptance: st.orderAcceptance,
    holiday: st.holiday,
    hoursSet: st.hoursSet,
    localTime: st.localTime,
  };
}

module.exports = { FLAG_KEY, flagOf, presenceOf };
