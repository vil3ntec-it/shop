'use strict';
/**
 * تپشِ بالای قیفِ ویترینِ وب — `POST /api/store/:slug/funnel/:step` (RPT-14؛ `lib/storefront-funnel.js`).
 *
 *   :step ∈ visit | view | cart — بی بدنه (`navigator.sendBeacon`)؛ پاسخِ ۲۰۴ ِ تهی و هرگز چیزی دربارهٔ شمارش نمی‌گوید.
 *
 * ⛔ سقفِ نرخِ IP پیش از هر کار (سطلِ جدای همین در). ویترینِ نبوده یا بسته (اشتراکِ منقضی/معلق/لغو) ⇒ همان ۴۰۴
 * `store_not_found` ِ `sf.open` (SUB-10: دستهٔ STORE_PAID ِ `paid-routes-public-entitlement.test.js`). پرچمِ
 * `shop.orders.online_report` ِ همان دکان خاموش ⇒ `next()`، همان پاسخِ مسیرِ ناشناختهٔ امروز و هیچ ردیف.
 * عمومی و بی‌توکن: هیچ دادهٔ مشتری نمی‌گیرد و نمی‌دهد؛ shop_id فقط از `slug`؛ IP و مرورگر فقط در HMAC ِ روزانه.
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const funnel = require('../lib/storefront-funnel');
const { rateLimit, clientIp } = require('../middleware/ratelimit');

const router = express.Router();

const beatLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-funnel' });

router.post('/:slug/funnel/:step', beatLimit, async (req, res, next) => {
  const store = await sf.open(req.params.slug);
  if (!(await funnel.flagOn(store.shop_id))) return next();
  const step = funnel.stepOf(req.params.step);
  await funnel.record(store.shop_id, step, { ip: clientIp(req), ua: req.get('user-agent') || '' });
  res.set('Cache-Control', 'no-store').status(204).end();
});

module.exports = router;
