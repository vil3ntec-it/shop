'use strict';
/**
 * تخفیفِ ویترین برای مشتری — `/api/store/:slug/promos…` (MKT-01، MKT-09).
 *
 * ⛔ پیش‌فرض خاموش: بی ویترینِ روشن (`sf.open`) یا بی پرچمِ `shop.storefront.promos` ⇒ ۴۰۴ و هیچ داده‌ای.
 * ⛔ هیچ کدی فهرست نمی‌شود؛ پیش‌نمایش چیزی نمی‌نویسد و مصرف نمی‌سازد (مصرف فقط در ثبتِ سفارش).
 *
 *   GET  /:slug/promos         تخفیف‌های خودکارِ زنده (نام، نوع، پایانِ بازه) و «کد پذیرفته می‌شود؟»
 *   POST /:slug/promos/quote   `{lines:[{productId, qtyMilli}], promoCode?}` ⇒ تخفیفِ همان حسابِ ثبت
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const promos = require('../lib/storefront-promos');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-promos-read' });
//  حدس زدنِ کد با پیش‌نمایش: همان سطلِ ثبتِ ویترین
const quoteLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-promos-quote' });

const store = async (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  req.store = await sf.open(req.params.slug);
  next();
};

router.get('/:slug/promos', readLimit, store, async (req, res) => {
  res.json(await promos.publicList(req.store));
});

router.post('/:slug/promos/quote', quoteLimit, store, async (req, res) => {
  res.json(await promos.quote(req.store, req.body));
});

module.exports = router;
