'use strict';
/**
 * امتیازِ مشتری به پیک — `/api/store/:slug/orders/:token/rating` (DLV-18).
 *
 * ⛔ پیش‌فرض خاموش: بی ویترینِ روشن (`sf.open`) یا بی پرچمِ `shop.delivery_ratings` ِ همان دکان ⇒ ۴۰۴
 * (همان «سفارش پیدا نشد») و هیچ ردیفی نوشته نمی‌شود.
 * ⛔ اجازه همان توکنِ امضاشدهٔ پیگیریِ سفارش است (با همان قاعدهٔ انقضا و باطل‌شدنِ لینک)؛ shop_id فقط از
 * `slug` و سفارش فقط از توکن، هرگز از بدنه. سقف: خواندن سطلِ خواندنِ ویترین، نوشتن سطلِ ثبتِ ویترین.
 *
 *   GET  /:slug/orders/:token/rating   `{canRate, rating, tags}`
 *   POST /:slug/orders/:token/rating   `{stars, tags?}` — فقط پس از «تحویل شد» و تا ۷ روز، یک بار
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const ratings = require('../lib/delivery-ratings');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-rating-read' });
const writeLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-rating-write' });

const store = async (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  req.store = await sf.open(req.params.slug);
  next();
};

router.get('/:slug/orders/:token/rating', readLimit, store, async (req, res) => {
  res.json(await ratings.customerState(req.store, req.params.token));
});

router.post('/:slug/orders/:token/rating', writeLimit, store, async (req, res) => {
  const out = await ratings.rateByCustomer(req.store, req.params.token, req.body);
  res.status(out.replayed ? 200 : 201).json(out);
});

module.exports = router;
