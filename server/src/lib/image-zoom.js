'use strict';
/**
 * IMG-11 — نسخهٔ بزرگِ «zoom» ِ عکسِ بانک (بزرگ‌ترین ضلع ۲۴۰۰px، همان `DERIV.zoom`) روی خودِ سرور با `sharp` ِ اختیاری،
 * برای بزرگ‌نماییِ برگهٔ کالای ویترین و برنامه.
 *
 *   ⛔ پرچمِ پیش‌فرض‌خاموشِ `image_bank.zoom` (دکان/`all`): هر نسخهٔ ساخته‌شده یک بلابِ تا ۲MB روی دیسک است. خاموش یا بی
 *      `sharp` (یا زیرِ Electron) ⇒ null و همه‌چیز مثلِ امروز (کلاینت همان display را بزرگ می‌کند).
 *   ⛔ همان جای نسخه‌های دیگر: `image_derivatives` با size = 'zoom' از راهِ `bank.putDerivative` — همان سنجه‌ها (نوعِ واقعی، سقفِ
 *      ضلع و بایت، نه بزرگ‌تر از اصل، نسبتِ ابعاد ۲٪، orientation = 1، فرادادهٔ حساس برداشته) و یک‌بارنویس: هر (عکس، نسخه) حداکثر
 *      یک بار ساخته می‌شود؛ zoom ای که برنامه پیش‌تر فرستاده (`PUT …/v/:n/zoom`) دست نمی‌خورد.
 *   ⛔ فقط وقتی اصل از `display` (۱۲۰۰px) بزرگ‌تر است (از فرادادهٔ پایگاه، پیش از خواندنِ بایت‌ها)؛ وگرنه zoom چیزی بیش از
 *      display ندارد ⇒ null.
 *   ⛔ فقط عکسِ **خودِ همین دکان** (`space = 'shop'`، `owner_shop_id` همان): عکسِ بانکِ مرکزی هرگز — اصلش به دکان نمی‌رسد و
 *      zoom اش روی داراییِ مشترک فقط از راهِ بارگذاریِ مدیر (بازبینیِ ۵۴).
 *   ⛔ از اصلِ همان نسخه (هرگز نسخهٔ دیگر)، جهتِ EXIF روی پیکسل، رنگ به sRGB با پروفایلِ خودِ عکس، بی EXIF/GPS؛ سقفِ پیکسلِ ورودی.
 *   ⛔ بار: یک ساختِ هم‌زمان (سنجش و افزودنِ شمارنده بی هیچ await در میان)؛ بیشتر ⇒ null (همان display، نه صف). ساختِ ناکام
 *      (در هیچ کیفیتی زیرِ سقفِ بایت نشد، رمزگشایی نشد، سنجهٔ بانک رد کرد) در یادِ کران‌دار با مهلت می‌ماند و با هر درخواستِ
 *      عمومی از نو ساخته نمی‌شود. پرچمِ خاموش ⇒ `sharp` حتی بار نمی‌شود. هر خطا ⇒ null، هرگز ۵۰۰.
 *
 *   ensure(shopId, img, version) ⇒ { data, hash, mime } ِ نسخهٔ zoom، یا null
 *   withUrl(shopId, resolved, base) ⇒ `urls.zoom` ِ «کدام عکس» برای برنامه وقتی سرور می‌سازد
 */
const db = require('../db');
const bank = require('./image-bank');
const { lib } = require('./sharp-lib');

const FLAG = 'image_bank.zoom';
const SIDE = bank.DERIV.zoom.side;
const MAX_BYTES = bank.DERIV.zoom.bytes;
const QUALITIES = Object.freeze([85, 76, 68]);
const MAX_JOBS = 1;
const MAX_PIXELS = 40_000_000;
const FAIL_TTL_MS = 6 * 60 * 60 * 1000;
const FAIL_MAX = 1000;

const flagOn = (shopId) => bank.flagOf(FLAG, shopId);

/**
 * یادِ شکست — (عکس، نسخه) ⇒ زمانِ ثبت؛ کران‌دار (`FAIL_MAX`، کهنه‌ترین کنار) و با مهلت (`FAIL_TTL_MS`). فقط در حافظهٔ همین
 * فرایند: ری‌استارت ⇒ یک آزمونِ دیگر. بی آن هر `GET …/zoom` ِ بی‌توکن سه رمزگذاریِ mozjpeg (چند ثانیه CPU) را از نو می‌کرد.
 */
const failed = new Map();
const keyOf = (imageId, version) => `${imageId}@${version}`;
function hasFailed(key) {
  const at = failed.get(key);
  if (at === undefined) return false;
  if (Date.now() - at < FAIL_TTL_MS) return true;
  failed.delete(key);
  return false;
}
function noteFailure(key) {
  failed.delete(key);
  failed.set(key, Date.now());
  while (failed.size > FAIL_MAX) failed.delete(failed.keys().next().value);
}

