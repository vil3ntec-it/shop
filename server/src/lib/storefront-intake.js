'use strict';
/**
 * #467 — سفارشِ آنلاینِ تازه فقط برای دکانِ مشترک، در ویترین (پشتِ پرچمِ خاموشِ
 * `shop.orders.subscribers_only`؛ دروازهٔ ثبت `orders.intakeOf` در `orders.create` است).
 * جدا از `lib/storefront.js` تا آن زیرِ سقفِ اندازه بماند (`tools/quality`).
 *
 *   ⛔ SUB-10 (#621) دست نمی‌خورد: `enabled` ِ `storefront.stateOf` همان پرچم‌ها + کلیدهای اشتراک
 *      است؛ بی اشتراک، منقضی، معلق، لغوشده یا بی کلیدِ ویترین ⇒ ۴۰۴ ِ `store_not_found`، با هر پرچمی.
 *   دروازه ─ فقط دکانی که ویترینش طبقِ همان قاعده باز است ولی **اشتراکِ پولیِ فعال** ندارد (مثلاً
 *      دورهٔ آزمایشی): `accepting: false` ⇒ ویترین فقط‌خواندنی (`ordering`)، بیرون از بازارچه، و ثبتِ
 *      تازه ۴۰۳ ِ `store_not_accepting_orders`؛ سفارشِ پیشین جلو می‌رود. پرچمِ خاموش ⇒ همان main.
 */
const orders = require('./orders');

/** افزوده‌های `stateOf` ِ ویترین: `accepting` (سفارشِ تازه می‌گیرد؟)، `subscribersOnly`، `subscribed`. */
async function stateOf(shopId, ent, enabled, featureKey) {
  const intake = await orders.intakeOf(shopId, ent, [featureKey]);
  return { accepting: enabled && intake.accepting, subscribersOnly: intake.gated, subscribed: intake.subscribed };
}

/** `open`: همین دو روی ردیفِ ویترین (برای `orderingOf` و دروازهٔ بازارچه)؛ همان `st` را برمی‌گرداند. */
function onRow(row, st) {
  Object.assign(row, { accepting: st.accepting, subscribersOnly: st.subscribersOnly });
  return st;
}

/**
 * «سفارش می‌پذیرد؟» برای نمای عمومی — بی هیچ حرفی از اشتراک. فقط با پرچمِ روشن ⇒ `{ ordering }`؛
 * خاموش ⇒ `{}` (پاسخِ main بی‌کم‌وکاست).
 */
function orderingOf(sf) {
  if (!sf.subscribersOnly) return {};
  if (sf.accepting !== false) return { ordering: { accepting: true } };
  return { ordering: { accepting: false, code: orders.NOT_ACCEPTING, message: orders.NOT_ACCEPTING_MESSAGE } };
}

module.exports = { stateOf, onRow, orderingOf };
