'use strict';
/**
 * میزِ سفارشِ دکان — `/api/shop/orders/desk` (ORD-04، ORD-08، ORD-09؛ `lib/order-desk.js`).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.orders` و `shop.orders.desk` ⇒ `GET /` فقط `{enabled:false}` و هر نوشتن
 * ۴۰۳ (`orders_disabled` / `order_desk_disabled`). نوشتن کلیدِ اشتراکِ `online_orders` را هم می‌خواهد
 * (SUB-10: دستهٔ PAID ِ `paid-routes-entitlement.test.js`).
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن. «شلوغم»، توقف و ادامه کارِ هر کسی است که سفارش را جلو می‌برد
 * (`orders.manage`، مثلِ Ctrl+B و Ctrl+Shift+P ِ §۵)؛ تنظیمِ قبول و توقفِ خودکار کارِ صاحب و مدیر (`settings.write`).
 * ⚠️ در app.js پیش از `/shop/orders` سوار می‌شود.
 */
const express = require('express');
const orders = require('../lib/orders');
const desk = require('../lib/order-desk');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('orders.view'));
const actorOf = (req) => ({ type: 'user', id: req.user.id, role: req.role });

const needFlag = async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  next();
};

/** نوشتن: اشتراک (همان پیام‌های `requireFeature`)، سپس پرچمِ میز. */
const needWrite = async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (!entitled) {
    return next(ent.trial.used && !ent.trial.active
      ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
      : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
  }
  next();
};

const needDesk = async (req, res, next) => {
  if (!(await desk.flagOf(req.shopId))) return next(forbidden('میزِ سفارش برای این دکان روشن نیست', 'order_desk_disabled'));
  next();
};

router.get('/', async (req, res) => {
  const enabled = (await orders.flagOf(req.shopId)) && (await desk.flagOf(req.shopId));
  if (!enabled) return res.json({ enabled: false });
  res.json({
    enabled: true,
    ...(await desk.stateOf(req.shopId)),
    pauseReasons: desk.PAUSE_REASONS,
    permissions: { manage: can(req.role, 'orders.manage'), settings: can(req.role, 'settings.write') },
  });
});

/** پاسخِ یکسانِ هر نوشتن + سابقهٔ عملیات (تکرار ⇒ بی سطرِ دوم). */
function writer(action, fn, detailOf) {
  return async (req, res) => {
    const out = await fn(req.shopId, { body: req.body, actor: actorOf(req) });
    if (out.replayed) {
      res.set('Idempotent-Replayed', 'true');
    } else {
      await audit.log({
        shopId: req.shopId, userId: req.user.id, action, targetType: 'shop', targetId: req.shopId,
        detail: detailOf(out.state, req.body || {}), ip: clientIp(req),
      });
    }
    res.json({ state: out.state, replayed: out.replayed });
  };
}

router.post('/busy', needFlag, requirePermission('orders.manage'), needWrite, needDesk,
  writer('order_desk.busy', desk.busy, (s, b) => ({ minutes: b.minutes, until: s.busy ? s.busy.until : null, extraMinutes: s.busy ? s.busy.extraMinutes : 0 })));

router.post('/pause', needFlag, requirePermission('orders.manage'), needWrite, needDesk,
  writer('order_desk.pause', desk.pause, (s, b) => ({ minutes: b.minutes, reason: b.reason, until: s.pause ? s.pause.until : null })));

router.post('/resume', needFlag, requirePermission('orders.manage'), needWrite, needDesk,
  writer('order_desk.resume', desk.resume, () => ({})));

router.put('/settings', needFlag, requirePermission('settings.write'), needWrite, needDesk,
  writer('order_desk.settings', desk.saveSettings, (s) => ({ autoPause: s.autoPause, autoAccept: s.autoAccept, version: s.version })));

module.exports = router;
