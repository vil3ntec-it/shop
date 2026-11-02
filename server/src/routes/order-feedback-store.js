'use strict';
/**
 * گزارشِ مشکل (NTF-07) و نظرِ سفارش (NTF-09) از سوی مشتریِ ویترین — زیرِ `/api/store`.
 *
 * ⛔ پیش‌فرض خاموش: بی ویترینِ روشن (`sf.open`) یا بی پرچمِ `shop.orders.issue_reports` / `shop.orders.reviews`
 * ِ همان دکان ⇒ ۴۰۴ (همان «سفارش پیدا نشد») و هیچ ردیفی نوشته نمی‌شود.
 * ⛔ اجازه همان توکنِ امضاشدهٔ پیگیریِ سفارش است؛ shop_id فقط از `slug` و سفارش فقط از توکن، هرگز از بدنه.
 * سقف: نوشتن هم سطلِ ثبتِ ویترین (هر IP) و هم سطلِ هر سفارش (`RATE_STORE_FEEDBACK_MAX` در ساعت، پیش‌فرض ۲۰)؛
 * سقفِ شمار (۳ گزارش، ۳ عکس) و طولِ متن در `lib/order-issues.js` و `lib/order-reviews.js`.
 *
 *   POST /:slug/orders/:token/issues                    `{kind, text, requestKey}`      ⇒ ۲۰۱ `{issue}`
 *   POST /:slug/orders/:token/issues/:issueId/photos    `{imageBase64}` (یک عکس)        ⇒ ۲۰۱ `{photo}`
 *   POST /:slug/orders/:token/review                    `{stars, comment?}`             ⇒ ۲۰۱ `{review}`
 *   GET  /:slug/reviews                                 فقط نظرهای تأییدشده
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const issues = require('../lib/order-issues');
const reviews = require('../lib/order-reviews');
const audit = require('../lib/audit');
const { rateLimit, clientIp } = require('../middleware/ratelimit');

const router = express.Router();

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-feedback-read' });
const writeLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-feedback-write' });
const orderLimit = rateLimit({
  max: config.rateLimit.storeFeedbackMax, windowMs: 60 * 60 * 1000, keyPrefix: 'store-feedback-order',
  key: (req) => `${req.params.slug}:${req.params.token}`,
});

const store = async (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  req.store = await sf.open(req.params.slug);
  next();
};

const log = (req, action, detail) => audit.log({
  shopId: req.store.shop_id, actorType: 'customer', action, targetType: 'online_order',
  targetId: sf.orderIdOf(req.store.shop_id, req.params.token), detail, ip: clientIp(req),
});

router.post('/:slug/orders/:token/issues', writeLimit, orderLimit, store, async (req, res) => {
  const out = await issues.report(req.store, req.params.token, req.body);
  if (!out.replayed) await log(req, 'order.issue.report', { issueId: out.issue.id, kind: out.issue.kind });
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/:slug/orders/:token/issues/:issueId/photos', writeLimit, orderLimit, store, async (req, res) => {
  const out = await issues.addPhoto(req.store, req.params.token, req.params.issueId, req.body);
  if (!out.replayed) await log(req, 'order.issue.photo', { issueId: req.params.issueId, photoId: out.photo.id, size: out.photo.size });
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/:slug/orders/:token/review', writeLimit, orderLimit, store, async (req, res) => {
  const out = await reviews.submit(req.store, req.params.token, req.body);
  if (!out.replayed) await log(req, 'order.review.submit', { stars: out.review.stars });
  res.status(out.replayed ? 200 : 201).json(out);
});

router.get('/:slug/reviews', readLimit, store, async (req, res) => {
  res.json(await reviews.publicList(req.store));
});

module.exports = router;
