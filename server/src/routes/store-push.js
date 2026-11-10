'use strict';
/**
 * «خبرم کن روی همین دستگاه» ِ صفحهٔ پیگیریِ سفارش — پوشِ وب برای مشتریِ ویترین (`lib/store-push.js`، CHK-20، NTF-01).
 *
 * ⛔ پیش‌فرض خاموش: بی ویترینِ روشن (`sf.open`) یا بی پرچم‌های `shop.storefront.web_push` و `shop.customer_notify` ِ همان
 * دکان ⇒ ۴۰۴ (همان «سفارش پیدا نشد») و هیچ ردیفی نوشته نمی‌شود.
 * ⛔ اجازه همان توکنِ امضاشدهٔ پیگیریِ سفارش (همان انقضا و باطل‌شدنِ لینک)؛ shop_id فقط از `slug` و سفارش فقط از توکن.
 * سقف: سطلِ IP ِ خودش (پیش از هر کار) و سطلِ هر سفارش در ساعت — جدا از سطلِ ثبتِ سفارش.
 *
 *   POST   /:slug/orders/:token/push   `{subscription: {endpoint, keys: {p256dh, auth}}, page?}` ⇒ ۲۰۰ `{push: {on: true}}`
 *   DELETE /:slug/orders/:token/push   `{endpoint}`                                            ⇒ ۲۰۰ `{push: {on: false}}`
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const storePush = require('../lib/store-push');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();

const ipLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-push' });
const orderLimit = rateLimit({
  max: config.rateLimit.storeFeedbackMax, windowMs: 60 * 60 * 1000, keyPrefix: 'store-push-order',
  //  نشانیِ بهنجار (همان trim و کوچک کردنِ `sf.open`)، وگرنه `/ABC/` ِ همان سفارش سطلِ تازه می‌گرفت
  key: (req) => `${String(req.params.slug || '').trim().toLowerCase()}:${req.params.token}`,
});

const store = async (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  req.store = await sf.open(req.params.slug);
  next();
};

router.post('/:slug/orders/:token/push', ipLimit, orderLimit, store, async (req, res) => {
  res.json(await storePush.attach(req.store, req.params.token, req.body));
});

router.delete('/:slug/orders/:token/push', ipLimit, orderLimit, store, async (req, res) => {
  res.json(await storePush.detach(req.store, req.params.token, req.body));
});

module.exports = router;
