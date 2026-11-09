'use strict';
/**
 * نمای کاملِ پیگیریِ سفارش برای مشتری — **یک** شکل برای `GET /api/store/:slug/orders/:token` و پاسخِ هر کارِ مشتری روی
 * همان سفارش که صفحه با آن دوباره می‌کشد (`pay-choice`، `items`، پذیرش/ردِ ویرایش، لغو).
 *
 * ⛔ چرا: صفحه (`store/order.js` `renderOrder`) هر کلیدی را که در پاسخ نیست پنهان می‌کند. آن پاسخ‌ها فقط `sf.track` بودند ⇒
 *    با «پرداخت پس از پذیرش» و گفت‌وگو/دستورِ تحویل/زمانِ تقریبی/روش/گزارش و نظر/بازپرداخت روشن، زدنِ «نقد» کارتِ
 *    گفت‌وگو و بقیه را تا پیگیریِ بعدی (۲۰ ثانیه) می‌برد — هر کدام تنها سالم، با هم خراب (`web-store-allflags.mjs`).
 * هر کلید فقط با پرچمِ خودش؛ ترتیب و قاعده‌ها همان مسیرِ GET ِ پیشین (بی تغییرِ رفتار).
 */
const sf = require('./storefront');
const pay = require('./payment-intents');
const flow = require('./order-flow');
const eta = require('./storefront-eta');

async function customerOrder(store, token) {
  //  PAY-21: قصدِ گذشته از زمان پیش از خواندن بسته می‌شود؛ دکمه فقط وقتی همین حالا پرداختنی است
  //  TEN-03: نخست سنجشِ لینک (نادرست ۴۰۴؛ منقضی/باطل ۴۱۰) — هیچ نوشتنی برای لینکِ ناروا
  const orderId = await sf.orderIdOfLink(store, token);
  await pay.refreshOrder(store.shop_id, orderId);
  const order = await sf.track(store, token);
  //  «پرداخت پس از پذیرش» (lib/order-flow): پیش از قبول یا پس از فاکتورِ نقدی دکمه نیست؛ کارتِ «پرداخت» فقط با تنظیمِ روشن
  const flowView = await flow.trackView(store.shop_id, orderId);
  order.onlinePay = { available: !flowView.blocked && await pay.orderPayable(store.shop_id, orderId) };
  if (flowView.step !== undefined) order.paymentStep = { ...flowView.step, online: order.onlinePay.available };
  //  NTF-08: پیگیریِ بازپرداخت — فقط با پرچمِ `shop.orders.refund_tracking` (خاموش ⇒ کلید نیست)
  const refund = await require('./order-refund-view').viewOf(store.shop_id, orderId);
  if (refund !== undefined) order.refund = refund;
  //  CHK-24: «ویرایشِ دستورِ تحویل» فقط با پرچمِ `shop.orders.delivery_prefs` (خاموش ⇒ کلید نیست)
  const prefs = await require('./delivery-prefs').customerPrefs(store.shop_id, orderId);
  if (prefs !== undefined) order.deliveryPrefs = prefs;
  //  NTF-07/09: گزارشِ مشکل و نظر — فقط با پرچم‌های `shop.orders.issue_reports` / `shop.orders.reviews` (خاموش ⇒ کلید نیست)
  const issueReports = await require('./order-issues').customerView(store.shop_id, orderId);
  if (issueReports !== undefined) order.issueReports = issueReports;
  const review = await require('./order-reviews').customerView(store.shop_id, orderId, order.status);
  if (review !== undefined) order.review = review;
  //  WEB-04: زمانِ تقریبیِ همین سفارش، از نو با هر مرحله — فقط با پرچمِ `shop.storefront.eta` (خاموش ⇒ کلید نیست)
  const live = await eta.orderEta(store, orderId);
  if (live !== undefined) order.eta = live;
  //  S71/S72: روش و مسئولِ تحویل و «تغییرِ روش» — فقط با پرچمِ `shop.storefront.delivery_method` (خاموش ⇒ کلید نیست)
  const method = await require('./order-delivery-method').customerView(store, orderId);
  if (method !== undefined) order.deliveryMethod = method;
  //  گفت‌وگوی همین سفارش (یک رشته برای هر سفارش) — فقط با پرچمِ `shop.chat` (خاموش ⇒ کلید نیست)
  if (await require('./shop-chat').flagOf(store.shop_id)) order.chat = true;
  //  DLV-18: فرمِ «به پیک امتیاز دهید» فقط با پرچمِ `shop.delivery_ratings` — بی این کلید صفحه `…/rating` را نمی‌خواهد (خاموش ⇒ ۴۰۴ ِ
  //  بی‌جا روی هر سفارشِ ارسالیِ تحویل‌شده؛ `web-store-allflags.mjs`)
  if (await require('./orders').flagOf(store.shop_id, require('./delivery-ratings').FLAG)) order.deliveryRating = true;
  return order;
}

module.exports = { customerOrder };
