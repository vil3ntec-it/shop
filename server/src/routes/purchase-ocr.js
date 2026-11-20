'use strict';
/**
 * PUR-12 / AI-10 — «از عکسِ فاکتور»: عکسِ فاکتورِ کاغذیِ تأمین‌کننده ⇒ خط‌های متن (Tesseract ِ همین سرور، `lib/purchase-ocr.js`).
 *
 *   GET  /api/shop/purchase-ocr/config   ⇒ `{ enabled }`؛ روشن ⇒ هم `permitted`، `paid`، `available`، `lang`، `missing[]`، `limits`
 *   POST /api/shop/purchase-ocr          بدنه: بایت‌های عکس (JPEG/PNG) ⇒ `{ lines: [{ text, conf }], lang, ms, notice }`
 *
 * ⛔ پیش‌فرض خاموش (`shop.purchase_ocr`، «پس از انتشار»): فقط `GET /config` جواب می‌دهد (`enabled: false`) تا برنامه دکمه را
 *    نشان ندهد؛ POST ۴۰۳ `purchase_ocr_disabled` پیش از خواندنِ بدنه.
 * ⛔ مجوز همان نوشتنِ خرید: `stock.adjust` (صاحب، مدیر، انباردار — همان سفارش و فاکتورِ خرید). shop_id فقط از توکن.
 * ⛔ پولی: هر خواندن پردازندهٔ سرورِ صاحب را می‌خورد ⇒ اشتراکِ فعال یا دورهٔ آزمایشی (یکی از `PAID_KEYS`)؛ وگرنه همان
 *    ۴۰۳ `subscription_expired|subscription_required` ِ `requireFeature`. فاکتور را بی عکس همیشه می‌شود نوشت (D-05).
 * ⛔ سقفِ نرخ: IP پیش از احراز، سپس دکان (۲۰ در ربع ساعت و ۱۰۰ در روز)؛ همزمانیِ سراسری در `lib/purchase-ocr.js`.
 * ⛔ هیچ ردیف، فایل، سابقه یا لاگی از عکس یا متن؛ پاسخ `no-store`.
 */
const express = require('express');
const config = require('../config');
const ocr = require('../lib/purchase-ocr');
const { can } = require('../lib/permissions');
const { PAID_KEYS } = require('../lib/features');
const { entitlementOf } = require('../lib/entitlement');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { forbidden, ApiError } = require('../middleware/errors');
const { rawBody } = require('../lib/raw-body');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'purchase-ocr-ip' }));
router.use(requireUser, requireShop, requirePermission('stock.view'));

const NOTICE = 'متن از روی عکس خوانده شده؛ هر ردیف را خودتان ببینید و تأیید کنید. عکس و متن روی سرور نگه داشته نمی‌شوند.';
const DAY = 24 * 60 * 60 * 1000;

const needFlag = async (req, res, next) => {
  if (!(await ocr.enabled(req.shopId))) return next(forbidden('خواندنِ عکسِ فاکتور برای این دکان روشن نیست', 'purchase_ocr_disabled'));
  next();
};

/** اشتراکِ فعال یا آزمایشی — همان پیام‌های `requireFeature` */
async function paidOf(shopId) {
  const ent = await entitlementOf(shopId);
  return { paid: ent.features.some((k) => PAID_KEYS.includes(k)), ent };
}
const needPaid = async (req, res, next) => {
  const { paid, ent } = await paidOf(req.shopId);
  if (paid) return next();
  next(ent.trial.used && !ent.trial.active
    ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
    : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
};

const shopKey = (req) => (req.shopId ? `shop:${req.shopId}` : '');
const shopLimit = rateLimit({ max: 20, keyPrefix: 'purchase-ocr', key: shopKey });
const dayLimit = rateLimit({ max: 100, windowMs: DAY, keyPrefix: 'purchase-ocr-day', key: shopKey });
const raw = express.raw({ type: () => true, limit: ocr.LIMITS.MAX_BYTES });
/** بدنهٔ خام؛ بزرگ‌تر از سقف ⇒ ۴۱۳ ِ فارسیِ خودمان، پیش از خواندنِ بقیه (`Content-Length`) */
const body = (req, res, next) => raw(req, res, (err) => {
  if (!err) return next();
  if (err.type === 'entity.too.large') {
    return next(new ApiError(413, 'too_large', `عکس بزرگ‌تر از ${Math.round(ocr.LIMITS.MAX_BYTES / 1024 / 1024)} مگابایت است`));
  }
  next(new ApiError(400, 'image_empty', 'عکس خوانده نشد'));
});

router.get('/config', async (req, res) => {
  res.set('Cache-Control', 'no-store');
  if (!(await ocr.enabled(req.shopId))) return res.json({ enabled: false });
  const st = await ocr.status();
  res.json({
    enabled: true,
    permitted: can(req.role, 'stock.adjust'),
    paid: (await paidOf(req.shopId)).paid,
    available: !!st.lang,
    lang: st.lang,
    missing: st.missing,
    limits: { maxBytes: ocr.LIMITS.MAX_BYTES, maxSide: ocr.LIMITS.MAX_SIDE, minSide: ocr.LIMITS.MIN_SIDE },
  });
});

router.post('/', needFlag, requirePermission('stock.adjust'), needPaid, shopLimit, dayLimit, body, async (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  try {
    const out = await ocr.read(rawBody(req));
    res.json({ ...out, notice: NOTICE });
  } catch (e) {
    if (e && e.code === 'ocr_busy') res.set('Retry-After', String(e.retryAfter || 5));
    next(e);
  }
});

module.exports = router;
