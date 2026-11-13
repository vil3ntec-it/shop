'use strict';
/**
 * RET-11 — صندوقِ «درخواست‌های برگشت» ِ دکان، زیرِ `/api/shop/order-returns` (`lib/order-return-requests-shop.js`).
 *
 *   GET  /?status=pending|accepted|completed|rejected|all   فهرست (بی بایت) و `waiting`
 *   GET  /:id/photos/:photoId                                بایت‌های عکس — خصوصی، هر دیدن در سابقه
 *   POST /:id/accept                                         پذیرفتن (برنامه سپس همان برگهٔ برگشتِ موجود را باز می‌کند)
 *   POST /:id/reject     {note}                              رد با دلیل (مشتری می‌بیند)
 *   POST /:id/complete   {returnDocId}                       «برگشت ثبت شد» با شناسهٔ سندِ `ReturnFlow.post`
 *
 * ⛔ خواندن `orders.view`؛ نوشتن `orders.manage` + مجوزِ برگشتِ موجود (`returns.create` ِ نقش و لایه‌ها نه «ممنوع») +
 *    کلیدِ اشتراکِ `online_orders` (اشتراکِ تمام‌شده فقط نوشتن را می‌بندد). پرچمِ خاموش ⇒ ۴۰۳ `return_requests_disabled`.
 * ⛔ shop_id فقط از عضویتِ توکن. سقف: سطلِ IP پیش از احراز، سپس سطلِ هر کاربر.
 */
const express = require('express');
const config = require('../config');
const orders = require('../lib/orders');
const S = require('../lib/order-return-requests-shop');
const chatMedia = require('../lib/chat-media');
const perms = require('../lib/staff-perms');
const core = require('../lib/perm-core');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp, rateLimit } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'shop-returns-ip' }));
const limit = rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'shop-returns', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit, requirePermission('orders.view'));

const manage = requirePermission('orders.manage');
const actorOf = (req) => ({ type: 'user', id: req.user.id, role: req.role });

/** مجوزِ برگشتِ موجود (STF-03): نقشی که برگشت برایش «ممنوع» است (یا صاحب برایش بسته) درخواست را نمی‌پذیرد و رد نمی‌کند */
const canReturn = async (req, res, next) => {
  const role = req.member && req.member.role;
  if (core.evaluate(role, await perms.layersOfReq(req), 'returns.create', null).decision === 'deny') {
    return next(forbidden('برگشت در حدِ دسترسیِ شما نیست', 'permission_denied'));
  }
  next();
};

/** نوشتن: کلیدِ اشتراکِ `online_orders` (همان پیام‌های `requireFeature`) */
const needWrite = async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (!entitled) {
    return next(ent.trial.used && !ent.trial.active
      ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
      : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
  }
  next();
};

const log = (req, action, targetId, detail) => audit.log({
  shopId: req.shopId, userId: req.user.id, action, targetType: 'order_return_request', targetId, detail, ip: clientIp(req),
});

router.get('/', async (req, res) => {
  res.json(await S.forShop(req.shopId, req.query || {}));
});

router.get('/:id/photos/:photoId', async (req, res) => {
  const p = await S.photoOf(req.shopId, req.params.id, req.params.photoId);
  await log(req, 'order.return_request.photo.view', req.params.id, { photoId: p.id });
  chatMedia.send(res, p);
});

const write = (action, fn) => async (req, res) => {
  const out = await fn(req);
  if (!out.unchanged) await log(req, action, out.request.id, { orderId: out.request.orderId, from: out.from, to: out.request.status });
  delete out.from;
  res.json(out);
};

router.post('/:id/accept', manage, canReturn, needWrite, write('order.return_request.accept',
  (req) => S.accept(req.shopId, req.params.id, actorOf(req))));
router.post('/:id/reject', manage, canReturn, needWrite, write('order.return_request.reject',
  (req) => S.reject(req.shopId, req.params.id, req.body || {}, actorOf(req))));
router.post('/:id/complete', manage, canReturn, needWrite, write('order.return_request.complete',
  (req) => S.complete(req.shopId, req.params.id, req.body || {}, actorOf(req))));

module.exports = router;
