'use strict';
/**
 * پرچم‌های **پیش‌فرض‌روشن** ِ `orders.flagOf` (۱۴۰۵/۰۸/۲۲، خواستِ صاحب‌کار: «کلیدهای کم‌خطر را روشن کن») — تنها فهرست.
 *
 *   قاعده ── نبودِ ردیف در `live_config` (نه دکان، نه `all`) ⇒ روشن؛ فقط `false` ِ صریح (دکان بر `all` جلو) خاموش
 *            می‌کند؛ «غیرفعال کردنِ سرویس» ِ `shop_holds` هنوز هر مقداری را می‌بُرد. کلیدِ بیرون از این فهرست همان
 *            قاعدهٔ پیشین است: فقط `true` روشن.
 *   مرز ─── فقط نمایش، راحتی یا خواندنی: هیچ‌کدام پول یا داده را بازنویسی نمی‌کند، سندی را عوض نمی‌کند، هزینهٔ بیرونی
 *            ندارد و دادهٔ خصوصی را به کسی که پیش‌تر نمی‌دید نشان نمی‌دهد. کلیدِ تازه فقط با همین سنجه و ردیفی در
 *            `docs/FLAGS-ON-fa.md` و `default: true` در `routes/admin-flags.js` (KNOWN) می‌آید.
 *   استثنا ─ چهار کلیدِ پایهٔ سفارش (`shop.orders`، `shop.storefront`، `shop.order_notify`، `shop.device_push`) با تصمیمِ صریحِ
 *            صاحب‌کار (۱۴۰۵/۰۸/۲۴) نه با سنجهٔ «فقط نمایش»؛ `orders.flagOf`، `order-notify.flagIn` و `shop-push` همین فهرست را می‌خوانند.
 *   ⛔ دری که جز پرچم شرطِ دیگری هم دارد همان می‌ماند (مثلاً `map_pin` بی `STORE_MAP_TILES` نقشه ندارد، `gallery` بی
 *      `shop.image_bank` گالری ندارد، اعلانِ بازپرداخت بی `shop.customer_notify` پیامی نمی‌فرستد).
 *   آزمون: `test/flag-defaults.test.js`.
 */
const KEYS = Object.freeze([
  'shop.storefront.filters',        //  فیلترِ کاتالوگِ ویترین (برند، قیمت، موجود، مشخصه) — فقط خواندن
  'shop.storefront.recent',         //  «دیده‌شده‌های اخیر» فقط روی دستگاهِ خودِ مشتری؛ سرور چیزی نمی‌نویسد
  'shop.storefront.search',         //  جست‌وجوی ویترین با غلطِ تایپی، پرش به دسته و «پیدا نشد» ِ مفید
  'shop.storefront.search_core',    //  جست‌وجو با قاعده‌های صندوق (رقمِ فارسی = لاتین، «ي» = «ی»)؛ متنِ ذخیره دست نمی‌خورد
  'shop.storefront.gallery',        //  عکس‌های بیشترِ کالا در برگهٔ کالا (فقط با `shop.image_bank` ِ همان دکان)
  'shop.storefront.stock_list',     //  در ۴۰۹ ِ ناموجود همهٔ قلم‌های کم با «کم کن به …» (همان پاسخِ سرور، فقط نمایش)
  'shop.storefront.address_extra',  //  خانه‌های اختیاریِ نشانی (شمارهٔ دوم، Plus Code، ولایت/گذر/منزل) در فرمِ ثبت
  'shop.storefront.map_pin',        //  «انتخاب روی نقشه» برای پینِ نشانی (فقط با `STORE_MAP_TILES` ِ سرور)
  'shop.orders.refund_tracking',    //  پیگیریِ بازپرداختِ همان سفارش برای خودِ مشتری
  'shop.orders.refund_receipt',     //  رسیدِ اصلاحیِ خواندنی از دفترِ فقط‌افزودنیِ بازپرداخت؛ هیچ ردیفی نمی‌نویسد
  'shop.orders.presence',           //  نشانگرِ «باز / بسته» در بخشِ سفارش‌های کارمند (همان داوریِ ویترین)
  'shop.orders.online_report',      //  گزارشِ فروشِ اینترنتی و جست‌وجوهای بی‌نتیجه (بازدیدکننده فقط HMAC، بی IP)
  'shop.branches_report',           //  خلاصهٔ خواندنیِ شعبه‌ها فقط برای صاحب
  'shop.delivery_route',            //  ترتیبِ پیشنهادیِ مأموریت‌های پیک؛ وضعیتی عوض نمی‌شود
  'shop.delivery_map',              //  پیوندِ مسیریاب و نقشهٔ آفلاینِ پیک (فقط فایل‌های خودِ سرور)
  'shop.orders.start_alert',        //  یادداشت و پوشِ «شروعِ سفارشِ زمان‌بندی‌شده» به خودِ دکان؛ وضعیت عوض نمی‌شود
  'shop.orders.stuck_alert',        //  یادداشت و پوشِ «سفارشِ گیرکرده» به خودِ دکان؛ وضعیت عوض نمی‌شود
  //  ── تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۴ («همهٔ این‌ها رو فعال‌شده بذار»؛ `docs/FLAGS-ON-fa.md` §۴۸): همهٔ قابلیت‌های سفارش
  //     پیش‌فرض روشن — پایهٔ سفارش و ویترین و اعلانِ سفارش، نه فقط نمایشی. اشتراک (`online_orders`/`storefront`)، ساختن و
  //     روشن کردنِ ویترین به دستِ خودِ دکان، `shop.orders.subscribers_only` و «غیرفعال کردنِ سرویس» همان دروازه‌اند.
  'shop.orders',                    //  بخشِ «سفارش‌ها» و ثبتِ سفارش (`/api/shop/orders/*`؛ نوشتن هنوز کلیدِ اشتراکِ `online_orders` می‌خواهد)
  'shop.storefront',                //  ویترینِ عمومی و مدیریتش (هنوز با `shop.orders`، کلیدِ اشتراکِ `storefront` و ویترینِ ساخته‌وروشنِ خودِ دکان)
  'shop.order_notify',              //  اعلانِ سفارشِ تازه/پرداخت/لغو به گوشی‌های همان دکان (صفِ پایدار؛ پوش رایگان، بی FCM ِ سرور فقط نمی‌رود)
  'shop.device_push',               //  ثبتِ توکنِ پوشِ دستگاه‌های دکان؛ بی حسابِ Firebase ِ سرور هیچ پوشی فرستاده نمی‌شود (`push.sendTo` ⇒ `skipped`)
]);

/** چهار کلیدِ پایهٔ سفارش (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۴) — جدا از ۱۷ کلیدِ نمایشی تا آزمونِ «پاسخِ پیش از ۱۴۰۵/۰۸/۲۲» پایه را نبندد */
const ORDER_BASE = Object.freeze(['shop.orders', 'shop.storefront', 'shop.order_notify', 'shop.device_push']);

//  Set ِ واقعی (has/size/iteration) ولی فقط‌خواندنی: `Object.freeze` به‌تنهایی جلوی add/delete/clear را نمی‌گیرد
const DEFAULT_ON = new Set(KEYS);
for (const m of ['add', 'delete', 'clear']) {
  Object.defineProperty(DEFAULT_ON, m, { value: () => { throw new TypeError('DEFAULT_ON فقط‌خواندنی است'); } });
}
Object.freeze(DEFAULT_ON);

module.exports = { DEFAULT_ON, KEYS, ORDER_BASE };
