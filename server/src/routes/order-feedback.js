'use strict';
/**
 * گزارش‌های مشکل (NTF-07) و نظرهای مشتری (NTF-09) برای کارمندِ دکان — زیرِ `/api/shop/order-feedback`.
 * خواندن با `orders.view`، رسیدگی و بازبینی با `orders.manage` و کلیدِ اشتراکِ `online_orders` (اشتراکِ تمام‌شده
 * فقط نوشتن را می‌بندد). هر پرچمِ خاموش ⇒ ۴۰۳ ِ همان قابلیت (`issue_reports_disabled` / `reviews_disabled`).
 *
 *   GET  /issues?status=open|resolved|all          فهرست (بی بایت)
 *   GET  /issues/:id/photos/:photoId               بایت‌های عکس — خصوصی، هر دیدن در سابقه
 *   POST /issues/:id/resolve   {reply}             «رسیدگی شد» با پاسخ (مشتری در صفحهٔ پیگیری می‌بیند)
 *   GET  /reviews?status=pending|approved|rejected|all
 *   POST /reviews/:id/moderate {decision: approve|reject}
 */
const express = require('express');
const orders = require('../lib/orders');
const issues = require('../lib/order-issues');
const reviews = require('../lib/order-reviews');
const chatMedia = require('../lib/chat-media');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('orders.view'));

const manage = requirePermission('orders.manage');
const actorOf = (req) => ({ type: 'user', id: req.user.id, role: req.role });

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

const log = (req, action, targetType, targetId, detail) => audit.log({
  shopId: req.shopId, userId: req.user.id, action, targetType, targetId, detail, ip: clientIp(req),
});

router.get('/issues', async (req, res) => {
  res.json(await issues.forShop(req.shopId, req.query || {}));
});

router.get('/issues/:id/photos/:photoId', async (req, res) => {
  const p = await issues.photoOf(req.shopId, req.params.id, req.params.photoId);
  await log(req, 'order.issue.photo.view', 'order_issue', req.params.id, { photoId: p.id });
  chatMedia.send(res, p);
});

router.post('/issues/:id/resolve', manage, needWrite, async (req, res) => {
  const out = await issues.resolve(req.shopId, req.params.id, req.body || {}, actorOf(req));
  if (!out.unchanged) await log(req, 'order.issue.resolve', 'online_order', out.issue.orderId, { issueId: out.issue.id });
  res.json(out);
});

router.get('/reviews', async (req, res) => {
  res.json(await reviews.forShop(req.shopId, req.query || {}));
});

router.post('/reviews/:id/moderate', manage, needWrite, async (req, res) => {
  const out = await reviews.moderate(req.shopId, req.params.id, req.body || {}, actorOf(req));
  if (!out.unchanged) {
    await log(req, 'order.review.moderate', 'online_order', out.review.orderId, { reviewId: out.review.id, from: out.from, to: out.review.status });
  }
  delete out.from;
  res.json(out);
});

module.exports = router;
