'use strict';
/**
 * NTF-10 — سیاستِ «پوزش و کوپنِ خودکار برای تأخیر»: `/api/shop/orders/late-apology` (`lib/order-late-apology.js`).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.orders` و `shop.orders.late_apology` ⇒ `GET /` فقط `{enabled:false}` و `PUT /`
 *    ۴۰۳ (`orders_disabled` / `late_apology_disabled`). نوشتن کلیدِ اشتراکِ `online_orders` را هم می‌خواهد
 *    (SUB-10: دستهٔ PAID ِ `paid-routes-entitlement.test.js`) — پیش از پرچمِ خودِ این قابلیت.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن. دیدن: `orders.view`؛ تنظیم: صاحب و مدیر (`settings.write`).
 * ⛔ سقف: سطلِ IP (`late-apology-ip`) پیش از احراز، سپس همان سقفِ عمومی.
 * ⚠️ در app.js پیش از `/shop/orders` سوار می‌شود.
 */
const express = require('express');
const config = require('../config');
const orders = require('../lib/orders');
const apology = require('../lib/order-late-apology');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'late-apology-ip' }));
router.use(requireUser, requireShop, requirePermission('orders.view'));

const needFlag = async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  next();
};

/** نوشتن: اشتراک (همان پیام‌های `requireFeature`)، سپس پرچمِ همین قابلیت. */
const needWrite = async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (!entitled) {
    return next(ent.trial.used && !ent.trial.active
      ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
      : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
  }
  next();
};

const needApology = async (req, res, next) => {
  if (!(await apology.flagOf(req.shopId))) return next(forbidden('پوزشِ خودکارِ تأخیر برای این دکان روشن نیست', 'late_apology_disabled'));
  next();
};

router.get('/', async (req, res) => {
  const enabled = (await orders.flagOf(req.shopId)) && (await apology.flagOf(req.shopId));
  if (!enabled) return res.json({ enabled: false });
  res.json({
    enabled: true,
    policy: await apology.policyOf(req.shopId),
    recent: await apology.listOf(req.shopId),
    permissions: { settings: can(req.role, 'settings.write') },
  });
});

router.put('/', needFlag, requirePermission('settings.write'), needWrite, needApology, async (req, res) => {
  const policy = await apology.setPolicy(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order_late_apology.policy', targetType: 'shop', targetId: req.shopId,
    detail: { enabled: policy.enabled, lateMinutes: policy.lateMinutes, coupon: policy.coupon }, ip: clientIp(req),
  });
  res.json({ policy });
});

module.exports = router;
