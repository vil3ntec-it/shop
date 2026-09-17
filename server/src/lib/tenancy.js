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
  eventsTable: 'shop_events',
  eventReadsTable: 'shop_event_reads',
  //  `shop_events` ستونِ دستگاه ندارد و لازم هم نیست: هر خبرِ دکان از
  //  حسابِ یک عضو می‌آید. کامپیوترِ پمپ حساب ندارد، پس آن‌جا هست.
  eventDeviceColumn: '',
  eventTitles: Object.freeze({
    stock_out: 'کالا تمام شد',
    low_stock: 'کالا رو به اتمام',
    debt: 'قرضِ از حد گذشته',
  }),
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
  eventsTable: 'station_events',
  eventReadsTable: 'station_event_reads',
  eventDeviceColumn: 'device_uid',
  eventTitles: Object.freeze({
    stock_out: 'تیلِ مخزن تمام شد',
    low_stock: 'تیلِ مخزن رو به اتمام',
    debt: 'قرض از حد گذشت',
  }),
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

/**
 * نام‌های دیگری که هر بخش با آن‌ها صدا زده می‌شود.
 *
 * ── چرا لازم شد ────────────────────────────────────────────────────
 * برنامه‌ها خودشان را با یک نام معرفی نمی‌کنند: برنامهٔ کامپیوترِ پمپ
 * در هدرِ `X-App-Id` همان `tohid-pump-app` را می‌فرستد (شنوندهٔ
 * مجوزش)، در حالی که بدنهٔ درخواست `pump` می‌گوید. یکی‌شان را
 * نشناختن یعنی نشستی که به بخشِ **اشتباه** مهر می‌خورد.
 */
const ALIASES = Object.freeze({
  shop: 'shop',
  'tohid-shop-app': 'shop',
  'shop-app': 'shop',
  pump: 'pump',
  'tohid-pump-app': 'pump',
  'pump-app': 'pump',
  station: 'pump',
});

/**
 * نامِ بخش از یک رشتهٔ خام — یا `''` اگر نشناخت.
 *
 * ⚠️ این `byApp` نیست: این‌جا نشناختن **خطا نیست**، چون ورودی از
 * درخواست می‌آید و یک نامِ عجیب نباید ورودِ کسی را بشکند. هر جا که
 * نامِ جدول لازم است، `byApp` سرِ جایش است و همان هم بسته می‌ماند.
 */
function sectionOf(raw) {
  return ALIASES[String(raw || '').trim().toLowerCase()] || '';
}

/**
 * بخشی که این درخواست از آن آمده.
 *
 * ── سه جای گفتن، به همین ترتیب ─────────────────────────────────────
 *   ۱) `app` در بدنه            — صریح‌ترین، و همان که از روزِ اول بود
 *   ۲) هدرِ `X-App-Id`          — هر درخواستِ برنامهٔ پمپ آن را دارد
 *   ۳) `?app=` در نشانی         — برای صفحه‌هایی که بدنه ندارند
 *
 * ⛔ **و هیچ‌کدام دری باز نمی‌کند.** این فقط می‌گوید نشستِ تازه به کدام
 * بخش مهر بخورد؛ دسترسی همچنان از عضویتِ واقعیِ همان کاربر در
 * `shops`/`stations` می‌آید. پس دروغ گفتنش جز این‌که آدم را به بخشِ
 * خالیِ خودش ببرد کاری نمی‌کند.
 *
 * ⚠️ **نگفتن یعنی `shop`** — هر برنامه‌ای که امروز در دستِ کاربران است
 * و چیزی نمی‌گوید، مالِ بخشِ دکان است. پس هیچ‌کس بیرون نمی‌افتد.
 */
function appOfRequest(req) {
  return sectionOf(req?.body?.app)
    || sectionOf(req?.headers?.['x-app-id'])
    || sectionOf(req?.query?.app)
    || 'shop';
}

module.exports = {
  SHOP, PUMP, ALL, byApp, APPS: Object.keys(ALL),
  ALIASES, sectionOf, appOfRequest,
};