/** اصلِ همان نسخه از عکسِ خودِ همین دکان و بزرگ‌تر از `display`؟ (فرادادهٔ بلاب، بی خواندنِ بایت‌ها؛ عکسِ مرکزی ⇒ false) */
async function sized(shopId, imageId, version) {
  const b = await db.one(
    `SELECT b.width, b.height FROM images i JOIN image_versions v ON v.image_id = i.id JOIN image_blobs b ON b.hash = v.blob_hash
      WHERE i.id = $1 AND v.version = $2 AND i.space = 'shop' AND i.owner_shop_id = $3`,
    [imageId, version, shopId]);
  return !!b && Math.max(Number(b.width) || 0, Number(b.height) || 0) > bank.DERIV.display.side;
}

/** خودِ ساختن: JPEG با سقفِ ضلع و بایت — یا null */
async function render(buf) {
  const sharp = lib();
  for (const quality of QUALITIES) {
    const out = await sharp(buf, { limitInputPixels: MAX_PIXELS, failOn: 'error' })
      .rotate()                                                                  //  جهتِ EXIF روی پیکسل
      .resize({ width: SIDE, height: SIDE, fit: 'inside', withoutEnlargement: true })
      .toColourspace('srgb')                                                     //  رنگ با پروفایلِ خودِ عکس به sRGB
      .jpeg({ quality, mozjpeg: true })
      .toBuffer();
    if (out.length && out.length <= MAX_BYTES) return out;
  }
  return null;
}

/**
 * سرور این نسخه را می‌سازد؟ اول پرچم (خاموش ⇒ `sharp` بار هم نمی‌شود)، سپس `sharp`، یادِ شکست و اصلِ خودی و بزرگ‌تر از display.
 */
async function buildable(shopId, imageId, version) {
  if (!(await flagOn(shopId)) || !lib() || hasFailed(keyOf(imageId, version))) return false;
  return sized(shopId, imageId, version);
}

/**
 * «کدام عکس» ِ برنامه (`GET /api/shop/images/products/:id`): نسخهٔ بزرگی که هنوز نیست ولی سرور می‌سازد ⇒ همان نشانی؛ نخستین
 * خواندنش آن را یک بار می‌سازد. خاموش، بی sharp، اصلِ کوچک، عکسِ مرکزی یا ساختِ ناکامِ یادشده ⇒ همان پاسخِ پیشین (`urls.zoom` = null).
 */
async function withUrl(shopId, out, base) {
  if (out && out.urls && !out.urls.zoom && await buildable(shopId, out.image_id, out.version)) {
    out.urls.zoom = bank.fileUrl(base, out.image_id, out.version, 'zoom');
  }
  return out;
}

let jobs = 0;
async function ensure(shopId, img, version) {
  try {
    const have = await bank.readFile(img.id, version, 'zoom');
    if (have) return have;
    if (!(await buildable(shopId, img.id, version))) return null;
    //  ⛔ سدِ هم‌زمانی: سنجش و افزودن بی هیچ await در میان — وگرنه درخواست‌های هم‌زمان همه از سد می‌گذشتند
    if (jobs >= MAX_JOBS) return null;
    jobs++;
    try {
      if (!(await build(img, version))) return null;
    } finally {
      jobs--;
    }
    return await bank.readFile(img.id, version, 'zoom');
  } catch {
    return null;   //  خطای گذرا (پایگاه، دیسک) ⇒ بی zoom، هرگز ۵۰۰؛ یاد نمی‌شود
  }
}

/** ساختن و نشاندن ⇒ true اگر zoom اکنون هست (همین یا نوشتنِ هم‌زمانِ دیگری)؛ شکستِ قطعی یاد می‌شود */
async function build(img, version) {
  const src = await bank.readFile(img.id, version, 'original');
  if (!src) return false;
  const key = keyOf(img.id, version);
  const out = await render(src.data).catch(() => null);
  if (!out) { noteFailure(key); return false; }   //  بیش از سقفِ بایت در هر کیفیت، یا رمزگشایی نشد
  try {
    await bank.tx((c) => bank.putDerivative(c, img, version, 'zoom', out));
  } catch (e) {
    //  یک‌بارنویس: هم‌زمان نشست (`derivative_exists`، یا یکتاییِ پایگاه وقتی هر دو پیش از دیگری درج کردند) ⇒ همان که نشست
    if (e && (e.code === 'derivative_exists' || e.code === '23505')) return true;
    if (e && e.status >= 400 && e.status < 500) noteFailure(key);   //  سنجهٔ بانک رد کرد ⇒ قطعی
    throw e;
  }
  return true;
}

/** برای آزمون */
function _jobs(n) { if (Number.isInteger(n)) jobs = n; return jobs; }
const _failed = () => failed.size;
function _reset() { jobs = 0; failed.clear(); }

module.exports = { FLAG, SIDE, MAX_JOBS, FAIL_TTL_MS, FAIL_MAX, flagOn, buildable, withUrl, ensure, _jobs, _failed, _reset };
