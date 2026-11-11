'use strict';
/**
 * CHK-08، تصمیمِ ۴-۳ «الف» ِ صاحب‌کار — نقشهٔ OpenStreetMap برای پینِ نشانی در برگهٔ ثبتِ ویترین (`public/store/pin-map.js` ِ
 * دیربار، واژه‌نامهٔ `kit/i18n/map.*.json`). دو در، هر دو پیش‌فرض بسته:
 *   ۱) سرور: `STORE_MAP_TILES` — الگوی نشانیِ تکه‌ها با `{z}`/`{x}`/`{y}`؛ فقط https یا مسیرِ همین مبدأ (`/tiles/{z}/{x}/{y}.png`
 *      برای «ب»: تکه‌های روی سرورِ خودتان). نبود یا نادرست ⇒ هیچ دکانی نقشه ندارد و CSP ِ ویترین بیت‌به‌بیت همان امروز.
 *   ۲) دکان: پرچمِ خاموشِ `shop.storefront.map_pin` (`FLAGS-ON-fa.md` §۲۳).
 * تکه‌ها مستقیم از مرورگرِ مشتری به سرورِ نقشه می‌روند: فقط تصویر (`img-src` همان یک مبدأ؛ بی اسکریپت، بی کوکی، Referer فقط
 * مبدأ) — پس آن سرور IP و ناحیهٔ دیده‌شده را می‌بیند (خطرِ گزینهٔ «الف» در همان تصمیم). پین همان راهِ «موقعیتِ من» را می‌رود
 * (`setPin` ⇒ `GET /delivery`)؛ ناحیه را فقط سرور می‌گوید و مختصات همان اعتبارسنجیِ سفارش را دارد. آزمون: `store-map.test.js`.
 */
const config = require('../config');

const FLAG = 'shop.storefront.map_pin';
/** نامِ دارندهٔ داده برای «©» ِ زیرِ نقشه (شرطِ پروانهٔ ODbL و قاعدهٔ استفادهٔ تکه‌های OSM) */
const ATTRIBUTION = 'OpenStreetMap';
const COPYRIGHT = 'https://www.openstreetmap.org/copyright';

/** الگوی درست: سه جای‌نگهدار، https ِ میزبانِ ساده یا مسیرِ همین مبدأ، بی نویسه‌ای که سرآیندِ CSP یا HTML را بشکند */
function valid(t) {
  if (typeof t !== 'string' || t.length > 300 || /["'\s;,<>\\]/.test(t)) return false;
  if (!t.includes('{z}') || !t.includes('{x}') || !t.includes('{y}')) return false;
  return /^https:\/\/[a-z0-9.-]+(:\d{1,5})?\//i.test(t) || /^\/[^/]/.test(t);
}

function tiles() {
  const t = String(config.storefront.mapTiles || '').trim();
  return valid(t) ? t : '';
}

/** مبدأ برای `img-src` ِ CSP ِ ویترین؛ مسیرِ همین مبدأ یا بی تنظیم ⇒ '' (همان 'self') */
function origin(t = tiles()) {
  const m = /^(https:\/\/[^/]+)\//i.exec(t);
  return m ? m[1].toLowerCase() : '';
}

const flagOn = async (shopId) => !!tiles() && !!(await require('./orders').flagOf(shopId, FLAG));

/** `features.mapPin` ِ پاسخِ ویترین؛ خاموش ⇒ {} (همان پاسخِ امروز) */
async function featureOf(shopId) {
  return (await flagOn(shopId)) ? { mapPin: { tiles: tiles(), attribution: ATTRIBUTION, copyright: COPYRIGHT } } : {};
}

module.exports = { FLAG, ATTRIBUTION, COPYRIGHT, valid, tiles, origin, flagOn, featureOf };
