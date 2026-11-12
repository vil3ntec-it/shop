'use strict';
/**
 * CHK-08 — عکسِ دروازه و یادداشتِ صوتیِ نشانیِ سفارش برای کارمند (`lib/order-address-media.js`) — زیرِ `/api/shop/orders`
 * (`router.use` در `routes/orders.js`، پس از `requireUser`، `requireShop` و `orders.view`):
 *
 *   GET /api/shop/orders/:id/address-media              `{items:[{id, kind, mime, size, createdAt}]}`
 *   GET /api/shop/orders/:id/address-media/:mediaId     بایت‌ها با سرآیندهای امن (`chat-media.send`)
 *
 * ⛔ فقط `orders.view` (انباردار نه)؛ دکان فقط از عضویت، سفارشِ دکانِ دیگر ۴۰۴. پرچمِ خاموشِ
 *    `shop.storefront.address_media` ⇒ ۴۰۴. سقف: سطلِ هر کاربر.
 * ⛔ پیک (توکنِ دامنهٔ courier) به این‌جا نمی‌رسد (`courier_scope`)؛ پیکِ همان سفارش از `/api/courier/missions/:id/address-media`.
 */
const express = require('express');
const media = require('../lib/order-address-media');
const { one } = require('../db');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();
const limit = rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'order-addr-media', key: (req) => req.user && req.user.id });

router.use('/:id/address-media', limit, async (req, res, next) => {
  res.set('Cache-Control', 'private, no-store');
  if (!(await media.flagOn(req.shopId))) throw notFound('پیدا نشد', 'not_found');
  const own = await one('SELECT 1 AS ok FROM online_orders WHERE shop_id=$1 AND id=$2', [req.shopId, String(req.params.id || '')]);
  if (!own) throw notFound('سفارش پیدا نشد', 'order_not_found');
  next();
});

router.get('/:id/address-media', async (req, res) => {
  res.json({ items: await media.listFor(req.shopId, req.params.id) });
});

router.get('/:id/address-media/:mediaId', async (req, res) => {
  media.send(res, await media.bytesOf(req.shopId, req.params.id, req.params.mediaId));
});

module.exports = router;
