'use strict';
/**
 * WEB-16، تصمیمِ ۴-۲ «الف» ِ صاحب‌کار — عکسِ سبک‌ترِ ویترین با WebP، با کتابخانهٔ آزادِ libvips (`sharp`) روی خودِ سرور.
 *
 *   ⛔ پیش‌فرض خاموش: فقط با `STORE_WEBP=1` ِ سرور. `sharp` وابستگیِ **اختیاری** است: اگر روی این ماشین نصب نشد یا بار نشد
 *      ⇒ همان JPEG ِ امروز، بی خطا (`available()` = false).
 *   ⛔ فقط وقتی مرورگر `Accept: image/webp` می‌فرستد؛ پاسخ `Vary: Accept` دارد تا کشِ مشترک دو نسخه را قاطی نکند.
 *   ⛔ تبدیل از همان بایت‌های امروزِ `thumb`/`display` (اصل هرگز)، بی EXIF/GPS، با سقفِ پیکسل؛ هر خطا ⇒ همان JPEG.
 *   ⛔ بار: حداکثر `MAX_JOBS` تبدیلِ هم‌زمان (بیشتر ⇒ همان JPEG، نه صف) و کشِ حافظه با سقفِ بایت (LRU، کلید = هشِ محتوا + اندازه).
 *
 *   webpOf(data, hash, size) ⇒ Buffer ِ WebP یا null (یعنی همان اصل را بفرست)
 */
const config = require('../config');

const MAX_JOBS = 2;
const CACHE_BYTES = 32 * 1024 * 1024;
const MAX_PIXELS = 40_000_000;
const QUALITY = Object.freeze({ thumb: 70, display: 78 });

let sharp; // undefined = هنوز نسنجیده، null = نیست
function lib() {
  if (sharp === undefined) {
    try {
      sharp = require('sharp');
      sharp.cache(false);          //  کشِ درونیِ libvips نه؛ سقفِ حافظه همین‌جاست
      sharp.concurrency(1);
    } catch { sharp = null; }
  }
  return sharp;
}

const on = () => !!config.storefront.webp;
const available = () => on() && !!lib();
const wants = (accept) => /(^|,)\s*image\/webp\s*(;|,|$)/i.test(String(accept || ''));

const cache = new Map(); // key ⇒ Buffer (ترتیبِ درج = تازگی)
let cached = 0;
function remember(key, buf) {
  if (buf.length > CACHE_BYTES / 8) return;
  cache.set(key, buf); cached += buf.length;
  for (const [k, v] of cache) {
    if (cached <= CACHE_BYTES) break;
    cache.delete(k); cached -= v.length;
  }
}

let jobs = 0;
async function webpOf(data, hash, size) {
  if (!available() || !Buffer.isBuffer(data) || !data.length || !QUALITY[size]) return null;
  const key = `${hash}:${size}`;
  const hit = cache.get(key);
  if (hit) { cache.delete(key); cache.set(key, hit); return hit; }
  if (jobs >= MAX_JOBS) return null;
  jobs++;
  try {
    const out = await lib()(data, { limitInputPixels: MAX_PIXELS, failOn: 'error' })
      .rotate()                                    //  جهتِ EXIF روی پیکسل؛ خروجی بی فراداده
      .webp({ quality: QUALITY[size], effort: 4 })
      .toBuffer();
    //  فقط اگر واقعاً سبک‌تر شد؛ وگرنه همان اصل
    if (!out.length || out.length >= data.length) { remember(key, Buffer.alloc(0)); return null; }
    remember(key, out);
    return out;
  } catch { return null; } finally { jobs--; }
}

/** برای آزمون */
function _reset() { cache.clear(); cached = 0; jobs = 0; }

module.exports = { MAX_JOBS, CACHE_BYTES, on, available, wants, webpOf, _reset };
