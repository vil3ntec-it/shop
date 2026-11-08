'use strict';
/**
 * زنجیرهٔ پردازشِ عکس — رابطِ مرحله‌ها با وضعیتِ راست (v3 ۳۴.۱، ۳۶.۴، ۳۹).
 *
 * هر مرحله یکی از سه وضعیت را دارد و همان به برنامه گفته می‌شود:
 *   available         روی همین سرور، بی سرویسِ بیرونی
 *   missing           ساختنی است ولی ابزارش این‌جا نیست (نسخهٔ کوچک را برنامه می‌سازد)
 *   blocked_external  به سرویس/مدلِ بیرونی نیاز دارد ⇒ BLOCKED-EXTERNAL (D-16: پولی، پیش‌فرض خاموش)
 *
 * ⛔ هیچ مرحله‌ای عکس را به بیرون نمی‌فرستد. `run` برای مرحلهٔ ناموجود
 *    خطای ۵۰۳ِ `step_unavailable` می‌دهد و هیچ ردیف، صف یا هزینه‌ای نمی‌سازد —
 *    پس قطع یا نبودِ provider ورودِ کالا و عکسِ اصل را نمی‌خواباند (۳۴.۳ بندِ ۴).
 */
const { ApiError } = require('../middleware/errors');
const bytes = require('./image-bytes');

const STEPS = [
  { id: 'validate', status: 'available', note: 'نوعِ واقعی از محتوا، ابعاد، ساختارِ قطعه‌ها، CRCِ PNG، سقفِ پیکسل' },
  { id: 'dedupe_hash', status: 'available', note: 'sha256ِ محتوا — تکراری فقط نشان داده می‌شود، ادغام نمی‌شود' },
  { id: 'strip_metadata', status: 'available', note: 'حذفِ EXIF/GPS/XMP/IPTC از نسخه‌های عمومی؛ ICC می‌ماند' },
  { id: 'resize', status: 'missing', note: 'سرور رمزگشای تصویر ندارد؛ thumbnail/نمایش/بزرگ را برنامه می‌سازد و سرور می‌سنجد' },
  { id: 'encode_webp', status: 'missing', note: 'رمزگذارِ WebP روی سرور نیست؛ WebPِ ساختهٔ برنامه پذیرفته می‌شود' },
  { id: 'encode_avif', status: 'missing', note: 'رمزگذارِ AVIF نیست' },
  { id: 'perceptual_hash', status: 'missing', note: 'شباهتِ دیداری بی رمزگشا ممکن نیست' },
  { id: 'quality_metrics', status: 'missing', note: 'تاری/نور/پس‌زمینه بی رمزگشا سنجیده نمی‌شود؛ فقط ابعادِ کم' },
  { id: 'enhance', status: 'blocked_external', note: 'بهبودِ نور/وضوح با مدل — provider ندارد' },
  { id: 'background_removal', status: 'blocked_external', note: 'حذفِ پس‌زمینه — provider ندارد' },
  { id: 'upscale', status: 'blocked_external', note: 'بزرگ‌نمایی با مدل — provider ندارد' },
  { id: 'inpainting', status: 'blocked_external', note: 'ترمیم با مدل — provider ندارد' },
];

/**
 * فهرستِ بستهٔ کارِ مرحله‌های `available` — `Map`، نه شیءِ ساده. نامِ مرحله از بدنهٔ درخواست می‌آید
 * (`POST /api/shop/images/:id/process`)؛ `stepOf` پیش‌تر هم آن را به STEPS می‌بست، ولی `LOCAL[id]` ِ
 * شیءِ ساده این را نشان نمی‌داد (CodeQL `js/unvalidated-dynamic-method-call`: `__proto__`، `constructor`).
 * حالا کار با `s.id` ِ خودِ سرور از `Map` گرفته می‌شود که جز همین سه هیچ ندارد.
 */
const LOCAL = new Map([
  ['validate', (buf) => bytes.inspect(buf)],
  ['strip_metadata', (buf) => bytes.stripMetadata(buf)],
  ['dedupe_hash', (buf) => require('crypto').createHash('sha256').update(buf).digest('hex')],
]);

function describe() {
  return STEPS.map((s) => ({ ...s }));
}

function stepOf(id) {
  return STEPS.find((s) => s.id === id) || null;
}

/** اجرای یک مرحله؛ ناموجود ⇒ ۵۰۳ و هیچ اثری. */
function run(id, buf) {
  const s = stepOf(id);
  if (!s) throw new ApiError(400, 'unknown_step', 'مرحلهٔ ناشناخته');
  if (s.status !== 'available') {
    const e = new ApiError(503, 'step_unavailable',
      s.status === 'blocked_external'
        ? 'این پردازش به سرویسِ بیرونی نیاز دارد و فعال نیست — هیچ عکسی به بیرون فرستاده نشد'
        : 'این پردازش روی سرور در دسترس نیست');
    e.step = id;
    e.state = s.status;
    throw e;
  }
  const work = LOCAL.get(s.id);
  //  مرحلهٔ `available` ِ بی کار (ناهم‌خوانیِ STEPS و LOCAL) همان ۵۰۳ است، نه TypeError
  if (typeof work !== 'function') throw new ApiError(503, 'step_unavailable', 'این پردازش روی سرور در دسترس نیست');
  return work(buf);
}

module.exports = { STEPS, describe, stepOf, run };
