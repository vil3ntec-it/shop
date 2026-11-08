'use strict';
/**
 *   PUT /api/sync/v1/photos/:productId/thumb   نسخهٔ کارتِ ویترین برای عکسِ همگام‌شدهٔ کالا (WEB-15، مهاجرتِ ۳۵۰)
 *       { data: <base64>, of: <hash ِ همان اصل در فهرستِ /photos> } ⇐ { ok, product_id, of, hash, size, width, height, mime, unchanged }
 *
 * سرور رمزگشای تصویر ندارد (`lib/image-pipeline.js`: `resize` = missing)؛ برنامه کارتِ ۳۲۰px را می‌سازد و درست پس از
 * رسیدنِ عکس (و برای عکس‌های کهنه سرِ فرصت) همین‌جا می‌فرستد. سنجش و نوشتن: `product-photos.putThumb`
 * (`validate`، `strip_metadata`، سقفِ بایت/ضلع/پیکسل، جهت، نسبتِ اصل). `of` ِ کهنه ⇒ ۴۰۹ `thumb_stale` بی نوشتن؛
 * `rev` بالا نمی‌رود و به دستگاه‌های دیگر خبری نمی‌رود (کارت فقط برای ویترین است).
 *
 * ⛔ جدا از `routes/sync-v1.js` (مشترک با پمپ) و فقط دکان. پیش از آن سوار می‌شود (`app.js`) ولی **فقط همین یک در**
 *    را دارد و همهٔ زنجیره روی خودِ در است، نه `router.use`: هیچ درخواستِ دیگرِ `/sync/v1` (پمپ یا دکان) از این
 *    پرونده نمی‌گذرد و سقف یا احرازش دو بار شمرده نمی‌شود. زنجیره همان درِ `/photos/:productId`: سقفِ آی‌پی پیش از
 *    احراز (همان سطلِ `syncip`)، `requireSyncAccount` (حساب از توکن)، فقط دکان (پمپ ⇒ ۴۰۳ `shop_only`)، سقفِ حساب
 *    (همان سطلِ `sync`)، `requireSyncWrite` (نقشِ بی اجازهٔ نوشتن ⇒ ۴۰۳).
 */
const express = require('express');
const config = require('../config');
const { rateLimit } = require('../middleware/ratelimit');
const { ApiError } = require('../middleware/errors');
const { requireSyncAccount, requireSyncWrite } = require('../lib/sync-v1-auth');
const { SHOP } = require('../lib/tenancy');
const photos = require('../lib/product-photos');

const router = express.Router();

const ipLimit = rateLimit({ max: config.rateLimit.syncIpMax, keyPrefix: 'syncip' });
const accountLimit = rateLimit({
  max: config.rateLimit.syncMax,
  keyPrefix: 'sync',
  key: (req) => (req.sync ? req.sync.app + ':' + req.sync.accountId : ''),
});

/** فقط حسابِ دکان (`app` و `accountKind` هر دو `tenancy.SHOP.app`)؛ پمپ و هر بخشِ دیگر ⇒ ۴۰۳ */
function shopOnly(req, res, next) {
  const s = req.sync;
  if (s && s.app === SHOP.app && s.accountKind === SHOP.app && s.accountId) return next();
  next(new ApiError(403, 'shop_only', 'نسخهٔ کارتِ ویترین فقط برای دکان است'));
}

/** base64 ِ کارت ⇒ بایت؛ فقط رشته (نه آرایه/شیء)، و سقف پیش از رمزگشایی */
function thumbBytes(b64) {
  if (typeof b64 !== 'string' || !b64) throw new ApiError(400, 'thumb_empty', 'نسخهٔ کارت خالی است');
  if (b64.length > Math.ceil(photos.THUMB_MAX_BYTES * 4 / 3) + 8) {
    throw new ApiError(413, 'thumb_too_large', 'نسخهٔ کارت بزرگ‌تر از ۶۴ کیلوبایت است');
  }
  return Buffer.from(b64, 'base64');
}

router.put('/photos/:productId/thumb', ipLimit, requireSyncAccount, shopOnly, accountLimit, requireSyncWrite, async (req, res, next) => {
  try {
    res.json({ ok: true, ...(await photos.putThumb(req.sync, req.params.productId, thumbBytes(req.body?.data), req.body?.of)) });
  } catch (err) { next(err); }
});

module.exports = router;
