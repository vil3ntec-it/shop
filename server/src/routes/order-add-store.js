'use strict';
/**
 * CHK-21 — افزودنِ قلم به همان سفارش تا پیش از شروعِ جمع‌آوری: `POST /api/store/:slug/orders/:token/items`
 * (`lib/order-add-items.js`؛ قفل، دروازه و رویدادِ ORD-14).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.orders.add_items` ⇒ ۴۰۴ (همان «نیست») و هیچ ردیفی.
 * ⛔ ویترینِ روشن با اشتراکِ زنده (`sf.open` ⇒ وگرنه ۴۰۴ `store_not_found`، SUB-10 دستهٔ STORE_PAID)، توکنِ
 *    پیگیریِ همین ویترین و شمارهٔ تأییدشدهٔ همان سفارش. کلیدِ یکتایی (`Idempotency-Key`) لازم است.
 * ⛔ سقف: سطلِ جدای IP (`store-add-items-ip`، همان اندازهٔ «ثبت») فقط روی همین نشانی — پیش از هر کار.
 * پیشوندِ تازه نیست (`/store`)؛ ACCOUNT_PREFIXES دست نمی‌خورد.
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const orders = require('../lib/orders');
const add = require('../lib/order-add-items');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();
const PATH = '/:slug/orders/:token/items';

router.use(PATH, rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-add-items-ip' }));

router.post(PATH, async (req, res) => {
  res.set('Cache-Control', 'no-store');
  const store = await sf.open(req.params.slug);
  if (!(await add.flagOf(store.shop_id))) throw notFound('پیدا نشد', 'not_found');
  const key = orders.clientKeyOf(req);
  const out = await add.add(store, req.params.token, {
    key, body: req.body, ticket: req.get('x-store-ticket') || '', session: req.get('x-store-session') || '',
  });
  //  همان نمای پیگیری (`lib/store-order-view.js`): صفحه با همین پاسخ دوباره می‌کشد
  const order = await require('../lib/store-order-view').customerOrder(store, req.params.token);
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  res.status(out.replayed ? 200 : 201).json({ added: out.added, replayed: out.replayed, order });
});

module.exports = router;
