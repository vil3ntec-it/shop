'use strict';
/**
 * WEB-16، تصمیمِ ۴-۲ «الف» ِ صاحب‌کار — عکسِ سبک‌ترِ ویترین با WebP، با کتابخانهٔ آزادِ libvips (`sharp`) روی خودِ سرور.
 *
 *   ⛔ پیش‌فرض خاموش: فقط با `STORE_WEBP=1` ِ سرور، و هرگز زیرِ Electron (سرورِ درونیِ برنامهٔ کامپیوتر). `sharp` وابستگیِ **اختیاری** است: اگر روی این ماشین نصب نشد یا بار نشد
 *      ⇒ همان JPEG ِ امروز، بی خطا (`available()` = false).
 *   ⛔ فقط وقتی مرورگر `Accept: image/webp` می‌فرستد؛ پاسخ `Vary: Accept` دارد تا کشِ مشترک دو نسخه را قاطی نکند.
 *   ⛔ تبدیل از همان بایت‌های امروزِ `thumb`/`display` (اصل هرگز)، بی EXIF/GPS، با سقفِ پیکسل؛ هر خطا ⇒ همان JPEG.
 *   ⛔ بار: حداکثر `MAX_JOBS` تبدیلِ هم‌زمان (بیشتر ⇒ همان JPEG، نه صف) و کشِ حافظه با سقفِ بایت (LRU، کلید = هشِ محتوا + اندازه).
 *
 *   ⛔ AVIF (WEB-16، همان تصمیمِ ۴-۲): فقط با `STORE_AVIF=1` ِ سرور (جدا، پیش‌فرض خاموش؛ رمزگذاریِ AVIF سنگین‌تر است) و
 *      فقط برای مرورگری که `image/avif` می‌پذیرد؛ وگرنه همان WebP یا JPEG. همان سقفِ کار، همان کش (کلید با قالب).
 *
 *   pick(accept) ⇒ 'avif' | 'webp' | null — قالبِ پاسخ برای همین مرورگر
 *   encodedOf(data, hash, size, fmt) ⇒ Buffer ِ همان قالب یا null (یعنی همان اصل را بفرست)
 *   webpOf(data, hash, size) = encodedOf(…, 'webp')
 */
const config = require('../config');

const MAX_JOBS = 2;
const CACHE_BYTES = 32 * 1024 * 1024;
const MAX_PIXELS = 40_000_000;
const QUALITY = Object.freeze({ thumb: 70, display: 78 });
//  AVIF با کیفیتِ عددیِ کمتر هم‌چشم با WebP است؛ effort ِ ۴ (از ۰ تا ۹) ⇒ زمانِ رمزگذاریِ معقول برای ۳۲۰ و اندازهٔ برگهٔ کالا
const AVIF_QUALITY = Object.freeze({ thumb: 50, display: 55 });

const { lib } = require('./sharp-lib');   //  بارکنندهٔ مشترک (اختیاری، هرگز زیرِ Electron)

const on = () => !!config.storefront.webp;
const avifOn = () => !!config.storefront.avif;
const available = () => (on() || avifOn()) && !!lib();
const accepts = (accept, type) => new RegExp(`(^|,)\\s*image\\/${type}\\s*(;|,|$)`, 'i').test(String(accept || ''));
const wants = (accept) => accepts(accept, 'webp');
/** قالبِ پاسخ: AVIF (اگر روشن و پذیرفته) ⇐ WebP (اگر روشن و پذیرفته) ⇐ null (همان اصل) */
const pick = (accept) => (avifOn() && accepts(accept, 'avif') ? 'avif' : on() && wants(accept) ? 'webp' : null);

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
const webpOf = (data, hash, size) => encodedOf(data, hash, size, 'webp');

async function encodedOf(data, hash, size, fmt = 'webp') {
  if (!available() || !Buffer.isBuffer(data) || !data.length || !QUALITY[size] || (fmt !== 'webp' && fmt !== 'avif')) return null;
  const key = `${hash}:${size}:${fmt}`;
  const hit = cache.get(key);
  if (hit) { cache.delete(key); cache.set(key, hit); return hit; }
  if (jobs >= MAX_JOBS) return null;
  jobs++;
  try {
    const out = await lib()(data, { limitInputPixels: MAX_PIXELS, failOn: 'error' })
      .rotate()                                    //  جهتِ EXIF روی پیکسل؛ خروجی بی فراداده
      [fmt]({ quality: (fmt === 'avif' ? AVIF_QUALITY : QUALITY)[size], effort: 4 })
      .toBuffer();
    //  فقط اگر واقعاً سبک‌تر شد؛ وگرنه همان اصل
    if (!out.length || out.length >= data.length) { remember(key, Buffer.alloc(0)); return null; }
    remember(key, out);
    return out;
  } catch { return null; } finally { jobs--; }
}

/** برای آزمون */
function _reset() { cache.clear(); cached = 0; jobs = 0; }

module.exports = { MAX_JOBS, CACHE_BYTES, on, avifOn, available, wants, pick, webpOf, encodedOf, _reset };
