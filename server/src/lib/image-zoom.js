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
 *   ⛔ فقط وقتی اصل از `display` (۱۲۰۰px) بزرگ‌تر است؛ وگرنه zoom چیزی بیش از display ندارد ⇒ null.
 *   ⛔ از اصلِ همان نسخه (هرگز نسخهٔ دیگر)، جهتِ EXIF روی پیکسل، رنگ به sRGB با پروفایلِ خودِ عکس، بی EXIF/GPS؛ سقفِ پیکسلِ ورودی.
 *   ⛔ بار: یک ساختِ هم‌زمان؛ بیشتر ⇒ null (همان display، نه صف). هر خطا ⇒ null، هرگز ۵۰۰.
 *
 *   ensure(shopId, img, version) ⇒ { data, hash, mime } ِ نسخهٔ zoom، یا null
 *   withUrl(shopId, resolved, base) ⇒ `urls.zoom` ِ «کدام عکس» برای برنامه وقتی سرور می‌سازد
 */
const bank = require('./image-bank');
const bytes = require('./image-bytes');
const { lib } = require('./sharp-lib');

const FLAG = 'image_bank.zoom';
const SIDE = bank.DERIV.zoom.side;
const MAX_BYTES = bank.DERIV.zoom.bytes;
const QUALITIES = Object.freeze([85, 76, 68]);
const MAX_JOBS = 1;
const MAX_PIXELS = 40_000_000;

const flagOn = (shopId) => bank.flagOf(FLAG, shopId);

/** بزرگ‌ترین ضلعِ اصل (پس از چرخشِ EXIF همان است) از `display` بیشتر؟ */
function worthZoom(buf) {
  const info = bytes.inspect(buf);
  return Math.max(info.width, info.height) > bank.DERIV.display.side;
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

/** سرور این نسخه را می‌سازد؟ (پرچم، `sharp` و ضلعِ بلندِ اصلِ همان نسخه از فرادادهٔ بلاب — بی خواندنِ بایت‌ها) */
async function buildable(shopId, imageId, version) {
  if (!lib() || !(await flagOn(shopId))) return false;
  const b = await require('../db').one(
    `SELECT b.width, b.height FROM image_versions v JOIN image_blobs b ON b.hash = v.blob_hash WHERE v.image_id = $1 AND v.version = $2`,
    [imageId, version]);
  return !!b && Math.max(Number(b.width) || 0, Number(b.height) || 0) > bank.DERIV.display.side;
}

/**
 * «کدام عکس» ِ برنامه (`GET /api/shop/images/products/:id`): نسخهٔ بزرگی که هنوز نیست ولی سرور می‌سازد ⇒ همان نشانی؛ نخستین
 * خواندنش آن را یک بار می‌سازد. خاموش، بی sharp یا اصلِ کوچک ⇒ همان پاسخِ پیشین (`urls.zoom` = null).
 */
async function withUrl(shopId, out, base) {
  if (out && out.urls && !out.urls.zoom && await buildable(shopId, out.image_id, out.version)) {
    out.urls.zoom = bank.fileUrl(base, out.image_id, out.version, 'zoom');
  }
  return out;
}

let jobs = 0;
async function ensure(shopId, img, version) {
  const have = await bank.readFile(img.id, version, 'zoom');
  if (have) return have;
  if (!lib() || jobs >= MAX_JOBS || !(await flagOn(shopId))) return null;
  jobs++;
  try {
    const src = await bank.readFile(img.id, version, 'original');
    if (!src || !worthZoom(src.data)) return null;
    const out = await render(src.data);
    if (!out) return null;
    await bank.tx((c) => bank.putDerivative(c, img, version, 'zoom', out));
  } catch (e) {
    //  هم‌زمان ساخته شد (یک‌بارنویس) ⇒ همان که نشست؛ هر خطای دیگر ⇒ بی zoom
    if (!(e && e.code === 'derivative_exists')) return null;
  } finally {
    jobs--;
  }
  return bank.readFile(img.id, version, 'zoom');
}

/** برای آزمون */
function _jobs(n) { if (Number.isInteger(n)) jobs = n; return jobs; }

module.exports = { FLAG, SIDE, MAX_JOBS, flagOn, buildable, withUrl, ensure, _jobs };
