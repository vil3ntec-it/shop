'use strict';
/**
 * «چرا سفارشِ آنلاین به دستم نمی‌رسد؟» (گلهٔ صاحب‌کار ۱۴۰۵/۰۸/۱۳) — فقط خواندن، برای `arrival` ِ
 * `GET /api/shop/orders/config`. هر دروازه‌ای که سفارشِ مشتری را پیش از رسیدن به برنامهٔ دکان می‌بندد، این‌جا
 * با کدِ پایدار و «چه کسی درستش می‌کند» گفته می‌شود — شکستِ بی‌صدا همان باگ بود: بخشِ «سفارش‌ها» با پرچمِ
 * خاموش یا بی اشتراک **کامل پنهان** می‌شد و ویترینِ خاموش/بی کالا سفارشی نمی‌گرفت و هیچ‌کس نمی‌گفت چرا.
 *
 * زنجیرهٔ واقعی: ویترین/بازارچه ⇒ `POST /api/store/:slug/checkout` (`sf.open` + `orders.create`) ⇒ ردیفِ
 * `online_orders` ⇒ برنامهٔ دکان با `GET /shop/orders/config` (`enabled`) و پرسیدنِ `summary` هر ۲۰ ثانیه، فقط
 * جلوی چشم (`OrdersLive`) و اعلانِ درونِ برنامه با پرچمِ `shop.order_notify` (`OrderNotify`؛ FCM در برنامه نیست).
 *
 * ⛔ هیچ دروازه‌ای این‌جا باز نمی‌شود — SUB-10، `shop.orders.subscribers_only` و پرچم‌ها همان تصمیمِ صاحب
 *    محصول‌اند؛ فقط گفته می‌شوند. `fix`: `support` (پرچم/سرور، کارِ پشتیبانی)، `subscribe` (اشتراک)،
 *    `storefront` (تنظیمِ ویترین در برنامه)، `products` (کالا در ویترین)، `server` (راه‌اندازیِ سرور).
 */
const config = require('../config');
const { one } = require('../db');

/**
 * خالص: واقعیت‌ها ⇒ `{ ok, blockers, warnings }`. ترتیبِ `blockers` همان ترتیبِ زنجیره است (نخستین = اصلی).
 * @param {object} f واقعیت‌ها ([factsOf])
 */
function verdict(f) {
  const blockers = [];
  const add = (code, fix) => blockers.push({ code, fix });
  if (!f.ordersFlag) add('orders_off', 'support');
  if (!f.ordersPlan) add('no_subscription', 'subscribe');
  if (!f.storefrontFlag) add('storefront_off', 'support');
  if (f.ordersPlan && !f.storefrontPlan) add('storefront_not_in_plan', 'subscribe');
  if (f.subscribersOnly && !f.subscribed) add('trial_not_accepting', 'subscribe');
  if (!f.storefrontMade) add('storefront_missing', 'storefront');
  else if (!f.storefrontOn) add('storefront_hidden', 'storefront');
  if (!f.shopActive) add('shop_inactive', 'support');
  if (f.suspended) add('storefront_suspended', 'support');
  if (f.storefrontMade && !f.hasProducts) add('no_products', 'products');
  if (!f.phoneCheck) add('phone_check_off', 'server');
  if (!f.serverReady) add('server_not_ready', 'server');
  const warnings = [];
  if (!f.alerts) warnings.push({ code: 'alerts_off', fix: 'support' });
  return { ok: blockers.length === 0, blockers, warnings };
}

/** واقعیت‌های همین دکان، با ساعتِ سرور (`entitlementOf`) */
async function factsOf(shopId) {
  const orders = require('./orders');
  const sf = require('./storefront');
  const [st, sfs, notify, phoneCheck] = await Promise.all([
    orders.stateOf(shopId), sf.stateOf(shopId), require('./order-notify').flagOf(shopId), sf.verificationAvailable(),
  ]);
  const row = await one(
    `SELECT s.status AS shop_status, f.slug, f.enabled,
            EXISTS (SELECT 1 FROM trust_sanctions ts WHERE ts.shop_id = s.id
                      AND ts.kind = 'suspend_storefront' AND ts.lifted_at IS NULL) AS suspended
       FROM shops s LEFT JOIN storefronts f ON f.shop_id = s.id WHERE s.id = $1`, [String(shopId)]
  );
  const made = Boolean(row && row.slug);
  let hasProducts = false;
  if (made) {
    hasProducts = Boolean(await one(
      `SELECT 1 AS x FROM ${await sf.productFrom(shopId)} WHERE sp.shop_id = $1 AND sp.published AND ${sf.LIVE_PRODUCT} LIMIT 1`,
      [String(shopId)]
    ));
  }
  return {
    ordersFlag: st.flag, ordersPlan: st.entitled, storefrontFlag: sfs.storefrontFlag,
    storefrontPlan: sfs.ent.features.includes(sf.FEATURE_KEY),
    subscribersOnly: st.subscribersOnly, subscribed: st.subscribed,
    storefrontMade: made, storefrontOn: made && row.enabled === true, shopActive: Boolean(row) && row.shop_status === 'active',
    suspended: Boolean(row) && row.suspended === true, hasProducts,
    phoneCheck: Boolean(phoneCheck), serverReady: Boolean(config.secrets.api || config.secrets.jwt), alerts: Boolean(notify),
  };
}

async function arrivalOf(shopId) {
  return verdict(await factsOf(shopId));
}

module.exports = { verdict, factsOf, arrivalOf };
