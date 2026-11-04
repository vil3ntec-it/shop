'use strict';
/**
 * TEN-03 / O-08 (گرفته‌شده ۱۴۰۵/۰۸/۱۳: ب، یک سال) — لینکِ عمومیِ «کاغذی» که مشتری نگه می‌دارد: QR ِ فاکتور
 * (`lib/receipt-public.js`) و پیگیریِ شکایت (`lib/market-trust.js`). با همان پرچمِ `shop.storefront.link_expiry` ِ
 * لینکِ پیگیریِ سفارش (پیش‌فرض خاموش ⇒ رفتارِ امروز، بی انقضا) لینک `config.storefront.paperLinkTtlMs`
 * (`STORE_PAPER_LINK_DAYS`، پیش‌فرض ۳۶۵) پس از `since` ⇒ ۴۱۰ `link_expired`.
 *
 * ⛔ `since` فقط از ساعتِ سرور (زمانِ رسیدنِ فروش، آخرین کارِ پرونده)، هرگز از برنامه. خواننده پیش از این
 *    همهٔ حالت‌های بسته (۴۰۴ ِ یکسان) را سنجیده است؛ پس ۴۱۰ فقط به دارندهٔ توکنِ درست می‌رسد. خودِ داده دست نمی‌خورد.
 * • اول زمان، بعد پرچم: لینکِ تازه پرسشِ پرچم نمی‌زند.
 */
const config = require('../config');
const { now } = require('../db');
const { ApiError } = require('../middleware/errors');

const LINK_FLAG = 'shop.storefront.link_expiry';

async function assertFresh(shopId, since, message) {
  if (now() <= Number(since || 0) + config.storefront.paperLinkTtlMs) return;
  if (!(await require('./orders').flagOf(shopId, LINK_FLAG))) return;
  throw new ApiError(410, 'link_expired', message);
}

module.exports = { LINK_FLAG, assertFresh };
