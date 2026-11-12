'use strict';
/**
 * CHK-08 — درِ مشتریِ عکسِ دروازه و یادداشتِ صوتیِ نشانیِ **یک سفارش** (`lib/order-address-media.js`، مهاجرتِ ۸۴۷؛ طراحیِ مشترکِ
 * `lib/address-media.js` با درِ نشانیِ حساب):
 *
 *   GET    /:slug/orders/:token/address-media          `{gatePhoto, voiceNote, saved}` (بی بایت)
 *   PUT    /:slug/orders/:token/address-media/:kind    بدنهٔ خام (عکس یا صدا)؛ `kind` = gate-photo | voice-note ⇒ ۲۰۱ تازه، ۲۰۰ جایگزین
 *   DELETE /:slug/orders/:token/address-media/:kind    ⇒ `{deleted}`
 *
 * ⛔ پیش‌فرض خاموش: بی ویترینِ روشن (`sf.open`) یا بی پرچمِ `shop.storefront.address_media` ⇒ ۴۰۴ و هیچ ردیفی.
 * ⛔ اجازه فقط توکنِ امضاشدهٔ پیگیریِ همان سفارش (انقضا و باطل‌شدنِ لینک همان)؛ دکان فقط از `slug`، سفارش فقط از توکن.
 * ⛔ سقف: سطلِ IP پیش از خواندنِ بدنه و سطلِ هر سفارش (توکن)؛ نوع و توکن پیش از خواندنِ بدنه؛ بدنهٔ بزرگ‌تر از سقف پیش از
 *    خواندن ۴۱۳ (`core.rawBody`).
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const media = require('../lib/order-address-media');
const core = require('../lib/address-media');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-addr-media-read' });
const ipLimit = rateLimit({ max: config.addressMedia.uploadIpMax, keyPrefix: 'store-addr-media-ip' });
const orderLimit = rateLimit({
  max: config.addressMedia.uploadMax, keyPrefix: 'store-addr-media', key: (req) => `t:${String(req.params.token || '').slice(0, 200)}`,
});

const store = async (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  req.store = await sf.open(req.params.slug);
  if (!(await media.flagOn(req.store.shop_id))) throw notFound('پیدا نشد', 'not_found');
  next();
};

router.get('/:slug/orders/:token/address-media', readLimit, store, async (req, res) => {
  const orderId = await sf.orderIdOfLink(req.store, req.params.token);
  res.json(await media.view(req.store.shop_id, orderId));
});

//  ⛔ نوع و توکنِ پیگیری **پیش از** خواندنِ بدنه (تا ۱ مگابایت) و تجزیهٔ عکس: درخواستِ بی توکنِ درست هیچ بایتی را به کار نمی‌اندازد
const linked = async (req, res, next) => {
  core.kindOf(req.params.kind);
  await sf.orderIdOfLink(req.store, req.params.token);
  next();
};

router.put('/:slug/orders/:token/address-media/:kind', ipLimit, orderLimit, store, linked, core.rawBody, async (req, res) => {
  const out = await media.put(req.store, req.params.token, req.params.kind, req.body);
  res.status(out.replaced || out.unchanged ? 200 : 201).json(out);
});

router.delete('/:slug/orders/:token/address-media/:kind', ipLimit, orderLimit, store, async (req, res) => {
  res.json(await media.remove(req.store, req.params.token, req.params.kind));
});

module.exports = router;
