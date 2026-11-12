'use strict';
/**
 * WEB-16، تصمیمِ ۴-۲ «الف» ِ صاحب‌کار — عکسِ سبک‌ترِ ویترین با WebP، با کتابخانهٔ آزادِ libvips (`sharp`) روی خودِ سرور.
 *
 *   ⛔ پیش‌فرض خاموش: فقط با `STORE_WEBP=1` ِ سرور، و هرگز زیرِ Electron (سرورِ درونیِ برنامهٔ کامپیوتر). `sharp` وابستگیِ **اختیاری** است: اگر روی این ماشین نصب نشد یا بار نشد
 *      ⇒ همان JPEG ِ امروز، بی خطا (`available()` = false).
 *   ⛔ فقط وقتی مرورگر `Accept: image/webp` می‌فرستد؛ پاسخ `Vary: Accept` دارد تا کشِ مشترک دو نسخه را قاطی نکند.
 *   ⛔ تبدیل از همان بایت‌های امروزِ `thumb`/`display` (اصل هرگز)، بی EXIF/GPS، با سقفِ پیکسل، و **کوچک‌شده تا ضلعِ همان اندازه**
 *      (`SIDE`: thumb ۴۰۰، display ۱۲۰۰؛ هرگز بزرگ‌تر) — عکسِ قدیمیِ همگام‌شده سقفِ ابعاد ندارد؛ هر خطا ⇒ همان JPEG.
 *   ⛔ بار: حداکثر `MAX_JOBS` تبدیلِ هم‌زمان، AVIF فقط `AVIF_JOBS` (بیشتر ⇒ قالبِ بعدی یا همان JPEG، نه صف)؛ سنجش و افزودنِ
 *      شمارنده بی هیچ await در میان. کشِ حافظه با سقفِ بایت (LRU، کلید = هشِ محتوا + اندازه + قالب)؛ «سبک‌تر نشد» هم یاد
 *      می‌ماند و **هرگز بافرِ خالی برنمی‌گرداند** (بازبینیِ ۵۴ب: درخواستِ دوم ۲۰۰ ِ بی بدنه بود).
 *
 *   ⛔ AVIF (WEB-16، همان تصمیمِ ۴-۲): فقط با `STORE_AVIF=1` ِ سرور (جدا، پیش‌فرض خاموش؛ رمزگذاریِ AVIF سنگین‌تر است) و
 *      فقط برای مرورگری که `image/avif` می‌پذیرد؛ وگرنه همان WebP یا JPEG. ورودیِ بیش از `AVIF_MAX_PIXELS` (از `metadata()` ِ ارزان،
 *      پیش از رمزگشایی) ⇒ بی AVIF (یاد می‌ماند؛ مسیر WebP یا JPEG می‌دهد): AVIF ِ ۴۰ مگاپیکسل ۱۴ ثانیه و ۹۰۰MB بود.
 *
 *   formats(accept) ⇒ ['avif'?, 'webp'?] — قالب‌های روشن و پذیرفتهٔ همین مرورگر، به ترتیب؛ نخستینِ ساخته‌شده فرستاده می‌شود
 *   pick(accept) ⇒ 'avif' | 'webp' | null — نخستینِ همان
 *   encodedOf(data, hash, size, fmt) ⇒ Buffer ِ همان قالب یا null (یعنی همان اصل را بفرست)
 *   webpOf(data, hash, size) = encodedOf(…, 'webp')
 */
const config = require('../config');

const MAX_JOBS = 2;
const AVIF_JOBS = 1;
const AVIF_MAX_PIXELS = 4_000_000;
//  همان ضلعِ نسخه‌های بانک (`image-bank` DERIV: thumb ۴۰۰، display ۱۲۰۰)
const SIDE = Object.freeze({ thumb: 400, display: 1200 });
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
/** قالب‌های پاسخ به ترتیب: AVIF (اگر روشن و پذیرفته) ⇐ WebP (اگر روشن و پذیرفته)؛ تهی ⇒ همان اصل */
const formats = (accept) => [
  ...(avifOn() && accepts(accept, 'avif') ? ['avif'] : []),
  ...(on() && wants(accept) ? ['webp'] : []),
];
const pick = (accept) => formats(accept)[0] || null;

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

const SKIP = Buffer.alloc(0);   //  یادِ «این قالب نه» (سبک‌تر نشد، یا AVIF ِ بیش از سقفِ پیکسل)
let jobs = 0;
let avifJobs = 0;
const webpOf = (data, hash, size) => encodedOf(data, hash, size, 'webp');

async function encodedOf(data, hash, size, fmt = 'webp') {
  if (!available() || !Buffer.isBuffer(data) || !data.length || !QUALITY[size] || (fmt !== 'webp' && fmt !== 'avif')) return null;
  const key = `${hash}:${size}:${fmt}`;
  const hit = cache.get(key);
  if (hit !== undefined) {
    cache.delete(key); cache.set(key, hit);
    return hit.length ? hit : null;   //  ⛔ یادِ «نه» هرگز بافرِ خالی نیست
  }
  const avif = fmt === 'avif';
  //  ⛔ سدِ هم‌زمانی: سنجش و افزودن بی هیچ await در میان — وگرنه درخواست‌های هم‌زمان همه از سد می‌گذشتند
  if (jobs >= MAX_JOBS || (avif && avifJobs >= AVIF_JOBS)) return null;
  jobs++;
  if (avif) avifJobs++;
  try {
    const sharp = lib();
    if (avif) {
      const m = await sharp(data, { limitInputPixels: MAX_PIXELS }).metadata();
      if (!(Number(m.width) * Number(m.height) <= AVIF_MAX_PIXELS)) { remember(key, SKIP); return null; }
    }
    const out = await sharp(data, { limitInputPixels: MAX_PIXELS, failOn: 'error' })
      .rotate()                                    //  جهتِ EXIF روی پیکسل؛ خروجی بی فراداده
      .resize({ width: SIDE[size], height: SIDE[size], fit: 'inside', withoutEnlargement: true })
      [fmt]({ quality: (avif ? AVIF_QUALITY : QUALITY)[size], effort: 4 })
      .toBuffer();
    //  فقط اگر واقعاً سبک‌تر شد؛ وگرنه همان اصل
    if (!out.length || out.length >= data.length) { remember(key, SKIP); return null; }
    remember(key, out);
    return out;
  } catch { return null; } finally {
    jobs--;
    if (avif) avifJobs--;
  }
}

/** برای آزمون */
function _reset() { cache.clear(); cached = 0; jobs = 0; avifJobs = 0; }

module.exports = {
  MAX_JOBS, AVIF_JOBS, AVIF_MAX_PIXELS, SIDE, CACHE_BYTES, on, avifOn, available, wants, formats, pick, webpOf, encodedOf, _reset,
};
