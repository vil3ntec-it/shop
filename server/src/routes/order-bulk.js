'use strict';
/**
 * کارِ گروهی روی سفارش‌ها — `/api/shop/orders/bulk` (ORD-19؛ `lib/order-bulk.js`).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.orders` ⇒ ۴۰۳ `orders_disabled`، بی `shop.orders.bulk` ⇒ ۴۰۳
 * `order_bulk_disabled`. نوشتن کلیدِ اشتراکِ `online_orders` را هم می‌خواهد (SUB-10: دستهٔ PAID).
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن؛ سفارشِ دکانِ دیگر همان «پیدا نشد» است. اجازه همان گذارِ تکی
 * (`orders.manage`). هر سفارشِ عوض‌شده یک سطرِ سابقهٔ عملیات (`order.transition`، `bulk: true`) دارد.
 * ⚠️ در app.js پیش از `/shop/orders` سوار می‌شود.
 */
const express = require('express');
const orders = require('../lib/orders');
const bulk = require('../lib/order-bulk');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('orders.view'));

const needFlag = async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  next();
};

const needWrite = async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (!entitled) {
    return next(ent.trial.used && !ent.trial.active
      ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
      : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
  }
  next();
};

const needBulk = async (req, res, next) => {
  if (!(await bulk.flagOf(req.shopId))) return next(forbidden('کارِ گروهی روی سفارش برای این دکان روشن نیست', 'order_bulk_disabled'));
  next();
};

router.post('/transition', needFlag, requirePermission('orders.manage'), needWrite, needBulk, async (req, res) => {
  const out = await bulk.transition(req.shopId, req.body, { type: 'user', id: req.user.id, role: req.role });
  for (const r of out.results) {
    if (!r.ok || r.unchanged) continue;
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'order.transition',
      targetType: 'online_order', targetId: r.id,
      detail: { orderNo: r.orderNo, from: r.from, to: r.status, reason: r.reason, bulk: true },
      ip: clientIp(req),
    });
  }
  res.json(out);
});

module.exports = router;
