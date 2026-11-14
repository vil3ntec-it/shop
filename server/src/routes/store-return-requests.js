'use strict';
/**
 * RET-11 — «درخواستِ برگشت» ِ مشتری از ویترین (`lib/order-return-requests.js`، شرح و قاعده‌ها همان‌جا).
 *
 *   POST /:slug/orders/:token/return-requests               `{lines, reason, text?, method, requestKey}` ⇒ ۲۰۱ `{request}`
 *   POST /:slug/orders/:token/return-requests/:id/photos    `{imageBase64}` (یک عکس)                     ⇒ ۲۰۱ `{photo}`
 *   GET  /:slug/account/return-requests                     درخواست‌های همین حساب در همین دکان («سفارش‌های من»)
 *
 * ⛔ پرچم پیش از بدنه: ویترینِ نبوده/بسته ⇒ همان ۴۰۴ `store_not_found` (SUB-10)؛ پرچمِ خاموشِ
 *    `shop.storefront.return_requests` ⇒ `next('route')` (همان درِ ناشناخته) پیش از نشست و بدنه.
 * ⛔ فقط با نشستِ حساب (`X-Store-Session`)؛ نبود ⇒ ۴۰۱ `session_required`. سفارشی که مالِ این حساب نیست ⇒ ۴۰۴.
 * ⛔ سقف: سطلِ IP پیش از هر کار، سطلِ نوشتنِ ویترین، و سطلِ هر حساب (`RATE_STORE_RETURNS_MAX` در ساعت، پیش‌فرض ۲۰).
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const R = require('../lib/order-return-requests');
const customers = require('../lib/store-customers');
const audit = require('../lib/audit');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { ApiError } = require('../middleware/errors');

const router = express.Router();

//  مسیرها با رشتهٔ صریح تا فهرستِ درها (`test/route-inventory.js`) آن‌ها را ببیند
router.use('/:slug/orders/:token/return-requests', rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-returns-ip' }));
router.use('/:slug/account/return-requests', rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-returns-ip' }));
const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-returns-read' });
const writeLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-returns-write' });
const accountLimit = rateLimit({
  max: Number(process.env.RATE_STORE_RETURNS_MAX) > 0 ? Number(process.env.RATE_STORE_RETURNS_MAX) : 20,
  windowMs: 60 * 60 * 1000, keyPrefix: 'store-returns-account', key: (req) => req.customer && req.customer.id,
});

const gate = async (req, res, next) => {
  const s = await sf.open(req.params.slug);
  if (!(await R.flagOn(s.shop_id))) return next('route');
  req.store = s;
  res.set('Cache-Control', 'no-store');
  next();
};

const customer = async (req, res, next) => {
  const a = await customers.auth(req.get('x-store-session') || '');
  if (!a) throw new ApiError(401, 'session_required', 'برای درخواستِ برگشت وارد حسابِ خود شوید');
  req.customer = a.customer;
  next();
};

const log = (req, action, targetId, detail) => audit.log({
  shopId: req.store.shop_id, actorType: 'customer', action, targetType: 'order_return_request',
  targetId, detail, ip: clientIp(req),
});

router.post('/:slug/orders/:token/return-requests', gate, writeLimit, customer, accountLimit, async (req, res) => {
  const out = await R.create(req.store, req.customer, req.params.token, req.body);
  if (!out.replayed) await log(req, 'order.return_request.create', out.request.id, { reason: out.request.reason, lines: out.request.lines.length });
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/:slug/orders/:token/return-requests/:id/photos', gate, writeLimit, customer, accountLimit, async (req, res) => {
  const out = await R.addPhoto(req.store, req.customer, req.params.token, req.params.id, req.body);
  if (!out.replayed) await log(req, 'order.return_request.photo', req.params.id, { photoId: out.photo.id, size: out.photo.size });
  res.status(out.replayed ? 200 : 201).json(out);
});

router.get('/:slug/account/return-requests', gate, readLimit, customer, async (req, res) => {
  res.json(await R.forCustomer(req.store, req.customer));
});

module.exports = router;
