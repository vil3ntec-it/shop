'use strict';
/**
 * S72 — تغییرِ روشِ تحویل تا پیش از جمع‌آوری: `POST /api/store/:slug/orders/:token/fulfillment`
 * (`lib/order-delivery-method.js`؛ قفل و دروازهٔ ORD-14، هزینه با `forCheckout` ِ ثبت).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.storefront.delivery_method` ⇒ ۴۰۴ (همان «نیست») و هیچ ردیفی.
 * ⛔ ویترینِ روشن با اشتراکِ زنده (`sf.open` ⇒ وگرنه ۴۰۴ `store_not_found`، SUB-10 دستهٔ STORE_PAID)، توکنِ
 *    پیگیریِ همین ویترین و شمارهٔ تأییدشدهٔ همان سفارش. کلیدِ یکتایی (`Idempotency-Key`) لازم است.
 * ⛔ سقف: سطلِ جدای IP (`store-method-ip`، همان اندازهٔ «ثبت») فقط روی همین نشانی — پیش از هر کار.
 * پیشوندِ تازه نیست (`/store`)؛ ACCOUNT_PREFIXES دست نمی‌خورد.
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const orders = require('../lib/orders');
const dm = require('../lib/order-delivery-method');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();
const PATH = '/:slug/orders/:token/fulfillment';

router.use(PATH, rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-method-ip' }));

router.post(PATH, async (req, res) => {
  res.set('Cache-Control', 'no-store');
  const store = await sf.open(req.params.slug);
  if (!(await dm.flagOf(store.shop_id))) throw notFound('پیدا نشد', 'not_found');
  const key = orders.clientKeyOf(req);
  const out = await dm.change(store, req.params.token, {
    key, body: req.body, ticket: req.get('x-store-ticket') || '', session: req.get('x-store-session') || '',
  });
  const order = await sf.track(store, req.params.token);
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  res.status(200).json({ changed: out.changed, replayed: out.replayed, order });
});

module.exports = router;
