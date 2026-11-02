'use strict';
/**
 * زمانِ تقریبیِ تحویلِ ویترین — `GET /api/store/:slug/eta` (WEB-04؛ `lib/storefront-eta.js`).
 *
 * ⛔ پیش‌فرض خاموش: پرچمِ `shop.storefront.eta` ِ همان دکان خاموش ⇒ `next()` پیش از هر سرآیند، همان پاسخِ مسیرِ
 * ناشناختهٔ امروز. ویترینِ نبوده یا بسته (اشتراکِ منقضی/معلق/لغو) ⇒ همان ۴۰۴ `store_not_found` ِ `sf.open`
 * (SUB-10: دستهٔ STORE_PAID ِ `paid-routes-public-entitlement.test.js`).
 * ⛔ عمومی و بی‌توکن، ولی فقط بازهٔ دقیقه‌ها: نه شمارِ صف و پیک، نه هیچ دادهٔ مشتری ⇒ کشِ کوتاهِ عمومی
 * (۳۰ ثانیه، همان کشِ سرور). shop_id فقط از `slug`. سقف: سطلِ خواندنِ جدای همین در به‌ازای IP.
 *
 *   GET /:slug/eta   `{eta: {available, reason?, approximate, delivery, pickup, zones:[{id,minMinutes,maxMinutes}], updatedAt}}`
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const eta = require('../lib/storefront-eta');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-eta-read' });

router.get('/:slug/eta', readLimit, async (req, res, next) => {
  const store = await sf.open(req.params.slug);
  const out = await eta.cachedPublic(store);
  if (out === undefined) return next();
  const ttl = Math.max(0, Math.round(config.storefront.etaCacheMs / 1000));
  res.set('Cache-Control', ttl > 0 ? `public, max-age=${ttl}` : 'no-store').json({ eta: out });
});

module.exports = router;
