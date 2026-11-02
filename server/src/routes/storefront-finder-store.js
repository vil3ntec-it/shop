'use strict';
/**
 * ویترین — گزینه‌های فیلترِ کاتالوگ (WEB-07، `lib/storefront-filters.js`) و «خبرم کن وقتی موجود/ارزان شد»
 * (WEB-10، `lib/storefront-alerts.js`) زیرِ `/api/store/:slug/…`. خودِ فیلتر روی `GET /:slug/products` ِ
 * `routes/storefront.js` است.
 *
 *   GET    /:slug/filters                     برندها، بازهٔ قیمت، مشخصه‌ها، «فقط موجود» معنا دارد؟
 *   GET    /:slug/alerts                      هشدارهای همین شمارهٔ تأییدشده در همین ویترین
 *   PUT    /:slug/alerts/:productId/:kind     ثبت (kind = stock | price) — تکرار بی‌اثر (۲۰۰)، تازه ۲۰۱
 *   DELETE /:slug/alerts/:productId/:kind     لغو
 *
 * ⛔ پرچمِ خاموش (یا ویترینِ نبوده/بسته) ⇒ `next('route')` پیش از هر سقف و شماره: همان پاسخِ مسیرِ
 *    ناشناختهٔ main، بیت‌به‌بیت. شماره فقط از بلیتِ شماره یا نشستِ حساب (همان که سفارش با آن ثبت می‌شود).
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const filters = require('../lib/storefront-filters');
const alerts = require('../lib/storefront-alerts');
const { rateLimit } = require('../middleware/ratelimit');
const { ApiError } = require('../middleware/errors');

const router = express.Router();

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-finder-read' });
const writeLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-alerts' });
//  جدا از سطلِ IP: هر شماره حداکثر `WRITES_PER_HOUR` ثبت/لغو در ساعت
const phoneLimit = rateLimit({
  max: alerts.WRITES_PER_HOUR, windowMs: 60 * 60 * 1000, keyPrefix: 'store-alerts-phone', key: (req) => req.verifiedPhone,
});

//  ویترینِ نبوده یا بسته (اشتراکِ تمام‌شده) ⇒ همان ۴۰۴ `store_not_found` ِ `sf.open` (SUB-10)؛ پرچمِ خاموش ⇒ مسیرِ بعدی، همان امروز
const gate = (on) => async (req, res, next) => {
  const s = await sf.open(req.params.slug);
  if (!(await on(s.shop_id))) return next('route');
  req.store = s;
  res.set('Cache-Control', 'no-store');
  next();
};
const filtersOn = gate(filters.flagOn);
const alertsOn = gate(alerts.flagOn);

const verified = async (req, res, next) => {
  const phone = await sf.verifiedPhone(req.get('x-store-ticket') || '', req.get('x-store-session') || '');
  if (!phone) throw new ApiError(401, 'verification_required', 'برای «خبرم کن»، شمارهٔ خود را با کد تأیید کنید');
  req.verifiedPhone = phone;
  next();
};

router.get('/:slug/filters', filtersOn, readLimit, async (req, res) => {
  res.json(await filters.facets(req.store.shop_id));
});

router.get('/:slug/alerts', alertsOn, readLimit, verified, async (req, res) => {
  res.json(await alerts.list(req.store, req.verifiedPhone));
});

router.put('/:slug/alerts/:productId/:kind', alertsOn, writeLimit, verified, phoneLimit, async (req, res) => {
  const out = await alerts.subscribe(req.store, req.verifiedPhone, req.params.productId, req.params.kind, {
    lang: alerts.langOf(req.get('accept-language')),
  });
  res.status(out.created ? 201 : 200).json(out);
});

router.delete('/:slug/alerts/:productId/:kind', alertsOn, writeLimit, verified, phoneLimit, async (req, res) => {
  res.json(await alerts.unsubscribe(req.store, req.verifiedPhone, req.params.productId, req.params.kind));
});

module.exports = router;
