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
 *
 * «خبرم کن وقتی موجود/ارزان شد» روی همین مرورگر (WEB-10، `lib/store-alert-push.js`، مهاجرتِ ۴۷۵): همان بدنه‌ها، این بار
 * برای (دکان، شمارهٔ تأییدشده، مرورگر). ⛔ پرچمِ «خبرم کن»، پوش یا اعلانِ خاموش ⇒ `next('route')` (همان پاسخِ مسیرِ
 * ناشناخته)؛ شماره فقط از بلیتِ شماره یا نشستِ حساب (`sf.verification`، اثباتش با ردیف می‌ماند). سقف: سطلِ IP ِ خودش
 * پیش از هر کار و سطلِ هر شماره (همان `WRITES_PER_HOUR` ِ «خبرم کن»).
 *
 *   POST   /:slug/alerts/push          `{subscription, page?}`                                  ⇒ ۲۰۰ `{push: {on: true}}`
 *   DELETE /:slug/alerts/push          `{endpoint}`                                            ⇒ ۲۰۰ `{push: {on: false}}`
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const storePush = require('../lib/store-push');
const alerts = require('../lib/storefront-alerts');
const alertPush = require('../lib/store-alert-push');
const { rateLimit } = require('../middleware/ratelimit');
const { ApiError } = require('../middleware/errors');

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

// ── «خبرم کن» ِ کالا روی همین مرورگر ──
const alertIp = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-alert-push' });
const alertPhone = rateLimit({
  max: alerts.WRITES_PER_HOUR, windowMs: 60 * 60 * 1000, keyPrefix: 'store-alert-push-phone', key: (req) => req.proof.phone,
});
//  ویترینِ نبوده یا بسته ⇒ ۴۰۴ `store_not_found` ِ `sf.open` (SUB-10)؛ هر پرچمی خاموش ⇒ مسیرِ بعدی، همان امروز
const alertGate = async (req, res, next) => {
  const s = await sf.open(req.params.slug);
  if (!(await alertPush.flagOn(s.shop_id))) return next('route');
  req.store = s;
  res.set('Cache-Control', 'no-store');
  next();
};
const verified = async (req, res, next) => {
  const v = await sf.verification(req.get('x-store-ticket') || '', req.get('x-store-session') || '');
  if (!v) throw new ApiError(401, 'verification_required', 'برای «خبرم کن»، شمارهٔ خود را با کد تأیید کنید');
  req.proof = v;
  next();
};

router.post('/:slug/alerts/push', alertIp, alertGate, verified, alertPhone, async (req, res) => {
  res.json(await alertPush.attach(req.store, req.proof, req.body));
});

router.delete('/:slug/alerts/push', alertIp, alertGate, verified, alertPhone, async (req, res) => {
  res.json(await alertPush.detach(req.store, req.proof.phone, req.body));
});

module.exports = router;
