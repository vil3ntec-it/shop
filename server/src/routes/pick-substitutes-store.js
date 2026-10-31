'use strict';
/**
 * تأییدِ جایگزین توسطِ مشتری — `/api/store/:slug/orders/:token/substitutions…` (PCK-05، S37).
 *
 * ⛔ پیش‌فرض خاموش: بی ویترینِ روشن (`sf.open`) یا بی پرچمِ `shop.orders.picking` ِ همان دکان ⇒ ۴۰۴
 * (همان «سفارش پیدا نشد») و هیچ ردیفی دست نمی‌خورد.
 * ⛔ اجازه همان توکنِ امضاشدهٔ پیگیریِ سفارش است (همان لینکی که مشتری از رسید یا واتس‌اپ دارد)؛
 * shop_id فقط از `slug` و سفارش فقط از توکن، هرگز از بدنه.
 * ⛔ سقف: خواندن سطلِ خواندنِ ویترین، تصمیم سطلِ ثبتِ ویترین — به‌ازای IP.
 *
 *   GET  /:slug/orders/:token/substitutions          پیشنهادهای جایگزینِ این سفارش
 *   POST /:slug/orders/:token/substitutions/:subId   `{decision: accept|reject}`
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const picking = require('../lib/order-picking');
const subs = require('../lib/pick-substitutes');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-subs-read' });
const decideLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-subs-decide' });

/** ویترینِ روشن + پرچمِ جمع‌آوریِ همان دکان — یا ۴۰۴. پاسخ‌ها کش نمی‌شوند. */
const store = async (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  req.store = await sf.open(req.params.slug);
  if (!(await picking.flagOf(req.store.shop_id))) throw notFound('سفارش پیدا نشد', 'order_not_found');
  next();
};

router.get('/:slug/orders/:token/substitutions', readLimit, store, async (req, res) => {
  res.json(await subs.forCustomer(req.store, req.params.token));
});

router.post('/:slug/orders/:token/substitutions/:subId', decideLimit, store, async (req, res) => {
  res.json(await subs.decideByCustomer(req.store, req.params.token, req.params.subId, req.body));
});

module.exports = router;
