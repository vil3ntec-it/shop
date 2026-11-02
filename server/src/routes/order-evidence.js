'use strict';
/**
 * RSK-08 — پروندهٔ مدرکِ اختلافِ سفارش (`lib/order-evidence.js`) — زیرِ `/api/shop/orders/evidence`، فقط صاحب و کارمندِ
 * دارای `orders.manage`، پشتِ پرچمِ خاموشِ `shop.orders.dispute_file` (+ `shop.orders`). خاموش ⇒ ۴۰۳ `dispute_file_disabled`
 * و هیچ ردیفی؛ هیچ پاسخِ دیگری (جزئیاتِ سفارش، مأموریت، پیگیری) با این پرچم عوض نمی‌شود.
 *
 *   GET  /:id                    پرونده (JSON) — هر دیدن در سابقه (`order.evidence.view`)
 *   GET  /:id/print              همان پرونده، صفحهٔ چاپی (HTML، بی اسکریپت و بی تصویر)
 *   POST /:id/disputes           {kind, reason, requestKey}                  بازکردنِ اختلاف
 *   POST /:id/disputes/notes     {text, requestKey, disputeId?}              یادداشت
 *   POST /:id/disputes/resolve   {outcome, text, requestKey, disputeId?}     بستن با نتیجه
 *
 * نوشتن کلیدِ اشتراکِ `online_orders` را هم می‌خواهد (همان `needWrite` ِ `order-feedback.js`). پیک (توکنِ دامنهٔ courier)
 * به این‌جا نمی‌رسد (`courier_scope`)؛ فقط مأموریتِ خودش را از `/api/courier` می‌بیند.
 */
const express = require('express');
const config = require('../config');
const orders = require('../lib/orders');
const evidence = require('../lib/order-evidence');
const { printHtml } = require('../lib/order-evidence-print');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp, rateLimit } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
//  سقفِ نرخِ هر IP پیش از احراز (همان الگوی `order-feedback.js`)، و پس از آن سقفِ هر کاربر
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'order-evidence-ip' }));
const limit = rateLimit({ max: 60, windowMs: 60 * 1000, keyPrefix: 'order-evidence', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit, requirePermission('orders.manage'));

const needFlag = async (req, res, next) => {
  if (!(await evidence.enabled(req.shopId))) {
    return next(forbidden('پروندهٔ اختلافِ سفارش برای این دکان روشن نیست', 'dispute_file_disabled'));
  }
  next();
};
router.use(needFlag);

/** نوشتن: کلیدِ اشتراکِ `online_orders` (همان پیام‌های `requireFeature`). */
const needWrite = async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (!entitled) {
    return next(ent.trial.used && !ent.trial.active
      ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
      : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
  }
  next();
};

const viewer = (req) => ({ type: 'user', id: req.user.id, role: req.role });
const actor = (req) => ({ id: req.user.id, role: req.role, ip: clientIp(req) });
const viewed = (req, orderId, format) => audit.log({
  shopId: req.shopId, userId: req.user.id, action: 'order.evidence.view', targetType: 'online_order', targetId: orderId,
  detail: { format }, ip: clientIp(req),
});

router.get('/:id', async (req, res) => {
  const file = await evidence.fileOf(req.shopId, req.params.id, viewer(req));
  await viewed(req, file.order.id, 'json');
  res.set('Cache-Control', 'private, no-store');
  res.json(file);
});

router.get('/:id/print', async (req, res) => {
  const file = await evidence.fileOf(req.shopId, req.params.id, viewer(req));
  await viewed(req, file.order.id, 'html');
  res.set('Content-Type', 'text/html; charset=utf-8');
  res.set('Cache-Control', 'private, no-store');
  res.set('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
  res.send(printHtml(file));
});

router.post('/:id/disputes', needWrite, async (req, res) => {
  const out = await evidence.open(req.shopId, req.params.id, req.body || {}, actor(req));
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/:id/disputes/notes', needWrite, async (req, res) => {
  const out = await evidence.note(req.shopId, req.params.id, req.body || {}, actor(req));
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/:id/disputes/resolve', needWrite, async (req, res) => {
  const out = await evidence.resolve(req.shopId, req.params.id, req.body || {}, actor(req));
  res.status(out.replayed ? 200 : 201).json(out);
});

module.exports = router;
