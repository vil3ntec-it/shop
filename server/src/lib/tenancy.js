'use strict';
/**
 * دو بخشِ سامانه، یک دستگاهِ اشتراک.
 *
 * ── مشکلی که این حل می‌کند ─────────────────────────────────────────
 * بخشِ پمپ‌بنزین باید همان چیزهایی را داشته باشد که بخشِ شاپ دارد:
 * اشتراک، تمدید، مهلت، تاریخچه، کدِ شش‌رقمی، و خبرِ «دارد تمام
 * می‌شود». راهِ ساده این بود که `subscriptions.js` و `vip-codes.js`
 * کپی شوند و در کپی، `shop` به `station` عوض شود.
 *
 * ولی آن‌وقت هر اصلاحِ بعدی باید دو بار انجام می‌شد — و روزی که یکی‌اش
 * فراموش شود، دو بخش دو جور حساب می‌کنند و هیچ‌کس تا وقتی پولِ کسی
 * اشتباه حساب نشده خبردار نمی‌شود.
 *
 * پس: یک کد، دو جدول. این فایل فقط می‌گوید «نامِ جدول و ستونِ هر بخش
 * چیست»؛ منطق در `subscriptions.js` و `vip-codes.js` یکی می‌ماند.
 *
 * ── چرا رشته‌ها مستقیم در SQL می‌روند ────────────────────────────────
 * نامِ جدول را نمی‌شود پارامترِ پرس‌وجو کرد. پس این نام‌ها داخل متنِ SQL
 * می‌نشینند — و امن‌اند **چون فقط از همین فایل می‌آیند**: ثابت‌های
 * نوشته‌شده در کد، نه چیزی که از درخواست خوانده شده باشد. `byApp()`
 * هم هر نامِ ناشناخته را رد می‌کند، پس حتی مسیری که `app` را از کاربر
 * می‌گیرد نمی‌تواند جدولِ دیگری را نشان بدهد.
 */

/** بخشِ دکان — همان چیزی که از روزِ اول بوده. */
const SHOP = Object.freeze({
  app: 'shop',
  label: 'دکان',
  tenantTable: 'shops',
  tenantKey: 'shop_id',
  subsTable: 'subscriptions',
  historyTable: 'subscription_history',
  vipTable: 'vip_codes',
  vipTenantKey: 'shop_id',
  vipUsedKey: 'used_shop_id',
  notFoundMessage: 'دکان پیدا نشد',
  notFoundCode: 'shop_not_found',
  //  متنِ خبرِ «اشتراکت دارد تمام می‌شود»
  expiryNoun: 'دکان شما',
});

/** بخشِ پمپ‌بنزین — جدول‌های جدا، همان منطق. */
const PUMP = Object.freeze({
  app: 'pump',
  label: 'پمپ',
  tenantTable: 'stations',
  tenantKey: 'station_id',
  subsTable: 'station_subscriptions',
  historyTable: 'station_subscription_history',
  vipTable: 'station_vip_codes',
  vipTenantKey: 'station_id',
  vipUsedKey: 'used_station_id',
  notFoundMessage: 'پمپ پیدا نشد',
  notFoundCode: 'station_not_found',
  expiryNoun: 'پمپ شما',
});

const ALL = Object.freeze({ shop: SHOP, pump: PUMP });

/**
 * بخش را با نامش پیدا می‌کند.
 *
 * هر نامِ ناشناخته خطا می‌دهد و چیزی برنمی‌گرداند — این تنها دری است
 * که نامِ جدول از آن به SQL می‌رسد، پس باید بسته بماند.
 */
function byApp(app) {
  const t = ALL[String(app || '').trim().toLowerCase()];
  if (!t) throw new Error(`بخشِ ناشناخته: ${app}`);
  return t;
}

module.exports = { SHOP, PUMP, ALL, byApp, APPS: Object.keys(ALL) };
