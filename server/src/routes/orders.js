'use strict';
/**
 * بخشِ «سفارش‌ها»ی برنامهٔ دکان — `/api/shop/orders` (فاز ۴، بخشِ ۳.۹).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.orders` در «تنظیماتِ زنده» فقط
 * `GET /config` جواب می‌دهد (تا برنامه بداند بخش را نشان ندهد).
 * ⛔ خواندن با اشتراکِ تمام‌شده هم باز است (داده گروگان نیست)؛ نوشتن
 * کلیدِ اشتراکِ `online_orders` را هم می‌خواهد.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید، هرگز از درخواست.
 *
 * زیرِ پیشوندِ موجودِ `/shop` است، پس `ACCOUNT_PREFIXES`ِ پنلِ خانگی
 * چیزِ تازه‌ای نمی‌خواهد.
 */
const express = require('express');
const orders = require('../lib/orders');
const S = require('../lib/order-states');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('orders.view'));

const actorOf = (req) => ({ type: 'user', id: req.user.id, role: req.role });
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

/** پرچم لازم است (خواندن و نوشتن). */
const needFlag = wrap(async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) {
    return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  }
  next();
});

/** نوشتن: پرچم + کلیدِ اشتراک (همان پیام‌های `requireFeature`). */
const needWrite = wrap(async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (!entitled) {
    const err = ent.trial.used && !ent.trial.active
      ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
      : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required');
    return next(err);
  }
  next();
});

router.get('/config', wrap(async (req, res) => {
  const st = await orders.stateOf(req.shopId);
  res.json({
    ...st,
    tabs: S.TABS,
    reasons: S.REASONS,
    sources: orders.STAFF_SOURCES,
    permissions: Object.fromEntries(
      ['create', 'manage', 'contact', 'refund'].map((k) => [k, can(req.role, `orders.${k}`)])
    ),
  });
}));

router.get('/summary', needFlag, wrap(async (req, res) => {
  res.json(await orders.summary(req.shopId));
}));

router.get('/', needFlag, wrap(async (req, res) => {
  res.json(await orders.list(req.shopId, {
    tab: String(req.query.tab || 'new'), limit: req.query.limit, cursor: req.query.cursor,
  }, actorOf(req)));
}));

router.get('/:id', needFlag, wrap(async (req, res) => {
  res.json({ order: await orders.get(req.shopId, req.params.id, actorOf(req)) });
}));

/**
 * سفارشِ دستیِ کارمند — تلفنی، تلگرام، واتس‌اپ یا دستی (ORD-21)؛ با
 * `test: true` «سفارشِ آزمایشی» است تا صف، هشدار و چاپ بی مشتریِ واقعی
 * آزموده شوند. کلیدِ یکتایی لازم است (CHK-19).
 */
router.post('/', needFlag, requirePermission('orders.create'), needWrite, wrap(async (req, res) => {
  const key = orders.clientKeyOf(req);
  const out = await orders.create(req.shopId, { key, body: req.body, actor: actorOf(req) });
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json({ order: out.order, replayed: true });
  }
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order.create',
    targetType: 'online_order', targetId: out.order.id,
    detail: { orderNo: out.order.orderNo, source: out.order.source, isTest: out.order.isTest, totalMinor: out.order.totalMinor },
    ip: clientIp(req),
  });
  res.status(201).json({ order: out.order });
}));

router.post('/:id/transition', needFlag, requirePermission('orders.manage'), needWrite, wrap(async (req, res) => {
  let out;
  try {
    out = await orders.transition(req.shopId, req.params.id, req.body || {}, actorOf(req));
  } catch (err) {
    if (err.code === 'status_changed') {
      return res.status(409).json({ error: { code: err.code, message: err.message, current: err.current } });
    }
    throw err;
  }
  if (!out.unchanged) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'order.transition',
      targetType: 'online_order', targetId: out.order.id,
      detail: { orderNo: out.order.orderNo, from: out.from, to: out.to, reason: out.order.statusReason },
      ip: clientIp(req),
    });
  }
  res.json({ order: out.order, unchanged: out.unchanged });
}));

module.exports = router;
